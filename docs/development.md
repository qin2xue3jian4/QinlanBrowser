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

JVM 测试覆盖 Cookie 校验、备份加密、收藏整理、搜索引擎、二维码、脚本匹配、广告规则语法及订阅配置。发布工具测试覆盖版本标签校验和校验文件生成。

需要设备的 WebView 检查可通过 instrumentation 运行：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e revision4 scripts dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
adb shell am instrument -w -e filtering true dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
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
