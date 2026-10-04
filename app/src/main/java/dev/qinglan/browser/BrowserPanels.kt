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
    fun dialog(view:View,bottom:Boolean=true):Dialog {val scroll=ScrollView(a).apply{addView(view)};val d=Dialog(a);d.setContentView(scroll);d.window?.setBackgroundDrawable(u.round(u.panel,24));d.show();d.window?.apply{setGravity(if(bottom)Gravity.BOTTOM else Gravity.CENTER);setLayout(a.resources.displayMetrics.widthPixels-u.dp(20),-2)};return d}
    fun info(title:String,message:String){pages.show(title){it.addView(u.label(message))}}
    fun confirm(title:String,message:String,run:()->Unit){pages.show(title){col->col.addView(u.label(message));col.addView(u.button("确认",true){pages.back();run()});col.addView(u.button("取消"){pages.back()})}}
    fun choose(title:String,options:List<String>,selected:Int=-1,run:(Int)->Unit){pages.show(title){col->options.forEachIndexed{i,s->col.addView(u.item((if(i==selected)"✓ "else"")+s){pages.back();run(i)})}}}
    fun cookies()=cookie.show()
    fun menu(){if(a.overlayKind=="menu"){a.dismissTabs();return};a.dismissTabs();val col=u.column(8);val columns=prefs.getInt("menuColumns",3).coerceIn(3,6);val grid=GridLayout(a).apply{columnCount=columns}
        fun entry(icon:String,title:String,run:()->Unit){val box=u.column(2).apply{gravity=Gravity.CENTER;isFocusable=true;contentDescription=title;setOnClickListener{a.dismissTabs();run()}}
            box.addView(u.icon(icon,title){a.dismissTabs();run()});box.addView(u.label(title,11f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER;setPadding(0,0,0,0)})
            grid.addView(box,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=u.dp(76)})}
        entry("bookmark","书签"){library.bookmarks()};entry("history","历史"){library.history()};entry("download","下载"){downloads()}
        entry("cookie","Cookie 管理"){cookies()};entry("desktop",if(store.siteBool(a.currentUrl,"desktop",false))"电脑模式 · 开"else"电脑模式"){if(a.isHttp(a.currentUrl)){store.setSiteBool(a.currentUrl,"desktop",!store.siteBool(a.currentUrl,"desktop",false));a.reload()}else a.toast("请先打开网页")}
        entry("moon",if(u.dark)"日间模式"else"夜间模式"){prefs.edit().putString("theme",if(u.dark)"light"else"dark").apply();a.retheme()}
        entry("image",if(prefs.getBoolean("noImages",false))"无图模式 · 开"else"无图模式"){prefs.edit().putBoolean("noImages",!prefs.getBoolean("noImages",false)).apply();a.reload()}
        entry("refresh","刷新"){a.reload()};entry("search","页面查找"){a.showFind()};entry("fullscreen","全屏"){a.setFullscreen(true)}
        entry("bookmark","收藏网页"){if(a.isHttp(a.currentUrl))library.collect(a.current?.title.orEmpty(),a.currentUrl)else a.addHomeChoice()}
        entry("site","网站设置"){site()};entry("settings","设置"){settings()};entry("globe","分享链接"){if(a.isHttp(a.currentUrl))a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,a.currentUrl),"分享链接"))}
        entry("shield","广告过滤"){filter.show()};entry("media","网页资源"){resources.show()}
        entry("speaker",if(a.speech.speaking)"朗读控制"else"朗读本页"){reading()}
        repeat((columns-grid.childCount%columns)%columns){grid.addView(View(a),GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=1})};col.addView(grid);a.showTabs(col,"menu")
    }
    fun tabs(){if(a.dismissTabs())return;renderTabs()}
    private fun renderTabs(){val col=u.column(4)
        a.tabs.toList().forEachIndexed{i,t->val row=u.row().apply{setBackgroundColor(if(i==a.selected)u.soft else u.panel)};row.addView(a.icons.view(u,t.title,t.url));row.addView(u.label(t.title,14f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER_VERTICAL;setPadding(u.dp(8),0,0,0);setOnClickListener{a.switchTab(a.tabs.indexOf(t))}},LinearLayout.LayoutParams(0,u.dp(48),1f));row.addView(u.icon("close","关闭 ${t.title.take(50)}"){a.closeTab(t.id);renderTabs()});col.addView(row,LinearLayout.LayoutParams(-1,u.dp(48)));col.addView(u.rule())}
        col.addView(u.button("＋ 新建标签页"){a.newHome()}.apply{background=u.round(u.panel,0);stateListAnimator=null},LinearLayout.LayoutParams(-1,u.dp(48)));a.showTabs(col)
    }
    fun site(){val url=a.currentUrl;pages.show("快捷设置"){col->val engine=a.current?.searchOverride?:prefs.getString("search",SearchEngines.default)!!;col.addView(u.item("搜索引擎 · ${searches.name(engine)}",if(a.current?.searchOverride!=null)"仅当前标签页"else"使用默认搜索引擎"){searchEngine()})
        if(a.isHttp(url)){col.addView(u.label(Uri.parse(url).host.orEmpty(),13f,u.muted));col.addView(u.item("Cookie 管理"){cookies()});col.addView(u.item("本网站密码","保存或手动填入登录信息"){vault.passwords()})
            fun toggle(title:String,key:String,default:Boolean){col.addView(Switch(a).apply{text=title;setTextColor(u.text);minimumHeight=u.dp(52);isChecked=store.siteBool(url,key,default);setOnCheckedChangeListener{_,b->store.setSiteBool(url,key,b);a.current?.web?.let{a.configure(it,url)}}})};toggle("广告过滤","adblock",true);toggle("电脑模式","desktop",false);toggle("JavaScript","js",true);toggle("第三方 Cookie","thirdParty",prefs.getBoolean("thirdParty",false));toggle("网页深色","dark",true);toggle("无图模式","noImages",prefs.getBoolean("noImages",false));col.addView(u.button("应用并刷新",true){pages.close();a.reload()})}
    }}
    fun settings(){pages.show("设置"){col->col.addView(u.item("外观与主题","主题、配色、字号、菜单列数"){appearance()});col.addView(u.item("操作习惯","地址栏、标签恢复、搜索引擎"){behavior()});col.addView(u.item("高级功能","密码、备份、网站数据、朗读"){advanced()});col.addView(u.item("关于项目","版本、系统内核、开源许可"){about()})}}
    private fun choice(col:LinearLayout,title:String,options:List<String>,current:Int,run:(Int)->Unit){col.addView(u.item(title,options.getOrElse(current){""}){choose(title,options,current){run(it);pages.refresh()}})}
    private fun toggle(col:LinearLayout,title:String,key:String,default:Boolean){col.addView(Switch(a).apply{text=title;setTextColor(u.text);minimumHeight=u.dp(52);isChecked=prefs.getBoolean(key,default);setOnCheckedChangeListener{_,b->prefs.edit().putBoolean(key,b).apply();if(key=="autoHideAddress"&&!b)a.showAddress()}})}
    private fun appearance(){pages.show("外观与主题"){col->val themes=listOf("system","light","dark");choice(col,"界面主题",listOf("跟随系统","浅色","深色"),themes.indexOf(prefs.getString("theme","system"))){prefs.edit().putString("theme",themes[it]).apply();a.retheme()};col.addView(u.item("配色方案",Palette.names[prefs.getString("palette","green")].orEmpty()){appearancePanels.palette()});choice(col,"工具菜单每行数量",(3..6).map{"每行 $it 个"},prefs.getInt("menuColumns",3)-3){prefs.edit().putInt("menuColumns",it+3).apply()};val sizes=listOf(80,90,100,110,125,150,175,200);choice(col,"网页字号",sizes.map{"$it%"},sizes.indexOf(prefs.getInt("textZoom",100))){prefs.edit().putInt("textZoom",sizes[it]).apply();a.tabs.forEach{t->t.web?.let{w->a.configure(w,t.url)}}}}}
    private fun behavior(){pages.show("操作习惯"){col->choice(col,"地址栏位置",listOf("顶部","底部"),if(prefs.getBoolean("bottomAddress",false))1 else 0){prefs.edit().putBoolean("bottomAddress",it==1).apply();a.retheme()};toggle(col,"向下浏览隐藏地址栏","autoHideAddress",true);toggle(col,"恢复上次标签页","restore",true);col.addView(u.item("搜索引擎",searches.name(prefs.getString("search",SearchEngines.default)!!)){searchEngine(true)});col.addView(u.label("长按底栏标签按钮可直接新建标签页。",13f,u.muted));col.addView(u.item("设置为默认浏览器"){runCatching{a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}.onFailure{a.toast("请到系统设置中选择默认浏览器")}})}}
    private fun advanced(){pages.show("高级功能"){col->col.addView(u.item("广告过滤","规则订阅、站点开关、手动屏蔽"){filter.show()});col.addView(u.item("密码管理","本机加密保存，手动保存与填入"){vault.passwords()});col.addView(u.item("备份与恢复","书签、主页、设置；密码需加密导出"){vault.backup()});toggle(col,"默认允许第三方 Cookie","thirdParty",false);col.addView(u.item("清理浏览数据"){clearData()});col.addView(u.item("用户脚本","导入 .user.js，管理启用与匹配网址"){scripts.show()});col.addView(u.item("朗读控制"){reading()})}}
    private fun about(){pages.show("关于项目"){col->col.addView(u.label("清岚 ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE}\nWebView ${WebView.getCurrentWebViewPackage()?.versionName?:"未知"}\n\n使用系统 WebView，数据保存在本机。"));col.addView(u.item("过滤规则与公共后缀表许可"){filter.information()});col.addView(u.item("MPL 2.0 许可证"){info("MPL 2.0",a.assets.open("MPL-2.0.txt").bufferedReader().use{it.readText()})});col.addView(u.item("开源许可","ZXing · Apache License 2.0"){info("ZXing 开源许可",a.assets.open("zxing-LICENSE.txt").bufferedReader().use{it.readText()})})}}
    fun searchEngine(permanent:Boolean=false)=searches.show(permanent)
    private fun clearData(){pages.show("清理浏览数据"){col->val labels=listOf("历史记录","网页缓存","所有网站 Cookie（退出登录）","网站本地存储");val boxes=labels.map{CheckBox(a).apply{text=it;setTextColor(u.text)}.also(col::addView)};col.addView(u.button("清理所选数据"){val checks=boxes.map{it.isChecked};if(checks.none{it})return@button;confirm("确认清理",labels.filterIndexed{i,_->checks[i]}.joinToString("\n")){if(checks[0]){store.history.clear();store.save()};if(checks[1])a.tabs.forEach{it.web?.clearCache(true)};if(checks[2])CookieManager.getInstance().removeAllCookies{CookieManager.getInstance().flush()};if(checks[3])WebStorage.getInstance().deleteAllData();a.toast("已清理")}})}}
    fun reading(){pages.show("朗读本页"){col->val status=u.label(if(a.speech.speaking)"正在朗读"else if(a.speech.paused)"已暂停"else"使用系统语音引擎朗读本页正文");col.addView(status);a.speech.onChange={status.text=if(a.speech.speaking)"正在朗读"else if(a.speech.paused)"已暂停"else"已停止"};col.addView(u.button("开始 / 重新朗读",true){a.speech.readPage()});col.addView(u.button("暂停"){a.speech.pause()});col.addView(u.button("继续"){a.speech.resume()});col.addView(u.button("停止"){a.speech.stop()});col.addView(u.label("离开应用时暂停；长页面最多读取前 8 万字符。",12f,u.muted));col.addView(u.item("系统语音设置"){runCatching{a.startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.onFailure{a.toast("系统未提供语音设置入口")}})}}
    fun downloads(){pages.show("下载"){col->val manager=a.getSystemService(Context.DOWNLOAD_SERVICE)as DownloadManager;val ids=prefs.getStringSet("downloads",emptySet()).orEmpty().mapNotNull{it.toLongOrNull()}.toLongArray();if(ids.isEmpty())col.addView(u.label("暂无下载记录"))else manager.query(DownloadManager.Query().setFilterById(*ids))?.use{c->while(c.moveToNext()){val id=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));val title=c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));val status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));val state=when(status){DownloadManager.STATUS_SUCCESSFUL->"已完成";DownloadManager.STATUS_FAILED->"失败";DownloadManager.STATUS_PAUSED->"已暂停";else->"下载中"};col.addView(u.item(title,state){if(status==DownloadManager.STATUS_SUCCESSFUL)runCatching{a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(manager.getUriForDownloadedFile(id),manager.getMimeTypeForDownloadedFile(id)?:"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{a.toast("无法打开此文件")}else a.toast(state)})}};col.addView(u.button("刷新列表"){pages.refresh()})}}
}
