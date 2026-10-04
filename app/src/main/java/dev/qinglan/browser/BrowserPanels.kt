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
    val searches=SearchPanels(this)
    val appearancePanels=AppearancePanels(this)
    val scripts=ScriptPanels(this)
    val filter=FilterPanels(this)
    val resources=ResourcePanels(this)
    val reader=ReaderPanels(this)
    val settingsUi=SettingsPanels(this)
    val menuPanels=MenuPanels(this)
    fun dialog(view:View,bottom:Boolean=true):Dialog {val scroll=ScrollView(a).apply{addView(view)};val d=Dialog(a);d.setContentView(scroll);d.window?.setBackgroundDrawable(u.round(u.panel,24));d.show();d.window?.apply{setGravity(if(bottom)Gravity.BOTTOM else Gravity.CENTER);setLayout(a.resources.displayMetrics.widthPixels-u.dp(20),-2)};return d}
    fun info(title:String,message:String){pages.show(title){it.addView(u.label(message))}}
    fun confirm(title:String,message:String,run:()->Unit){pages.show(title){col->col.addView(u.label(message));col.addView(u.button("确认",true){pages.back();run()});col.addView(u.button("取消"){pages.back()})}}
    fun choose(title:String,options:List<String>,selected:Int=-1,run:(Int)->Unit){pages.show(title){col->options.forEachIndexed{i,s->col.addView(u.item((if(i==selected)"✓ "else"")+s){pages.back();run(i)})}}}
    fun cookies()=cookie.show()
    fun menu()=menuPanels.show()
    fun menuEditor()=menuPanels.editor()
    fun tabs(){if(a.dismissTabs())return;renderTabs()}
    private fun renderTabs(){val col=u.column(4)
        val actions=u.row();actions.addView(u.button("搜索标签"){a.dismissTabs();searchTabs()},LinearLayout.LayoutParams(0,-2,1f));if(a.closedTabs.available())actions.addView(u.button("撤销关闭"){a.undoCloseTab()},LinearLayout.LayoutParams(0,-2,1f));col.addView(actions)
        a.tabs.toList().forEachIndexed{i,t->val row=u.row().apply{setBackgroundColor(if(i==a.selected)u.soft else u.panel)};row.addView(a.icons.view(u,t.title,t.url));row.addView(u.label(t.title,14f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER_VERTICAL;setPadding(u.dp(8),0,0,0);setOnClickListener{a.switchTab(a.tabs.indexOf(t))}},LinearLayout.LayoutParams(0,u.dp(48),1f));row.addView(u.icon("close","关闭 ${t.title.take(50)}"){a.closeTab(t.id);renderTabs()});col.addView(row,LinearLayout.LayoutParams(-1,u.dp(48)));col.addView(u.rule())}
        col.addView(u.button("＋ 新建标签页"){a.newHome()}.apply{background=u.round(u.panel,0);stateListAnimator=null},LinearLayout.LayoutParams(-1,u.dp(48)));a.showTabs(col)
    }
    fun searchTabs(){pages.show("搜索标签"){col->
        val input=u.edit("搜索标题或网址");col.addView(input);val results=u.column();col.addView(results)
        fun render(query:String){results.removeAllViews();val found=a.tabs.filter{it.title.contains(query,true)||it.url.contains(query,true)}
            if(found.isEmpty())results.addView(u.label("没有匹配的标签"))
            found.forEach{tab->results.addView(u.item(tab.title,tab.url){pages.close();a.switchTab(a.tabs.indexOf(tab))})}}
        input.onChange(::render);render("")
    }}
    fun site(){val url=a.currentUrl;pages.show("网站设置"){col->
        col.addView(u.item("搜索引擎 · ${searches.name(a.current?.searchOverride?:prefs.getString("search",SearchEngines.default)!!)}",if(a.current?.searchOverride!=null)"仅当前标签页"else"使用默认搜索引擎"){searchEngine()})
        if(a.isHttp(url)){
            col.addView(u.label(Uri.parse(url).host.orEmpty(),18f))
            col.addView(u.label(if(url.startsWith("https://"))"HTTPS 连接；加密连接不代表网站内容可信。"else"HTTP 连接未加密，请勿输入敏感信息。",13f,u.muted))
            col.addView(u.item("Cookie 管理"){cookies()});col.addView(u.item("本网站密码","手动保存与填入"){vault.passwords()})
            fun option(title:String,key:String,default:Boolean){val scope=if(store.siteOverridden(url,key))"此网站单独设置"else"继承默认"
                val enabled=store.siteBool(url,key,default)
                col.addView(u.item(title,"$scope · ${if(enabled)"开启"else"关闭"}"){choose(title,listOf("继承默认（${if(default)"开启"else"关闭"}）","仅此网站开启","仅此网站关闭"),if(!store.siteOverridden(url,key))0 else if(enabled)1 else 2){which->
                    if(which==0)prefs.edit().remove("site.${store.siteKey(url)}.$key").apply()else store.setSiteBool(url,key,which==1)
                    a.current?.web?.let{a.configure(it,url)};pages.refresh()
                }})}
            option("广告过滤","adblock",true);option("请求桌面网站","desktop",prefs.getBoolean("desktop",false))
            option("JavaScript","js",prefs.getBoolean("js",true));option("第三方 Cookie","thirdParty",prefs.getBoolean("thirdParty",false))
            option("网页深色","dark",prefs.getBoolean("webDark",true));option("无图模式","noImages",prefs.getBoolean("noImages",false))
            col.addView(u.item("网页字号","${store.siteZoom(url)}% · ${if(store.siteOverridden(url,"textZoom"))"此网站"else"继承默认"}"){settingsUi.zoom(url)})
            col.addView(u.button("应用并刷新",true){pages.close();a.reload()})
            col.addView(u.button("恢复此网站默认"){store.resetSite(url);a.current?.web?.let{a.configure(it,url)};pages.refresh()})
            col.addView(u.label("设置更改后刷新网页完全生效。恢复默认不删除登录数据。",12f,u.muted))
        }
    }}
    fun settings()=settingsUi.show()
    fun about(){pages.show("关于项目"){col->col.addView(u.item("正文提取开源许可","Mozilla Readability 0.6.0 · Apache 2.0"){info("Mozilla Readability",a.assets.open("reader/NOTICE.txt").bufferedReader().use{it.readText()}+"\n\n"+a.assets.open("reader/LICENSE.md").bufferedReader().use{it.readText()})});col.addView(u.label("清岚 ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE}\nWebView ${WebView.getCurrentWebViewPackage()?.versionName?:"未知"}\n\n使用系统 WebView，数据保存在本机。"));col.addView(u.item("过滤规则与公共后缀表许可"){filter.information()});col.addView(u.item("MPL 2.0 许可证"){info("MPL 2.0",a.assets.open("MPL-2.0.txt").bufferedReader().use{it.readText()})});col.addView(u.item("开源许可","ZXing · Apache License 2.0"){info("ZXing 开源许可",a.assets.open("zxing-LICENSE.txt").bufferedReader().use{it.readText()})})}}
    fun searchEngine(permanent:Boolean=false)=searches.show(permanent)
    fun clearData(){pages.show("清理浏览数据"){col->val labels=listOf("历史记录","网页缓存","所有网站 Cookie（退出登录）","网站本地存储");val boxes=labels.map{CheckBox(a).apply{text=it;setTextColor(u.text)}.also(col::addView)};col.addView(u.button("清理所选数据"){val checks=boxes.map{it.isChecked};if(checks.none{it})return@button;confirm("确认清理",labels.filterIndexed{i,_->checks[i]}.joinToString("\n")){if(checks[0]){store.history.clear();store.save()};if(checks[1])a.tabs.forEach{it.web?.clearCache(true)};if(checks[2])CookieManager.getInstance().removeAllCookies{CookieManager.getInstance().flush()};if(checks[3])WebStorage.getInstance().deleteAllData();a.toast("已清理")}})}}
    fun reading(){pages.show("朗读本页"){col->val status=u.label(if(a.speech.speaking)"正在朗读"else if(a.speech.paused)"已暂停"else"使用系统语音引擎朗读本页正文");col.addView(status);a.speech.onChange={status.text=if(a.speech.speaking)"正在朗读"else if(a.speech.paused)"已暂停"else"已停止"};col.addView(u.button("开始 / 重新朗读",true){a.speech.readPage()});col.addView(u.button("暂停"){a.speech.pause()});col.addView(u.button("继续"){a.speech.resume()});col.addView(u.button("停止"){a.speech.stop()});col.addView(u.label("离开应用时暂停；长页面最多读取前 8 万字符。",12f,u.muted));col.addView(u.item("系统语音设置"){runCatching{a.startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.onFailure{a.toast("系统未提供语音设置入口")}})}}
    fun downloads()=DownloadPanels(this).show()
}
