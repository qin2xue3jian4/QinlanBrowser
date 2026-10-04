# 清岚 Android 浏览器

原生 Kotlin + Android 系统 WebView 的个人浏览器。默认顶部地址栏，底部为后退、前进、主页、标签页与菜单。最低 Android 8.0（API 26）。不内置浏览器内核。

## 使用

### 0.2.0 更新

- 长按底栏标签按钮直接新建标签；点击显示底栏上方的同色列表。每项固定 48 dp，标题最多两行并省略，新建入口在列表末尾。
- 向下浏览自动隐藏地址栏，回滑显示；设置中可关闭，也适用于网页内部滚动区域。
- 进入夜间模式后菜单改为「日间模式」。
- 地址栏左侧快捷设置可选择必应、百度、Google、DuckDuckGo、搜狗、360 或自定义搜索地址。支持仅当前标签页或永久默认；临时选择不会保存到下次启动。
- 书签、历史、下载、设置使用带返回导航的独立全屏页面。
- 地址栏右侧为二维码扫描，支持相机及系统选图。识别 HTTP/HTTPS 网址直接打开；其他二维码显示文本。刷新仍在菜单中。
- 设置 → 备份与恢复：按书签、主页（含文件夹）、浏览器及网站设置分别选择导出。导入先校验，再选择要替换的分类；未选择的分类保留。始终排除 Cookie、历史、下载记录、标签会话及网站存储。

- 首页「添加」可创建网站或文件夹。长按网站可编辑、移动到文件夹或主页、向前移动及删除。长按文件夹可重命名、排序或删除；删除文件夹会把其中的网站移回主页。
- 点击文件夹弹出子网站网格。文件夹为一层结构。
- 菜单包含书签、历史、下载、Cookie 管理、电脑模式、夜间模式、无图模式、刷新、页面查找、全屏、添加到主页、收藏、网站设置、设置与分享。
- 按系统返回键退出全屏。长按网页链接可新标签打开、收藏或复制；长按图片可查看、下载或复制。
- 地址栏左侧进入当前网站设置，可独立设置电脑模式、JavaScript、第三方 Cookie、网页深色及无图模式。
- 书签支持 HTML 导入导出，导入时合并去重；外部书签文件夹目前平铺导入。

## Cookie

先打开目标网站，然后从菜单进入 Cookie 管理。支持 Cookie-Editor JSON 文件、粘贴 JSON、导入预览、导出、编辑和删除。

- 管理本应用自己的 WebView Cookie，不访问 Edge、Via 或其他应用的数据库。
- 读取范围是当前 URL 可访问的 Cookie；不同路径、子域名应分别访问和导出。
- 保留 name/value/domain/path/secure/httpOnly/hostOnly/sameSite/expirationDate/session；分区 Cookie 明确跳过，不静默伪造属性。
- 根据 Chromium 规范化域名的前导点区分域 Cookie 与 host-only Cookie，兼容 `__Host-` 前缀。
- 导入按名称、域名、路径合并，拒绝无关域名、控制字符、无效路径、过期或不支持的条目。Cookie 存储仍由 WebView 最终校验；其有效期上限等策略仍会生效。
- 导入成功不保证目标网站接受登录态。网站可能要求新的登录或验证。
- 导出文件是明文登录凭据，应保存在可信位置。应用不会把 Cookie 值写入日志。

## 构建

需要 JDK 17+、Android SDK 36、Build Tools 36.0.0。Gradle Wrapper 固定 9.3.1，Android Gradle Plugin 固定 9.1.0。AGP 内置 Kotlin。

在不提交的 `local.properties` 中配置 SDK 路径，例如：

```properties
sdk.dir=D\:/ProgramFiles/Android/Sdk
```

Windows：

```powershell
.\build.ps1
```

默认执行 Release 构建、Cookie JVM 单元测试和 Android lint。脚本只为本次构建设置项目内 Java 临时目录，避免此电脑的 Windows Unix-domain-socket 临时路径问题。

常用目标：

```powershell
.\build.ps1 -Tasks ':app:assembleDebug', ':app:assembleDebugAndroidTest'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.qinglan.browser.test/dev.qinglan.browser.CookieInstrumentation
```

Release 为便于个人直接安装，使用本机 Android 调试签名，但构建本身不可调试、启用 R8 和资源压缩，关闭 WebView 调试。后续升级必须保留相同签名。商店分发前应单独配置发行签名。

## 验证

- JVM：Cookie JSON 往返、域名边界、注入/过期/分区条目、前缀规则、同名不同路径、重复条目、精确删除、WebView canonical host-only 表示。
- 真机 instrumentation：只在保留的 `.test` 域使用合成值，覆盖 WebView 属性、JSON 往返、路径隔离、有效期、host-only 前缀与删除。
- `tools/fixture_server.py` 提供本地合成网页及需合成 Cookie 才允许的下载，用 `adb reverse tcp:8765 tcp:8765` 连接手机。
- `tools/device_ui.py` 用于本项目的 ADB 截图及界面检查。
- `tools/import_authorized_cookie_file.py` 是仅测试环境使用的显式授权导入工具；将指定文件传入可调试应用私有存储。测试 runner 的 `-e importAuthorizedFile true` 路径完成导入后删除副本，仅输出数量。此工具和测试 runner 不包含在正式 APK 中。

真实 Cookie 文件放在 `.local/`；该目录、截图、测试输出及签名文件均已加入 `.gitignore`。

## 首版边界

- 同时最多 50 个标签，保留最近 4 个 WebView，其余按需恢复。进程重启恢复标签标题和 URL，不承诺恢复表单与滚动位置。
- 普通 HTTP/HTTPS 下载使用系统 DownloadManager，并传递目标 URL 的 Cookie、UA 与 Referer。暂不支持 Blob 下载、媒体嗅探或下载队列的暂停/重试管理。
- 支持系统文件选择器上传与视频全屏；网页实时摄像头、麦克风和定位权限暂不开放。
- 不含云同步、广告过滤、油猴脚本、密码管理及无痕独立存储。
- 原生扫码相机权限与网页权限分离；网页仍不能使用摄像头或麦克风。二维码识别使用 ZXing 3.5.4，许可证随应用提供。
- 网页兼容性取决于设备 WebView；主题变暗以 WebView 能力和网站样式为准。
