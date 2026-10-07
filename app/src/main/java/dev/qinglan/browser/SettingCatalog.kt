package dev.qinglan.browser

data class SettingSpec(
    val id:String, val title:String, val group:String, val description:String,
    val default:Any?=null, val values:List<Any> = emptyList(), val labels:List<String> = emptyList(),
    val keywords:String=""
) {
    fun valid(value:Any):Boolean = if(default is Boolean)value is Boolean else value in values
    fun display(value:Any):String = if(value is Boolean)labels.getOrNull(if(value)1 else 0)?:if(value)tr("开启") else tr("关闭") else labels.getOrElse(values.indexOf(value)){value.toString()}
}

/** Small, local metadata shared by settings search, defaults and backup validation. */
object SettingCatalog {
    private val specs get()=listOf(
        SettingSpec("httpsOnly",tr("仅 HTTPS 模式"),tr("网站与隐私"),tr("HTTP 地址自动尝试 HTTPS，阻止 HTTP 子资源和下载，不自动回退。校园登录页可能需要暂时关闭。"),false),
        SettingSpec("certificateExceptions",tr("证书异常处理"),tr("网站与隐私"),tr("默认阻止。开启后可询问信任一次或信任当前网址；网址例外绑定当前证书，可随时删除。"),false,labels=listOf(tr("阻止异常证书（默认）"),tr("询问是否信任"))),
        SettingSpec("certificateTrust",tr("已信任的证书网址"),tr("网站与隐私"),tr("查看并删除证书例外；证书改变时需要重新确认。")),
        SettingSpec("edgeScroll",tr("右侧翻页按钮"),tr("外观"),tr("在网页右侧中央显示回到顶部、上滑一页、下滑一页、跳到页底四个图标。"),false),
        SettingSpec("shareFormat",tr("链接分享格式"),tr("网页工具"),tr("每次询问，或直接以默认格式打开系统分享。"),"ask",listOf("ask","url","title","qr"),listOf(tr("每次询问"),tr("仅网址"),tr("标题 + 网址"),tr("二维码"))),
        SettingSpec("blobDownloads",tr("支持 Blob 下载"),tr("网页工具"),tr("读取当前网页生成的 Blob 文件，选择位置保存；每个文件最多 64 MB。"),false),
        SettingSpec("theme",tr("界面主题"),tr("外观"),tr("只改变浏览器界面；网页深色可单独设置。"),"system",listOf("system","light","dark"),listOf(tr("跟随系统"),tr("浅色"),tr("深色")),tr("夜间 日间")),
        SettingSpec("palette",tr("配色方案"),tr("外观"),tr("选择预设颜色或自定义配色。"),keywords=tr("颜色")),
        SettingSpec("menuLayout",tr("菜单定制"),tr("外观"),tr("显示常用工具，拖动排序；设置入口始终保留。"),keywords=tr("工具 隐藏 排序")),
        SettingSpec("menuColumns",tr("工具菜单每行数量"),tr("外观"),tr("列数越少，文字与按钮越宽。"),MenuLayout.defaultColumns,(3..6).toList(),(3..6).map{tr("每行 %1\$s 个", it)}),
        SettingSpec("bottomAddress",tr("地址栏位置"),tr("外观"),tr("顶部更熟悉，底部更容易单手操作。"),false,keywords=tr("顶部 底部 单手")),
        SettingSpec("autoHideAddress",tr("滚动时隐藏地址栏"),tr("外观"),tr("向下阅读时收起，向上滚动时重新显示。"),true,keywords=tr("全屏 自动隐藏")),
        SettingSpec("textZoom",tr("网页字号"),tr("外观"),tr("放大网页文字，不改变浏览器按钮大小；网站可单独覆盖。"),100,(50..200 step 5).toList(),(50..200 step 5).map{"$it%"},tr("缩放 字体 大小")),
        SettingSpec("restore",tr("恢复上次标签页"),tr("浏览与搜索"),tr("启动时恢复网址和标题；不保证恢复网页表单或登录后的临时页面。"),true,keywords=tr("启动 会话")),
        SettingSpec("localSuggestions",tr("地址栏本地建议"),tr("浏览与搜索"),tr("输入至少两个字符后匹配书签和历史。输入过程不会发给搜索服务。"),true,keywords=tr("联想 补全 隐私")),
        SettingSpec("externalNewTab",tr("外部链接新建标签"),tr("浏览与搜索"),tr("从其他应用打开链接时新建标签；关闭则复用当前标签。"),true),
        SettingSpec("collectionOpen",tr("打开收藏的方式"),tr("浏览与搜索"),tr("主页网站和书签的默认打开方式；长按链接仍可单独选择。"),"current",listOf("current","new","background"),listOf(tr("当前标签"),tr("新标签并切换"),tr("后台新标签"))),
        SettingSpec("activeWebViews",tr("保留的活动页面数"),tr("浏览与搜索"),tr("越少越省内存，但切回旧标签可能需要重新加载。不会增加最多 50 个标签的上限。"),4,listOf(2,4,6,8),listOf(tr("2 个 · 节省内存"),tr("4 个 · 均衡"),tr("6 个"),tr("8 个 · 保留更多页面")),tr("内存 性能")),
        SettingSpec("toolbarAction",tr("地址栏右侧按钮"),tr("外观"),tr("刷新按钮在加载时变为停止；编辑地址时变为清空。二维码始终可在更多工具中找到。"),"refresh",listOf("refresh","qr"),listOf(tr("刷新 / 停止"),tr("扫描二维码"))),
        SettingSpec("homeColumns",tr("主页每行数量"),tr("外观"),tr("调整主页网站图标密度，文件夹内部仍为三列。"),4,listOf(3,4,5),listOf(tr("3 个 · 宽松"),tr("4 个 · 默认"),tr("5 个 · 紧凑"))),
        SettingSpec("homeTitle",tr("显示主页标题"),tr("外观"),tr("关闭后减少主页顶部留白，直接显示收藏。"),true),
        SettingSpec("recordHistory",tr("记录浏览历史"),tr("网站与隐私"),tr("关闭后不再新增历史。Cookie、缓存和标签仍会保留，这不是独立无痕模式。"),true,keywords=tr("隐私 无痕")),
        SettingSpec("cameraPrompt",tr("允许网站询问摄像头"),tr("网站与隐私"),tr("只有前台 HTTPS 网站可询问，每次由你确认；关闭后直接拒绝。"),true,keywords=tr("权限 拍照 视频")),
        SettingSpec("microphonePrompt",tr("允许网站询问麦克风"),tr("网站与隐私"),tr("只有前台 HTTPS 网站可询问，每次由你确认；关闭后直接拒绝。"),true,keywords=tr("权限 录音 语音")),
        SettingSpec("locationPrompt",tr("允许网站询问位置"),tr("网站与隐私"),tr("只申请系统大致位置权限，每次确认，不保存网站永久许可。"),true,keywords=tr("权限 定位 地图")),
        SettingSpec("externalApps",tr("网页唤起外部应用"),tr("网站与隐私"),tr("仅响应前台网页中的用户点击；每次询问，或直接阻止。不会自动拉起其他应用。"),"ask",listOf("ask","block"),listOf(tr("每次询问"),tr("始终阻止")),tr("跳转 唤醒 拦截")),
        SettingSpec("autoplay",tr("允许媒体自动播放"),tr("网站与隐私"),tr("默认需要点击才播放。开启后可能产生声音或消耗流量；网站可以单独覆盖。"),false),
        SettingSpec("downloadWifiOnly",tr("下载仅使用 Wi-Fi"),tr("网页工具"),tr("只影响之后创建的下载任务；未连接 Wi-Fi 时由系统等待。"),false,keywords=tr("流量 网络 下载")),
        SettingSpec("search",tr("搜索引擎"),tr("浏览与搜索"),tr("设定默认搜索服务，也可在网站面板中仅更改当前标签。"),keywords=tr("百度 必应 Google 自定义 搜索")),
        SettingSpec("js",tr("默认允许 JavaScript"),tr("网站与隐私"),tr("关闭可能导致网页登录、菜单和视频不能工作。"),true,keywords=tr("脚本")),
        SettingSpec("thirdParty",tr("默认允许第三方 Cookie"),tr("网站与隐私"),tr("关闭有助减少跨站标识；部分嵌入登录需要单站点允许。"),false,keywords=tr("隐私 登录 饼干")),
        SettingSpec("desktop",tr("默认请求桌面网站"),tr("网站与隐私"),tr("请求电脑版本；不改变系统 WebView 内核。"),false,keywords=tr("UA 电脑模式")),
        SettingSpec("noImages",tr("默认无图模式"),tr("网站与隐私"),tr("减少图片加载；站点可以单独设置。"),false,keywords=tr("图片 流量")),
        SettingSpec("webDark",tr("允许网页深色"),tr("网站与隐私"),tr("深色主题下让系统尝试调整网页；图表异常时可仅对此站关闭。"),true,keywords=tr("夜间 暗色")),
        SettingSpec("siteOverrides",tr("网站例外"),tr("网站与隐私"),tr("查看改过设置的网站，单独恢复默认。"),keywords=tr("站点 继承 覆盖 权限")),
        SettingSpec("filter",tr("广告过滤"),tr("网页工具"),tr("规则订阅、网站例外、拦截记录与元素屏蔽。"),keywords=tr("广告 拦截 屏蔽")),
        SettingSpec("resourceSniffing",tr("收集网页资源"),tr("网页工具"),tr("发现图片和媒体地址；改变后刷新网页完全生效。"),true,keywords=tr("嗅探 视频 音频")),
        SettingSpec("scripts",tr("用户脚本"),tr("网页工具"),tr("管理本地脚本与匹配网站，仅导入可信脚本。"),keywords=tr("user.js 油猴")),
        SettingSpec("speech",tr("朗读控制"),tr("网页工具"),tr("使用系统语音引擎；离开应用暂停。"),keywords=tr("听书 语音 TTS")),
        SettingSpec("reader",tr("阅读模式"),tr("网页工具"),tr("本地提取文章，以纯文字显示；可调字号、朗读、导出。"),keywords=tr("正文 简洁")),
        SettingSpec("readerSize",tr("阅读模式字号"),tr("外观"),tr("只改变阅读模式正文的字号。"),20,(14..32 step 2).toList(),(14..32 step 2).map{tr("%1\$s号", it)},tr("阅读 字体")),
        SettingSpec("readerSpacing",tr("阅读模式行距"),tr("外观"),tr("调整纯文字正文的行间距。"),135,listOf(110,135,160),listOf(tr("紧凑"),tr("舒适"),tr("宽松"))),
        SettingSpec("readerKeepAwake",tr("阅读时保持亮屏"),tr("网页工具"),tr("仅在阅读模式显示时保持亮屏，离开后恢复系统行为。"),false),
        SettingSpec("speechRate",tr("朗读语速"),tr("网页工具"),tr("使用系统语音引擎，下次开始或继续朗读时生效。"),100,listOf(75,100,125,150,175,200),listOf(tr("0.75 倍"),tr("1 倍"),tr("1.25 倍"),tr("1.5 倍"),tr("1.75 倍"),tr("2 倍"))),
        SettingSpec("readingList",tr("离线文章"),tr("数据管理"),tr("在阅读模式保存纯文字正文，不含图片；无需网络再次阅读。"),keywords=tr("稍后阅读 保存 本地")),
        SettingSpec("print",tr("打印 / 保存 PDF"),tr("网页工具"),tr("调用系统打印，可选择保存为 PDF。"),keywords=tr("导出 网页")),
        SettingSpec("backup",tr("备份与恢复"),tr("数据管理"),tr("文件保存在你选择的位置，不需要清岚账号或服务器。"),keywords=tr("导入 导出 书签")),
        SettingSpec("webdav",tr("WebDAV 设置同步"),tr("数据管理"),tr("连接自己的 WebDAV 服务器，手动上传或恢复浏览器设置。"),keywords="WebDAV webdev "+tr("同步 坚果云 云端 设置")),
        SettingSpec("passwords",tr("密码管理"),tr("数据管理"),tr("本机加密，手动保存与填入；导出需要口令。"),keywords=tr("账号 登录")),
        SettingSpec("cookies",tr("Cookie 管理"),tr("数据管理"),tr("查看和管理网站 Cookie；导出可能包含登录凭据。"),keywords=tr("导入 导出 登录")),
        SettingSpec("clear",tr("清理浏览数据"),tr("数据管理"),tr("自行选择历史、缓存、Cookie 和网站存储。"),keywords=tr("删除 隐私 缓存")),
        SettingSpec("defaultBrowser",tr("默认浏览器"),tr("关于"),tr("前往系统设置选择默认浏览器。")),
        SettingSpec("help",tr("帮助与排错"),tr("关于"),tr("常见问题、权限设置和不含浏览数据的诊断信息。"),keywords=tr("下载失败 白屏 网页异常 反馈")),
        SettingSpec("about",tr("关于项目"),tr("关于"),tr("版本、系统 WebView、开源许可。"),keywords=tr("版本 内核 许可"))
    )
    data class Section(val title:String,val ids:List<String>)
    val sections get()=linkedMapOf(
        tr("外观") to listOf(Section(tr("主题与网页文字"),listOf("language","theme","palette","textZoom")),Section(tr("地址栏"),listOf("bottomAddress","toolbarAction","autoHideAddress","edgeScroll")),Section(tr("主页与菜单"),listOf("homeColumns","homeTitle","menuLayout","menuColumns"))),
        tr("浏览与搜索") to listOf(Section(tr("搜索"),listOf("search","localSuggestions")),Section(tr("链接与标签"),listOf("collectionOpen","externalNewTab","restore","activeWebViews"))),
        tr("网站与隐私") to listOf(Section(tr("网站例外"),listOf("siteOverrides","accounts","certificateTrust")),Section(tr("连接安全"),listOf("httpsOnly","certificateExceptions")),Section(tr("内容与显示"),listOf("js","desktop","noImages","webDark","autoplay")),Section(tr("隐私与跳转"),listOf("incognito","thirdParty","recordHistory","externalApps")),Section(tr("网站权限询问"),listOf("cameraPrompt","microphonePrompt","locationPrompt"))),
        tr("工具") to listOf(Section(tr("标签整理"),listOf("tabSearch","undo","closeOtherTabs")),Section(tr("阅读与朗读"),listOf("reader","readerSize","readerSpacing","readerKeepAwake","speech","speechRate")),Section(tr("网页处理"),listOf("filter","scripts","resourceSniffing","downloadWifiOnly","blobDownloads","shareFormat","print","tools"))),
        tr("数据管理") to listOf(Section(tr("保存的数据"),listOf("readingList","passwords","cookies")),Section(tr("备份与清理"),listOf("backup","webdav","clear"))),
        tr("关于") to listOf(Section("",listOf("defaultBrowser","help","about")))
    )
    private val extra get()=listOf(
        SettingSpec("language",tr("语言 / Language"),tr("外观"),tr("默认跟随系统。更改只影响浏览器界面，不翻译网页或修改收藏、账号名称。"),keywords=tr("语言 英语 简体 繁体 language English")),
        SettingSpec("accounts",tr("网站多账号"),tr("网站与隐私"),tr("为网站保存多个独立登录空间，切换当前标签，其他标签不变。"),keywords=tr("切换 账号 登录 Cookie 容器 工作 个人")),
        SettingSpec("incognito",tr("无痕模式"),tr("网站与隐私"),tr("独立 Cookie 与网站存储，不记录历史；退出时清理无痕网站数据。"),keywords=tr("隐私 匿名 私密")),
        SettingSpec("tabSearch",tr("搜索标签"),tr("工具"),tr("按标题或网址查找已打开的标签。")),
        SettingSpec("undo",tr("撤销关闭标签"),tr("工具"),tr("恢复最近关闭的标签；最多 10 条，保留 10 分钟，重启后清空。")),
        SettingSpec("closeOtherTabs",tr("关闭其他标签"),tr("工具"),tr("确认后只保留当前标签，未提交的表单无法恢复。")),
        SettingSpec("tools",tr("全部工具"),tr("工具"),tr("查看阅读、页面、收藏、网站等全部操作。"))
    )
    private var cachedRevision=-1
    private var cachedEntries=emptyList<SettingSpec>()
    val entries:List<SettingSpec> get()=catalog()
    @Synchronized private fun catalog():List<SettingSpec>{
        if(cachedRevision!=LocalText.revision){
            val available=(specs+extra).associateBy{it.id}
            cachedEntries=sections.flatMap{(group,parts)->parts.flatMap{it.ids}.map{id->available.getValue(id).copy(group=group)}}
            cachedRevision=LocalText.revision
        }
        return cachedEntries
    }
    val groups get()=sections.keys.toList()
    fun find(id:String)=entries.firstOrNull{it.id==id}
    fun search(query:String):List<SettingSpec> {
        val words=query.trim().lowercase().split(Regex("\\s+")).filter{it.isNotEmpty()}
        if(words.isEmpty())return entries
        return entries.filter{s->val text="${s.title} ${s.group} ${s.description} ${s.keywords} ${s.id}".lowercase();words.all{text.contains(it)}}
            .sortedByDescending{s->words.count{s.title.lowercase().contains(it)}}
    }
}
