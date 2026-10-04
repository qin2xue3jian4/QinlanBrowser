package dev.qinglan.browser

data class SettingSpec(
    val id:String, val title:String, val group:String, val description:String,
    val default:Any?=null, val values:List<Any> = emptyList(), val labels:List<String> = emptyList(),
    val keywords:String=""
) {
    fun valid(value:Any):Boolean = if(default is Boolean)value is Boolean else value in values
    fun display(value:Any):String = if(value is Boolean)labels.getOrNull(if(value)1 else 0)?:if(value)"开启" else "关闭" else labels.getOrElse(values.indexOf(value)){value.toString()}
}

/** Small, local metadata shared by settings search, defaults and backup validation. */
object SettingCatalog {
    val entries=listOf(
        SettingSpec("theme","界面主题","外观","只改变浏览器界面；网页深色可单独设置。","system",listOf("system","light","dark"),listOf("跟随系统","浅色","深色"),"夜间 日间"),
        SettingSpec("palette","配色方案","外观","选择预设颜色或自定义配色。",keywords="颜色"),
        SettingSpec("menuLayout","菜单定制","外观","显示常用工具，拖动排序；设置入口始终保留。",keywords="工具 隐藏 排序"),
        SettingSpec("menuColumns","工具菜单每行数量","外观","列数越少，文字与按钮越宽。",3,(3..6).toList(),(3..6).map{"每行 $it 个"}),
        SettingSpec("bottomAddress","地址栏位置","外观","顶部更熟悉，底部更容易单手操作。",false,keywords="顶部 底部 单手"),
        SettingSpec("autoHideAddress","滚动时隐藏地址栏","外观","向下阅读时收起，向上滚动时重新显示。",true,keywords="全屏 自动隐藏"),
        SettingSpec("textZoom","网页字号","外观","放大网页文字，不改变浏览器按钮大小；网站可单独覆盖。",100,(50..200 step 5).toList(),(50..200 step 5).map{"$it%"},"缩放 字体 大小"),
        SettingSpec("restore","恢复上次标签页","浏览与搜索","启动时恢复网址和标题；不保证恢复网页表单或登录后的临时页面。",true,keywords="启动 会话"),
        SettingSpec("localSuggestions","地址栏本地建议","浏览与搜索","输入至少两个字符后匹配书签和历史。输入过程不会发给搜索服务。",true,keywords="联想 补全 隐私"),
        SettingSpec("externalNewTab","外部链接新建标签","浏览与搜索","从其他应用打开链接时新建标签；关闭则复用当前标签。",true),
        SettingSpec("collectionOpen","打开收藏的方式","浏览与搜索","主页网站和书签的默认打开方式；长按链接仍可单独选择。","current",listOf("current","new","background"),listOf("当前标签","新标签并切换","后台新标签")),
        SettingSpec("activeWebViews","保留的活动页面数","浏览与搜索","越少越省内存，但切回旧标签可能需要重新加载。不会增加最多 50 个标签的上限。",4,listOf(2,4,6,8),listOf("2 个 · 节省内存","4 个 · 均衡","6 个","8 个 · 保留更多页面"),"内存 性能"),
        SettingSpec("toolbarAction","地址栏右侧按钮","外观","刷新按钮在加载时变为停止；编辑地址时变为清空。二维码始终可在更多工具中找到。","refresh",listOf("refresh","qr"),listOf("刷新 / 停止","扫描二维码")),
        SettingSpec("homeColumns","主页每行数量","外观","调整主页网站图标密度，文件夹内部仍为三列。",4,listOf(3,4,5),listOf("3 个 · 宽松","4 个 · 默认","5 个 · 紧凑")),
        SettingSpec("homeTitle","显示主页标题","外观","关闭后减少主页顶部留白，直接显示收藏。",true),
        SettingSpec("recordHistory","记录浏览历史","网站与隐私","关闭后不再新增历史。Cookie、缓存和标签仍会保留，这不是独立无痕模式。",true,keywords="隐私 无痕"),
        SettingSpec("cameraPrompt","允许网站询问摄像头","网站与隐私","只有前台 HTTPS 网站可询问，每次由你确认；关闭后直接拒绝。",true,keywords="权限 拍照 视频"),
        SettingSpec("microphonePrompt","允许网站询问麦克风","网站与隐私","只有前台 HTTPS 网站可询问，每次由你确认；关闭后直接拒绝。",true,keywords="权限 录音 语音"),
        SettingSpec("locationPrompt","允许网站询问位置","网站与隐私","只申请系统大致位置权限，每次确认，不保存网站永久许可。",true,keywords="权限 定位 地图"),
        SettingSpec("externalApps","网页唤起外部应用","网站与隐私","仅响应前台网页中的用户点击；每次询问，或直接阻止。不会自动拉起其他应用。","ask",listOf("ask","block"),listOf("每次询问","始终阻止"),"跳转 唤醒 拦截"),
        SettingSpec("autoplay","允许媒体自动播放","网站与隐私","默认需要点击才播放。开启后可能产生声音或消耗流量；网站可以单独覆盖。",false),
        SettingSpec("downloadWifiOnly","下载仅使用 Wi-Fi","网页工具","只影响之后创建的下载任务；未连接 Wi-Fi 时由系统等待。",false,keywords="流量 网络 下载"),
        SettingSpec("search","搜索引擎","浏览与搜索","设定默认搜索服务，也可在网站面板中仅更改当前标签。",keywords="百度 必应 Google 自定义 搜索"),
        SettingSpec("js","默认允许 JavaScript","网站与隐私","关闭可能导致网页登录、菜单和视频不能工作。",true,keywords="脚本"),
        SettingSpec("thirdParty","默认允许第三方 Cookie","网站与隐私","关闭有助减少跨站标识；部分嵌入登录需要单站点允许。",false,keywords="隐私 登录 饼干"),
        SettingSpec("desktop","默认请求桌面网站","网站与隐私","请求电脑版本；不改变系统 WebView 内核。",false,keywords="UA 电脑模式"),
        SettingSpec("noImages","默认无图模式","网站与隐私","减少图片加载；站点可以单独设置。",false,keywords="图片 流量"),
        SettingSpec("webDark","允许网页深色","网站与隐私","深色主题下让系统尝试调整网页；图表异常时可仅对此站关闭。",true,keywords="夜间 暗色"),
        SettingSpec("siteOverrides","网站例外","网站与隐私","查看改过设置的网站，单独恢复默认。",keywords="站点 继承 覆盖 权限"),
        SettingSpec("filter","广告过滤","网页工具","规则订阅、网站例外、拦截记录与元素屏蔽。",keywords="广告 拦截 屏蔽"),
        SettingSpec("resourceSniffing","收集网页资源","网页工具","发现图片和媒体地址；改变后刷新网页完全生效。",true,keywords="嗅探 视频 音频"),
        SettingSpec("scripts","用户脚本","网页工具","管理本地脚本与匹配网站，仅导入可信脚本。",keywords="user.js 油猴"),
        SettingSpec("speech","朗读控制","网页工具","使用系统语音引擎；离开应用暂停。",keywords="听书 语音 TTS"),
        SettingSpec("reader","阅读模式","网页工具","本地提取文章，以纯文字显示；可调字号、朗读、导出。",keywords="正文 简洁"),
        SettingSpec("readerSize","阅读模式字号","外观","只改变阅读模式正文的字号。",20,(14..32 step 2).toList(),(14..32 step 2).map{"${it}号"},"阅读 字体"),
        SettingSpec("readerSpacing","阅读模式行距","外观","调整纯文字正文的行间距。",135,listOf(110,135,160),listOf("紧凑","舒适","宽松")),
        SettingSpec("readerKeepAwake","阅读时保持亮屏","网页工具","仅在阅读模式显示时保持亮屏，离开后恢复系统行为。",false),
        SettingSpec("speechRate","朗读语速","网页工具","使用系统语音引擎，下次开始或继续朗读时生效。",100,listOf(75,100,125,150,175,200),listOf("0.75 倍","1 倍","1.25 倍","1.5 倍","1.75 倍","2 倍")),
        SettingSpec("readingList","离线文章","数据管理","在阅读模式保存纯文字正文，不含图片；无需网络再次阅读。",keywords="稍后阅读 保存 本地"),
        SettingSpec("print","打印 / 保存 PDF","网页工具","调用系统打印，可选择保存为 PDF。",keywords="导出 网页"),
        SettingSpec("backup","备份与恢复","数据管理","文件保存在你选择的位置，不需要清岚账号或服务器。",keywords="导入 导出 书签"),
        SettingSpec("passwords","密码管理","数据管理","本机加密，手动保存与填入；导出需要口令。",keywords="账号 登录"),
        SettingSpec("cookies","Cookie 管理","数据管理","查看和管理网站 Cookie；导出可能包含登录凭据。",keywords="导入 导出 登录"),
        SettingSpec("clear","清理浏览数据","数据管理","自行选择历史、缓存、Cookie 和网站存储。",keywords="删除 隐私 缓存"),
        SettingSpec("defaultBrowser","默认浏览器","关于","前往系统设置选择默认浏览器。"),
        SettingSpec("help","帮助与排错","关于","常见问题、权限设置和不含浏览数据的诊断信息。",keywords="下载失败 白屏 网页异常 反馈"),
        SettingSpec("about","关于项目","关于","版本、系统 WebView、开源许可。",keywords="版本 内核 许可")
    )
    val groups=entries.map{it.group}.distinct()
    fun find(id:String)=entries.firstOrNull{it.id==id}
    fun search(query:String):List<SettingSpec> {
        val words=query.trim().lowercase().split(Regex("\\s+")).filter{it.isNotEmpty()}
        if(words.isEmpty())return entries
        return entries.filter{s->val text="${s.title} ${s.group} ${s.description} ${s.keywords} ${s.id}".lowercase();words.all{text.contains(it)}}
            .sortedByDescending{s->words.count{s.title.lowercase().contains(it)}}
    }
}
