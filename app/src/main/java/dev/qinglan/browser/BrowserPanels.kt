@file:Suppress("DEPRECATION")
package dev.qinglan.browser
import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.view.*
import android.webkit.*
import android.widget.*

class BrowserPanels(val a:BrowserActivity){
    val u get()=a.ui
    val store get()=a.store
    val prefs get()=a.prefs
    val pages=PageHost(a)
    val library=LibraryPanels(this)
    val vault=VaultPanels(this)
    val cookie=CookiePanels(this)
    val accounts=AccountPanels(this)
    val searches=SearchPanels(this)
    val appearancePanels=AppearancePanels(this)
    val scripts=ScriptPanels(this)
    val filter=FilterPanels(this)
    val resources=ResourcePanels(this)
    val reader=ReaderPanels(this)
    val settingsUi=SettingsPanels(this)
    val menuPanels=MenuPanels(this)
    fun dialog(view:View,bottom:Boolean=true):Dialog {val scroll=ScrollView(a).apply{addView(view)};val d=Dialog(a);if(a.isIncognito)d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE);d.setContentView(scroll);d.window?.setBackgroundDrawable(u.round(u.panel,24));d.show();d.window?.apply{setGravity(if(bottom)Gravity.BOTTOM else Gravity.CENTER);setLayout(a.resources.displayMetrics.widthPixels-u.dp(20),-2)};return d}
    fun info(title:String,message:String){pages.show(title){it.addView(u.label(message))}}
    fun confirm(title:String,message:String,run:()->Unit){pages.show(title){col->col.addView(u.label(message));col.addView(u.button(tr("确认"),true){pages.back();run()});col.addView(u.button(tr("取消")){pages.back()})}}
    fun choose(title:String,options:List<String>,selected:Int=-1,run:(Int)->Unit){pages.show(title){col->options.forEachIndexed{i,s->col.addView(u.item((if(i==selected)"✓ "else"")+s){pages.back();run(i)})}}}
    fun cookies(){if(a.isIncognito)a.toast(tr("无痕 Cookie 随会话隔离，退出时清理"))else cookie.show()}
    fun menu()=menuPanels.show()
    fun menuEditor()=menuPanels.editor()
    fun tabs(){if(a.dismissTabs())return;renderTabs()}
    private fun renderTabs(){val col=u.column(4)
        a.tabs.toList().forEachIndexed{i,t->val row=u.row().apply{setBackgroundColor(if(i==a.selected)u.soft else u.panel)};row.addView(a.icons.view(u,t.title,t.url));row.addView(u.label(a.accountTabTitle(t),14f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER_VERTICAL;setPadding(u.dp(8),0,0,0);setOnClickListener{a.switchTab(a.tabs.indexOf(t))}},LinearLayout.LayoutParams(0,u.dp(48),1f));row.addView(u.icon("close",tr("关闭 %1\$s", t.title.take(50))){a.closeTab(t.id);renderTabs()});col.addView(row,LinearLayout.LayoutParams(-1,u.dp(48)));col.addView(u.rule())}
        fun plain(button:Button)=button.apply{background=u.round(u.panel,0);backgroundTintList=null;stateListAnimator=null}
        val footer=u.row();footer.addView(plain(u.button(tr("＋ 新建标签页")){a.newHome()}),LinearLayout.LayoutParams(0,u.dp(48),1f))
        footer.addView(plain(u.button(tr("关闭全部标签页")){confirmCloseAllTabs()}),LinearLayout.LayoutParams(0,u.dp(48),1f));col.addView(footer);a.showTabs(col)
    }
    fun confirmCloseAllTabs():AlertDialog {
        a.dismissTabs()
        val dialog=AlertDialog.Builder(a)
            .setTitle(tr("关闭全部标签页？"))
            .setMessage(tr("未提交的表单无法恢复。普通标签可通过撤销关闭恢复。"))
            .setNegativeButton(tr("取消"),null)
            .setPositiveButton(tr("确认")){_,_->a.closeAllTabs()}.create()
        if(a.isIncognito)dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        return dialog
    }
    fun closeOtherTabs(){if(a.tabs.size<2){a.toast(tr("没有其他标签"));return};confirm(tr("关闭其他 %1\$s 个标签？", a.tabs.size-1),tr("当前页面会保留。未提交的表单无法通过撤销恢复。")){pages.close();a.closeOtherTabs()}}
    fun searchTabs(){var query="";pages.show(tr("搜索标签")){col->
        val input=u.edit(tr("搜索标题或网址"),query);col.addView(input);val results=u.column();col.addView(results)
        fun render(query:String){results.removeAllViews();val found=a.tabs.filter{it.title.contains(query,true)||it.url.contains(query,true)}
            if(found.isEmpty())results.addView(u.label(tr("没有匹配的标签")))
            found.forEach{tab->results.addView(u.item(tab.title,tab.url){pages.close();a.switchTab(a.tabs.indexOf(tab))})}}
        input.onChange{query=it;render(it)};render(query)
    }}
    fun site(){if(a.isIncognito){pages.show(tr("无痕网站信息")){col->col.addView(u.label(a.currentUrl,14f));col.addView(u.label(tr("无痕会话不与普通浏览共享登录；网站规则继承普通设置，无痕中不保存新的站点设置。")));col.addView(u.button(tr("退出无痕模式")){a.privateMode()})};return};val url=a.currentUrl;pages.show(tr("网站设置")){col->
        if(a.isHttp(url)||a.current?.accountId.orEmpty().isNotEmpty())col.addView(u.item(tr("网站账号 · %1\$s", a.accounts.label(a.current?.accountId.orEmpty(),a.store.siteKey(url))),tr("只切换当前标签，其他网站与标签不变")){accounts.current()})
        col.addView(u.item(tr("搜索引擎 · %1\$s", searches.name(a.current?.searchOverride?:prefs.getString("search",SearchEngines.default)!!)),if(a.current?.searchOverride!=null)tr("仅当前标签页")else tr("使用默认搜索引擎")){searchEngine()})
        if(a.isHttp(url)){
            col.addView(u.label(Uri.parse(url).host.orEmpty(),18f))
            col.addView(u.label(if(a.current?.error!=null)tr("页面未成功加载，无法确认连接状态。")else if(a.current?.let{a.security.trusted(it)}==true)tr("已使用证书例外，网站身份未经可信验证。")else if(url.startsWith("https://"))tr("HTTPS 连接；加密连接不代表网站内容可信。")else tr("HTTP 连接未加密，请勿输入敏感信息。"),13f,u.muted))
            col.addView(u.item(tr("Cookie 管理")){cookies()});col.addView(u.item(tr("本网站密码"),tr("手动保存与填入")){vault.passwords()})
            fun option(title:String,key:String,default:Boolean){val scope=if(store.siteOverridden(url,key))tr("此网站单独设置")else tr("继承默认")
                val enabled=store.siteBool(url,key,default)
                col.addView(u.item(title,"$scope · ${if(enabled)tr("开启")else tr("关闭")}"){choose(title,listOf(tr("继承默认（%1\$s）", if(default)tr("开启")else tr("关闭")),tr("仅此网站开启"),tr("仅此网站关闭")),if(!store.siteOverridden(url,key))0 else if(enabled)1 else 2){which->
                    if(which==0)prefs.edit().remove("site.${store.siteKey(url)}.$key").apply()else store.setSiteBool(url,key,which==1)
                    a.current?.web?.let{a.configure(it,url)};pages.refresh()
                }})}
            option(tr("广告过滤"),"adblock",true);option(tr("请求桌面网站"),"desktop",prefs.getBoolean("desktop",false))
            option("JavaScript","js",prefs.getBoolean("js",true));option(tr("第三方 Cookie"),"thirdParty",prefs.getBoolean("thirdParty",false));option(tr("媒体自动播放"),"autoplay",prefs.getBoolean("autoplay",false))
            option(tr("允许询问摄像头"),"camera",prefs.getBoolean("cameraPrompt",true));option(tr("允许询问麦克风"),"microphone",prefs.getBoolean("microphonePrompt",true));option(tr("允许询问大致位置"),"location",prefs.getBoolean("locationPrompt",true))
            option(tr("网页深色"),"dark",prefs.getBoolean("webDark",true));option(tr("无图模式"),"noImages",prefs.getBoolean("noImages",false))
            col.addView(u.item(tr("网页字号"),"${store.siteZoom(url)}% · ${if(store.siteOverridden(url,"textZoom"))tr("此网站")else tr("继承默认")}"){settingsUi.zoom(url)})
            col.addView(u.button(tr("应用并刷新"),true){pages.close();a.reload()})
            col.addView(u.button(tr("恢复此网站默认")){store.resetSite(url);a.current?.web?.let{a.configure(it,url)};pages.refresh()})
            col.addView(u.label(tr("设置更改后刷新网页完全生效。恢复默认不删除登录数据。"),12f,u.muted))
        }
    }}
    fun settings()=settingsUi.show()
    fun help(){pages.show(tr("帮助与排错")){col->
        listOf(
            tr("网页打不开或反复出错") to tr("检查网址、网络和设备时间。证书异常默认停止；可在证书异常处理设置中开启逐次询问，选择信任一次或信任当前网址。仅 HTTPS 模式不会回退 HTTP，校园登录页可能需要暂时关闭该模式。"),
            tr("网页白屏、登录或按钮失效") to tr("先在网站设置中检查 JavaScript 和第三方 Cookie，尝试恢复此网站默认并刷新。广告过滤可能误拦截，可仅对此网站关闭后比较。用户脚本也可能影响页面，可逐个停用排查。"),
            tr("下载一直没有进度") to tr("下载由系统下载器处理。检查网络、可用存储空间，以及是否开启仅 Wi-Fi 下载。部分系统会先等待或重试；系统未返回失败前清岚不能判断原因。可取消任务后重试，或复制来源链接交给其他下载工具。带登录的链接可能会过期。"),
            tr("网站无法使用相机、麦克风或位置") to tr("只支持前台 HTTPS 同源网站的逐次请求。检查网站设置是否允许询问，再检查系统是否授予清岚对应权限。跨源嵌入页面和未知权限类型会被拒绝；位置只使用大致位置。"),
            tr("离线文章与备份") to tr("阅读模式保存的是纯文字正文，最多 100 篇 / 20 MB，不包含图片。离线文章保存在本机，卸载清岚会删除；重要文章可导出 TXT。设置备份不包含离线文章、Cookie、历史、下载和标签。"),
            tr("关闭历史是否等于无痕") to tr("不是。关闭历史记录只停止新增浏览历史，Cookie、缓存、当前标签与下载仍会保留。如需隔离登录和网站数据，请从菜单进入无痕模式；退出时清理该会话，主动保存的文件和书签仍会保留。")
        ).forEach{(title,body)->col.addView(u.item(title){info(title,body)})}
        col.addView(u.item(tr("系统应用权限")){runCatching{a.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${a.packageName}")))}.onFailure{a.toast(tr("请打开系统应用设置"))}})
        col.addView(u.button(tr("复制诊断信息")){a.copy(tr("清岚诊断"),tr("清岚 %1\$s (%2\$s)\nAndroid %3\$s / API %4\$s\nWebView %5\$s\n广告过滤：%6\$s\n用户脚本：请在反馈时说明是否启用\n请补充问题现象和复现步骤。", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, WebView.getCurrentWebViewPackage()?.versionName?:tr("未知"), prefs.getBoolean("adblockEnabled",true)))})
        col.addView(u.label(tr("诊断信息不包含网址、Cookie、密码或历史。复制后由你决定是否分享。"),12f,u.muted))
    }}
    fun about(){pages.show(tr("关于项目")){col->col.addView(u.item(tr("正文提取开源许可"),"Mozilla Readability 0.6.0 · Apache 2.0"){info("Mozilla Readability",a.assets.open("reader/NOTICE.txt").bufferedReader().use{it.readText()}+"\n\n"+a.assets.open("reader/LICENSE.md").bufferedReader().use{it.readText()})});col.addView(u.label(tr("清岚 %1\$s\nAndroid %2\$s\nWebView %3\$s\n\n使用系统 WebView，数据保存在本机。", BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, WebView.getCurrentWebViewPackage()?.versionName?:tr("未知"))));col.addView(u.item(tr("过滤规则与公共后缀表许可")){filter.information()});col.addView(u.item(tr("MPL 2.0 许可证")){info("MPL 2.0",a.assets.open("MPL-2.0.txt").bufferedReader().use{it.readText()})});col.addView(u.item(tr("开源许可"),"ZXing · Apache License 2.0"){info(tr("ZXing 开源许可"),a.assets.open("zxing-LICENSE.txt").bufferedReader().use{it.readText()})})}}
    fun searchEngine(permanent:Boolean=false)=searches.show(permanent)
    fun clearData(){pages.show(tr("清理浏览数据")){col->col.addView(u.label(tr("历史记录共用；以下缓存、Cookie 和网站存储仅清理默认账号。独立账号请在网站多账号中删除对应空间。"),13f,u.muted));val labels=listOf(tr("历史记录和最近关闭"),tr("默认账号网页缓存"),tr("默认账号所有网站 Cookie（退出登录）"),tr("默认账号网站本地存储"));val boxes=labels.map{CheckBox(a).apply{text=it;setTextColor(u.text)}.also(col::addView)};col.addView(u.button(tr("清理所选数据")){val checks=boxes.map{it.isChecked};if(checks.none{it})return@button;confirm(tr("确认清理"),labels.filterIndexed{i,_->checks[i]}.joinToString("\n")){if(checks[0])a.clearHistoryData();if(checks[1]){val existing=a.tabs.filter{it.accountId.isEmpty()&&!it.incognito}.firstNotNullOfOrNull{it.web};if(existing!=null)existing.clearCache(true)else WebView(a).apply{clearCache(true);destroy()}};if(checks[2])CookieManager.getInstance().removeAllCookies{CookieManager.getInstance().flush()};if(checks[3])WebStorage.getInstance().deleteAllData();a.toast(tr("已清理"))}})}}
    fun reading(){pages.show(tr("朗读本页")){col->val status=u.label(if(a.speech.speaking)tr("正在朗读")else if(a.speech.paused)tr("已暂停")else tr("使用系统语音引擎朗读本页正文"));col.addView(status);a.speech.observe(status){status.text=if(a.speech.speaking)tr("正在朗读")else if(a.speech.paused)tr("已暂停")else tr("已停止")};col.addView(u.button(tr("开始 / 重新朗读"),true){a.speech.readPage()});col.addView(u.button(tr("暂停")){a.speech.pause()});col.addView(u.button(tr("继续")){a.speech.resume()});col.addView(u.button(tr("停止")){a.speech.stop()});col.addView(u.label(tr("离开应用时暂停；长页面最多读取前 8 万字符。"),12f,u.muted));col.addView(u.item(tr("系统语音设置")){runCatching{a.startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.onFailure{a.toast(tr("系统未提供语音设置入口"))}})}}
    fun downloads()=DownloadPanels(this).show()
}
