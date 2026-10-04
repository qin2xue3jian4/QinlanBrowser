# 构建与开发

## 环境

- JDK 21
- Android SDK Platform 36、Build Tools 36.0.0
- Python 3.10+，用于发布工具检查

项目使用 Gradle Wrapper 和 Android Gradle Plugin 内置 Kotlin，无需单独安装 Kotlin 编译器。

通过 Android Studio 打开项目，或设置 `ANDROID_HOME` 指向 SDK 目录。也可在不提交的 `local.properties` 中配置 `sdk.dir`。

## 构建

Linux / macOS：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows PowerShell：

```powershell
.\build.ps1 -Tasks ':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'
```

Debug APK 输出到 `app/build/outputs/apk/debug/`。Release 使用 R8 和资源压缩；未配置签名时生成未签名 APK，正式发布配置见[发布指南](releasing.md)。

## 检查

```sh
python -m unittest discover -s tools/tests -v
./gradlew :app:testDebugUnitTest :app:lintDebug
```

JVM 测试覆盖 Cookie 校验、备份加密、收藏整理、搜索引擎、二维码、脚本匹配、广告规则语法、订阅配置、媒体分类及标签页资源隔离。发布工具测试覆盖版本标签校验和校验文件生成。

0.6 新增设置搜索/备份类型、菜单损坏恢复和限时标签撤销测试。`ImprovementChecks` 在真实 WebView 检查正文首尾保留、排除导航与表单、原 DOM 不变、网站字号继承和父标签/撤销行为：

```sh
adb shell am instrument -w -e improvements true dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

部分系统会阻止测试进程从后台启动 Activity；此时可在另一终端执行 `adb shell am start -n dev.qinglan.browser/.BrowserActivity`，无需关闭系统安全检查。测试使用合成文章和临时主页标签。

`python tools/improvement_fixture.py` 加 `adb reverse tcp:8877 tcp:8877` 提供长文章、后台链接、小文件与慢速下载页面。测试后仅清理对应的测试标签和下载，并移除该端口转发。阅读提取库固定为 Mozilla Readability 0.6.0，源文件 SHA-256 和许可证位于 `assets/reader/`；展示使用原生文字，不把提取出的 HTML 作为可信页面执行。

需要设备的 WebView 检查可通过 instrumentation 运行：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e revision4 scripts dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e filtering true dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e resources true dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

建议使用模拟器或专用测试设备。Debug 包与正式包可能签名不同，不能直接互相覆盖。

CI 在 `main` 分支提交和 Pull Request 时运行，检查 Debug / Release 构建、单元测试与 lint，并保存 Debug APK 和报告。CI 的 Debug APK 仅用于测试，签名不保证跨运行一致。

## 目录

| 路径 | 内容 |
| --- | --- |
| `app/src/main/` | 应用源码与资源 |
| `app/src/test/` | JVM 测试 |
| `app/src/androidTest/` | 设备端测试 |
| `tools/` | 测试与发布辅助工具 |
| `.github/workflows/` | 持续集成和发布流程 |
| `version.properties` | 版本名称和版本号 |

密钥、设备数据、构建产物和本地配置不得提交。二维码识别使用 ZXing，其 Apache 2.0 许可证随应用分发；WebView 支持库使用 AndroidX WebKit。

资源下载验证可启动 `python tools/fixture_server.py`，通过 `adb reverse tcp:8765 tcp:8765` 后访问 `http://127.0.0.1:8765/resources`。页面只使用合成 Cookie、静音 WAV 与示例播放列表，可验证资源分类及带 Cookie、Referer 的下载。

完成合成页面的手动验证后，可运行 `adb shell am instrument -w -e cleanupResources true dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation` 清理该测试页、生成的屏蔽规则和测试下载。

## 0.7 成熟度回归

新增 JVM 检查覆盖地址解析（端口、本地域名、IPv6、IDN、危险协议和凭据不泄漏到搜索）、本地建议优先级/去重、严格 HTTPS origin 比较及设置备份拒绝损坏值。

```sh
adb shell am instrument -w -e maturity core dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

该检查使用独立的 `qa-maturity-reading` 目录和临时标签，验证离线正文存取/替换/删除/长度限制、未知权限与跨源请求拒绝、前台同源请求确认后取消。不会启动真实相机、麦克风或位置采集。

下载异常检查需要先运行本机 8877 fixture 和 ADB reverse，再执行 `-e maturity download`。它向 `/fail` 提交合成下载，最长等待 90 秒，允许厂商用 ERROR_UNKNOWN 代替 HTTP 状态码，并保留一个合成失败记录用于界面重试检查。检查后通过下载页删除该记录；不要把厂商超时/未知错误当作浏览器识别出的 HTTP 404。

WebView 权限实现参考 Android 官方 `PermissionRequest`、`GeolocationPermissions.Callback`；仅明确允许视频/音频资源子集，系统权限完成后再次校验当前标签与 HTTPS origin。离线文章索引采用 AtomicFile，正文文件先写入，再原子替换索引，最后删除旧快照；读写在单线程工作队列完成。

## 无痕回归

安装 Debug 与 AndroidTest APK 后运行 `adb shell am instrument -w -e incognito core dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation`。测试在保留域名 `qinglan-private.example.test` 加载本地合成页面，检查 Cookie、localStorage、IndexedDB、CacheStorage 的隔离/清理，普通标签恢复、后台标签配置继承、历史/会话不落盘和截图保护；不需要 fixture 服务器，不读取真实网站 Cookie。

异常恢复分两步：运行 `-e incognito seed`，收到 READY 后在 30 秒内 `adb shell am force-stop dev.qinglan.browser`，再运行 `-e incognito recover`。种子步骤故意不走正常生命周期清理，恢复步骤验证启动清除旧配置且未恢复无痕标签。

`PrivateSession` 要求 AndroidX WebKit `MULTI_PROFILE` 与 `DELETE_BROWSING_DATA`。新 WebView 的第一次调用必须是 `WebViewCompat.setProfile`；销毁会话所有 WebView 后调用 `WebStorageCompat.deleteBrowsingData(profile.webStorage)`。已加载的 profile 在当前进程可能无法删除，因此会话名称从不复用，下次启动删除旧配置。默认 profile 不参加无痕清理。没有新增依赖或服务端。

## 多账号回归

`-e accounts core` 验证账号/默认备注命名、重名拒绝、Cookie/localStorage/关联登录域隔离、后台标签继承、切换范围、撤销关闭、无痕互不干扰，以及删除账号保留其他登录。

重启验证依次执行 `-e accounts seed`、`adb shell am force-stop dev.qinglan.browser`、`-e accounts recover`，runner 同上述无痕测试。种子步骤用保留测试域写入持久 Cookie 和 localStorage，退后台并等待 WebView 批量写盘，再结束进程；恢复步骤检查名称、账号绑定与两个 origin 的数据，并清理合成账号。不能把立即杀死进程前的 JavaScript 回调当作数据库已刷盘证明。

手工操作可运行 `python tools/account_fixture.py`，设置 `adb reverse tcp:8881 tcp:8881`，访问 `http://127.0.0.1:8881/`。只提供合成身份、Cookie/localStorage 和小文本下载。测试后删除新建测试空间、关闭测试标签，移除该端口转发并停止 fixture；下载测试文件需从下载页删除。

账号资料与引擎分离：`AccountCodec` 只序列化备注/ID/无查询参数的入口，`AccountProfiles` 用 AtomicFile 保存列表，`BrowserTab.accountId` 跟随标签生命周期。删账号需先销毁所有引用视图，移除索引后清理数据；启动删除未登记的账号 profile。列表损坏时禁止孤立配置清理。Cookie 面板捕获具体 CookieManager，下载记录保存来源账号 ID，防止后台页面与下载重试使用当前前台标签的登录。
