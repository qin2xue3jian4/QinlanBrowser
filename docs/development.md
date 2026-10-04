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
