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

设备辅助工具通过 PATH 查找 `adb`，也可使用 `ADB` 环境变量指定可执行文件；不需要修改源码填写本机安装路径。调试凭据、设备数据和构建产物保存在被忽略的本地目录，不应打包或提交。

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

## 浏览控件回归

`python tools/controls_fixture.py --cert .local/controls/cert.pem --key .local/controls/key.pem` 提供合成 HTTP 8892 / 自签名 HTTPS 8893 页面；测试证书只保存在忽略目录中。设置 `adb reverse tcp:8892 tcp:8892` 和 `adb reverse tcp:8893 tcp:8893` 后，可运行：

```sh
adb shell am instrument -w -e controls core dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e controls tls dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

core 检查导航状态、网址全选、边缘按钮、整页截图首尾、PNG/JPEG/PDF 输出、二维码分享提供器、UA、文本选择菜单、收藏多账号直达和 200003 字节 Blob 跨块内容完整性。文件保存选择器由测试拦截并取消，不写入公共文件。厂商分享选择器可能绕过 ActivityMonitor，此时检查实际生成文件和内容提供器，再手动取消系统分享。tls 检查默认拒绝、一次性例外、再次访问询问、指纹绑定例外及 HTTPS 升级。`screenshotPreview` / `sharePreview` 各停留 30 秒用于合成页面视觉检查，再恢复设置和标签。异常中断遗留的合成标签可用 `cleanup` 清理；该清理也移除新功能测试设置，使其回到默认值。测试完成后移除端口转发并停止 fixture。JVM `NavigationPolicyTest` 检查协议升级、证书 origin 范围和备份不包含证书信任。

证书例外属于用户明确选择的兼容模式，偏离 Android 默认建议，不能把这种连接视为已验证身份：[WebViewClient SSL 处理](https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,android.webkit.SslErrorHandler,android.net.http.SslError))。原生默认仍取消，永久例外必须匹配 origin 和证书指纹；每次导航清除 WebView 自身 SSL 决策缓存。

0.11.1 回归增加了未编辑地址栏显示标题、菜单覆盖边缘翻页控件，以及只命名默认账号时的收藏子菜单检测。`controls blobCsp` 使用 `/csp` 页面测试限制 Blob 请求的 `connect-src 'self' ws: wss:` 策略，同时禁用 Blob fetch，验证直接对象读取、程序点击及立即 revoke 后的文件完整性。需要远端复现时，使用 `-e controls blobSite -e controlSite HTTPS_URL` 明确指定可控测试服务。检查在临时隔离账号中创建合成 Blob，不登录或发送真实文件，结束后删除临时账号；源码不预置私人服务器地址。

Blob 对象捕获在文档开始注册，只记录顶层页面创建且未撤销的下载大小范围内 Blob。撤销时释放引用；下载点击可临时保留当前对象，以适配立即 revoke 的网站。普通跨文档、跨 origin 和已关闭账号请求仍拒绝。不能通过移除 CSP 或全局降低 WebView 安全配置解决网站下载兼容问题。

## 界面本地化

运行 `python tools/localization.py` 检查三套资源的键、占位符和源码引用；更新源文案后运行 `python tools/localization.py --generate` 刷新 `TextResources.kt`。默认 `values/strings.xml` 为英语，`values-zh` 为简体中文，`values-b+zh+Hant` 为繁体中文。新增资源建议使用有意义的名称；`text_XXXX` 是这次一次性迁移生成的稳定 ID。

业务代码通过显式 `tr("源文案")` 或 `tr("包含 %1\$s 的模板", value)` 引用；简体源模板对应 Android 资源 ID，`formatted=false` 保留模板中的字面百分号。`LocalText` 只替换编号参数，支持译文重排，参数不再递归翻译或格式化。不要对网页正文、收藏标题、账号名称、用户脚本或 URL 调用翻译；动态 key 只用于受控的内部枚举标签。第三方 Readability 源码保持原样。

`AppLanguage` 使用 Android 13+ 原生 LocaleManager；旧版用独立本机偏好与本地化 Context。两者均默认跟随系统。BrowserActivity 自行处理 locale/layoutDirection 变化，刷新原生界面而保留已打开的 WebView 和 profile；设置目录按语言修订号失效重建，避免切换后搜索/分类残留旧语言。资源嗅探类别、历史范围、预置引擎和配色名称也在显示时解析。语言切换不关停离线文章的后台执行器。

JVM 的 LanguageTest 覆盖中文脚本/地区与系统语言列表回退、参数重排、用户参数不被二次处理、百分号/搜索模板保持、设置搜索及内部标签重新本地化。无设备时可完成资源检查、单元测试和 APK 构建，但不能声称已完成真机布局或 Android 系统设置交互验收。

Android 接口依据：[应用语言偏好](https://developer.android.com/guide/topics/resources/app-languages)。不新增 AppCompat、在线翻译 SDK 或服务器。

## 用户脚本扩展回归

`python tools/userscript_fixture.py` 配合 `adb reverse tcp:8895 tcp:8895`，在真实 WebView 验证存储、网络、依赖和安装：

```sh
adb shell am instrument -w -e userscripts true dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e userscripts install dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e userscripts remote dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e userscripts popular dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

接口测试使用独立 qa 文件、保留 .test 域名及合成本机响应。安装测试暂时关闭历史与 HTTPS-only，在临时标签上验证预览、默认关闭、启用和更新，清理合成脚本并恢复设置/标签。MIUI 阻止测试 Activity 后台启动时，从另一终端 `adb shell am start -n dev.qinglan.browser/.BrowserActivity`。

remote 只抓取官方脚本源码验证链接解析。popular 实时读取全语言总安装量榜单前 30 项，在独立库中下载依赖并安装为关闭状态，验证保存重读，不执行第三方代码；结果保存到 `files/qa-popular-report.json`，可通过 debug 的 run-as 取回。测试结束删除隔离安装库。不要把安装通过等同于各网站所有功能通过。

用户脚本的原生接口只使用 AndroidX WebMessageListener，不使用 addJavascriptInterface。每个脚本桥校验随机凭据、WebView 提供的 sourceOrigin、匹配规则和操作授权；请求不复用浏览器 Cookie 或证书例外。原生消息桥保持到 WebView 销毁，启用/更新时轮换凭据并替换注入脚本，避免删除正在派发回复的原生监听器。document-start 时 `WebView.url` 可能仍为旧地址，loadDataWithBaseURL 的历史地址也可能为 about:blank，身份检查依赖可信 sourceOrigin 和文档绑定的回复代理。

设备回归应使用模拟器或专用测试设备。需要延长亮屏时间时，先记录设备原值，在测试结束后恢复。不要在公开日志中包含设备序列号、账号、Cookie 或私有文件；USB 断开和锁屏可能影响界面测试。

## WebDAV 设置同步回归

`WebDavSyncTest` 覆盖 HTTPS 目录及凭据校验、设置白名单与语言、损坏/超大文件、错误响应不泄漏、禁止重定向、条件创建、ETag 更新和确认后复查。标准强 ETag 按原样发送；坚果云返回不加引号的版本标识，实测需要原样发送 `If-Match`，不能补引号。服务器条件请求语义参考 [HTTP 条件请求](https://www.rfc-editor.org/rfc/rfc9110.html#section-13)。

安装 Debug 和 AndroidTest APK 后运行：

```sh
adb shell am instrument -w -e webdav core dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

该检查使用单独的合成配置文件和 Keystore alias，验证加密存储、篡改拒绝/保留原文件、默认值恢复、其他偏好保留和原生设置入口；结束后恢复原设置并删除测试文件与密钥。MIUI 阻止后台 Activity 启动时，从另一终端启动 BrowserActivity。

真实服务测试仅显式运行 `-e webdav remote`：提前在目标应用的私有 `files/qa-webdav.json` 放入 `directory`、`username`、`password`，目录必须已存在且可写，凭据不得提交。检查会创建随机命名的 `qinglan-webdav-qa-*.json`，验证创建、下载、替换、确认期间的创建冲突和过期 ETag 拒绝，最后删除该文件及私有凭据。坚果云测试目录需位于已存在的同步文件夹下；测试后另行删除自行创建的空目录。

坚果云忽略 PUT 的 `If-None-Match: *`，因此上传在确认后再次读取远端并比较内容和 ETag；首次创建仍有服务器不提供原子条件创建带来的短暂竞态。已有文件更新通过 `If-Match` 实测返回 412 拒绝过期版本。单线程操作、返回不自动恢复、1 MB 上限和系统 HTTPS 校验不依赖浏览器 Cookie 或证书例外。

## 应用更新回归

`AppUpdatesTest` 覆盖语义版本比较、预发布/草稿排除、项目 APK 和来源 URL 校验、每日检查间隔、请求错误、响应大小及设置备份边界。更新使用 [GitHub latest release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)，不带认证信息，不复用浏览器 Cookie 或证书例外。

```sh
adb shell am instrument -w -e updates core dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e updates remote dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

core 使用独立更新状态和合成版本，验证关闭自动检查、每日频率、缓存重读、手动重查、更新详情和关于页，结束后恢复原偏好并删除测试状态。remote 仅请求项目公开 API，报告最新可安装正式版本或暂无版本，不下载或安装文件。公开仓库不存在可安装正式版本时，不应把 404 当作网络失败或已是最新版本。

`userscripts review` 把同批公开脚本保存到应用 cache/qa-popular-source 供静态分析，不执行代码。常用接口回归的剪贴板和新标签 API 使用合成接收器，不读取或覆盖用户剪贴板，也不打开外部网页。

安装入口另有 Greasy Fork 站点文档开始 hook：只接收官方 HTTPS 主框架的 .user.js 链接，真实点击先进入原生预览，避免站点扩展安装提示拦截；不会自动安装。安装回归使用受信任触摸事件与合成的扩展提示拦截器验证该顺序，并读取官方远程源码。

脚本执行放在 API 定义外层的内层异步作用域，避免管理器 clone 等辅助变量与脚本自己的顶层定义冲突。保存/安装时保留未变脚本的消息桥与凭据；只有停用或修改的脚本才撤销凭据。请求最多全局 32 个、每桥 8 个；已停用/销毁视图的排队请求不再发起。

## 主页返回网页的渲染回归

`adb shell am instrument -w -e controls navigation dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation` 使用临时标签和本机合成响应，连续验证三次「网页 → 主页 → 同标签打开网页」（包含地址栏输入）。检查旧 WebView 被恢复、文档 visibilityState 为 visible，以及设备截图的页面实际像素；结束后销毁临时标签并返回原标签，不修改设置或最近关闭记录。

原生主页会暂停保留的 WebView。所有重新显示网页的路径必须经过 `attach()` 恢复它，不能只把旧视图加回容器后调用 loadUrl，否则网络和 DOM 已完成但文档仍处于 hidden、没有首帧绘制。现场可通过无刷新切到后台再回来验证：若导航时间保持不变、内容随 onResume 出现，说明问题在视图生命周期。
