# 清岚 Android 浏览器

原生 Kotlin + Android 系统 WebView 的个人浏览器。默认顶部地址栏，底部为后退、前进、主页、标签页与菜单。最低 Android 8.0（API 26）。不内置浏览器内核。

## 使用

### 0.4.0 更新

- 主页长按至震动后继续拖动可排序；拖到文件夹中央移入，两侧用于排序。文件夹展开后可拖到「移回主页」区域移出。不移动、松手会在项目旁显示编辑和删除；名称与网址使用紧凑编辑框。
- 书签新增「编辑模式」：复选框批量选择、全选、移动到文件夹或根目录、批量删除；按住右侧移动柄即可上下排序。搜索时停用拖动，以免改变不可见项目的顺序。删除文件夹会保留未选中的子网站并移回根目录。
- 右下角菜单再次点击即收起。「添加到主页」与「收藏网页」合并为一个收藏入口，分别勾选书签和主页。
- 配色提供八种预设和自定义 RGB / `#RRGGBB` 色值。背景自动生成浅色或深色同色系，按钮文字自动选择黑白以保证对比度。
- 搜索引擎可命名保存多个自定义模板（最多 50 个），支持编辑和删除，并继续支持当前标签临时切换或设为默认。原有自定义搜索地址保留，可从管理页命名保存。搜索引擎列表与自定义颜色均随设置备份。

### 轻量用户脚本

设置 → 高级功能 → 用户脚本。支持导入 `.user.js` 或粘贴源代码，安装预览、手动启停、查看源代码、删除和单独导出。新脚本默认关闭，启停或删除后刷新网页生效。

- 支持 `@match`、`@include` 网址通配符、`@exclude`、`@exclude-match`，以及 `@run-at document-start/document-end/document-idle`。
- 支持 `GM_addStyle` / `GM.addStyle`、`GM_log` / `GM.log`、`GM_info` / `GM.info`、`unsafeWindow`。适合页面样式和内容修改类脚本。
- 仅在顶层 HTTP/HTTPS 网页执行，与网页共享 JavaScript 环境，没有独立扩展沙箱，也不向网页暴露原生文件、密码或 Cookie 管理接口。脚本仍能读取匹配网页本身的内容，应只启用可信脚本。
- **不是完整的 Tampermonkey 扩展实现**：不支持 `@require`、`@resource`、跨域请求、GM 存储、菜单命令等高级接口；声明不支持依赖或授权时拒绝安装。没有自动下载依赖或自动更新。脚本单文件上限 256 KB，最多 50 个，总共 2 MB。
- 使用系统 WebView 的文档开始注入能力；旧内核不具备该能力时不允许启用 document-start 脚本，其余脚本延后到加载完成执行。已打开页面的 DOM 修改要刷新后才会撤销。
- 脚本单独导出，不纳入普通设置备份。当前不支持直接点击安装网站链接安装。

实现参考：[Tampermonkey 元数据与接口文档](https://www.tampermonkey.net/documentation.php?locale=zh_CN)、[Android WebView 文档开始脚本接口](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E))。

### 0.3.0 更新

- 工具菜单与标签列表均在底栏上方显示。新建标签按钮使用普通底色；只有当前标签突出显示。设置 → 外观与主题可选每行 3～6 个工具。
- 设置分为外观与主题、操作习惯、高级功能、关于项目；使用统一的子页面和返回栈。增加青绿、海蓝、紫藤、暖金配色。
- 书签支持一层文件夹，长按可编辑、移动或删除；删除文件夹时网站移回根目录。收藏网页可以同时勾选书签和主页，分别选择文件夹。
- 历史单项固定高度，标题最多两行、网址一行。支持所有时间、最近一小时、今天、最近七天筛选，手动多选删除及按时段删除。删除前显示数量并确认。
- 主页、书签、历史、标签列表使用访问时由 WebView 提供的 favicon，并缓存在本机；未获取图标时使用首字符，不调用第三方图标服务。
- 全屏允许网页延伸到状态栏及屏幕开孔区域。长按链接可复制文本和添加到主页；文本选择的更多菜单中增加「清岚搜索」，同时注册系统网页搜索入口。
- 工具菜单增加本页朗读，使用系统 TTS 引擎，可暂停、继续、停止；离开应用时暂停，最多朗读前八万字符，效果依赖手机语音引擎及语言包。

### 密码与加密备份

- 设置 → 高级功能 → 密码管理。密码使用 Android Keystore 与 AES-GCM 存储在本机，支持手动新增、从当前已填写的表单读取后确认保存，以及手动填入；不自动提交登录。
- 仅支持 HTTPS、完全匹配的站点来源（含端口），不跨子域自动填入。仅处理主文档中的普通输入表单，多步骤登录、跨域 iframe、通行密钥暂不支持。不会从 Cookie 还原账号密码，也不会读取其他浏览器密码库。
- 备份可分别选择书签（含文件夹）、主页、设置、已保存密码；密码默认不勾选。**只要包含密码，就必须设置导出密码，导入时使用相同密码解密。没有明文密码导出入口。**
- `.qlb` 文件使用 PBKDF2-HMAC-SHA256（210,000 次、随机 16 字节盐）派生 AES-256 密钥，使用随机 12 字节 IV 的 GCM 加密并校验完整性。导出密码至少四个字符，建议使用更长的密码；忘记后无法解密已有文件。
- 不含密码的普通备份仍为 JSON，兼容旧版备份。恢复时可选分类；书签、主页、设置替换选中分类，密码按站点和账号合并。Cookie、历史、下载和标签会话仍不进入备份。单站 Cookie 管理保持独立。

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
- 书签支持 HTML 导入导出，导入时合并去重并保留文件夹；外部多层文件夹提升为一层文件夹。

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

默认执行 Release 构建、JVM 单元测试和 Android lint。脚本只为本次构建设置项目内 Java 临时目录，避免此电脑的 Windows Unix-domain-socket 临时路径问题。

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
- 不含云同步、内置广告过滤及无痕独立存储；密码管理为手动保存/填入，边界见上文。
- 原生扫码相机权限与网页权限分离；网页仍不能使用摄像头或麦克风。二维码识别使用 ZXing 3.5.4，许可证随应用提供。
- 网页兼容性取决于设备 WebView；主题变暗以 WebView 能力和网站样式为准。
