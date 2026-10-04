package dev.qinglan.browser

import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.*
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

class BrowserPanels(private val a:BrowserActivity) {
    private val u get()=a.ui
    private val store get()=a.store
    private val prefs get()=a.prefs
    fun dialog(view:View,bottom:Boolean=true):Dialog {
        val scroll=ScrollView(a).apply{addView(view)}
        val d=Dialog(a);d.setContentView(scroll);d.window?.apply{setBackgroundDrawable(u.round(u.panel,24));addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(.30f);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
        d.show();d.window?.apply{setGravity(if(bottom)Gravity.BOTTOM else Gravity.CENTER);setLayout(a.resources.displayMetrics.widthPixels-u.dp(20),WindowManager.LayoutParams.WRAP_CONTENT)}
        scroll.post{val max=a.resources.displayMetrics.heightPixels*82/100;if(scroll.height>max)d.window?.setLayout(a.resources.displayMetrics.widthPixels-u.dp(20),max)}
        return d
    }
    private fun page(title:String)=u.column(20).apply{addView(u.title(title))}
    /** A separate full-window management page, with its own back bar and scrolling body. */
    private fun pageDialog(col:LinearLayout):Dialog {
        a.dismissTabs()
        val title=(col.getChildAt(0)as TextView).text.toString();col.removeViewAt(0)
        val d=Dialog(a,if(u.dark)R.style.AppThemeDark else R.style.AppTheme)
        val root=u.column().apply{setBackgroundColor(u.bg)};val bar=u.row().apply{setBackgroundColor(u.panel)}
        bar.addView(u.icon("back","返回网页"){d.dismiss()});bar.addView(u.title(title));root.addView(bar,LinearLayout.LayoutParams(-1,u.dp(56)))
        root.addView(ScrollView(a).apply{addView(col)},LinearLayout.LayoutParams(-1,0,1f))
        d.setContentView(root);d.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
        d.show();d.window?.apply{
            setLayout(-1,-1);statusBarColor=u.panel;navigationBarColor=u.bg
            if(Build.VERSION.SDK_INT>=30){setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener{v,insets->val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets};insetsController?.setSystemBarsAppearance(if(u.dark)0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);root.requestApplyInsets()}
            else{root.fitsSystemWindows=true;decorView.systemUiVisibility=if(u.dark)0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR}
        };return d
    }
    private fun info(title:String,message:String)=AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show()
    fun menu(){
        a.dismissTabs()
        val col=page("浏览工具");lateinit var d:Dialog
        val grid=GridLayout(a).apply{columnCount=3}
        fun entry(icon:String,title:String,run:()->Unit){val box=u.column(5).apply{gravity=Gravity.CENTER;minimumHeight=u.dp(82);isFocusable=true;contentDescription=title;setOnClickListener{d.dismiss();run()}}
            box.addView(u.icon(icon,title){d.dismiss();run()});box.addView(u.label(title,12f).apply{gravity=Gravity.CENTER})
            grid.addView(box,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0})}
        entry("bookmark","书签"){library(false)};entry("history","历史"){library(true)};entry("download","下载"){downloads()}
        entry("cookie","Cookie 管理"){cookies()};entry("desktop",if(store.siteBool(a.currentUrl,"desktop",false))"电脑模式 · 开"else"电脑模式"){toggleSite("desktop",false)};entry("moon",if(u.dark)"日间模式"else"夜间模式"){prefs.edit().putString("theme",if(u.dark)"light"else"dark").apply();a.retheme()}
        entry("image",if(prefs.getBoolean("noImages",false))"无图模式 · 开"else"无图模式"){prefs.edit().putBoolean("noImages",!prefs.getBoolean("noImages",false)).apply();a.tabs.forEach{t->t.web?.let{a.configure(it,t.url)}};a.reload();a.toast("无图模式已切换")}
        entry("refresh","刷新"){a.reload()};entry("search","页面查找"){a.showFind()}
        entry("fullscreen","全屏"){a.setFullscreen(true)};entry("plus","添加到主页"){addCurrentHome()};entry("bookmark","收藏当前页"){bookmarkCurrent()}
        entry("site","网站设置"){site()};entry("settings","设置"){settings()};entry("globe","分享链接"){if(a.isHttp(a.currentUrl))a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,a.currentUrl),"分享链接"))}
        col.addView(grid);col.addView(u.label("清岚 ${BuildConfig.VERSION_NAME} · 简单，自在",12f,u.muted).apply{gravity=Gravity.CENTER});d=dialog(col)
    }
    private fun toggleSite(key:String,default:Boolean){if(!a.isHttp(a.currentUrl)){a.toast("请先打开网站");return};store.setSiteBool(a.currentUrl,key,!store.siteBool(a.currentUrl,key,default));a.reload()}
    fun tabs(){if(a.dismissTabs())return;renderTabs()}
    private fun renderTabs(){val col=u.column(4)
        a.tabs.toList().forEachIndexed{i,t->val r=u.row().apply{setBackgroundColor(if(i==a.selected)u.soft else u.panel)}
            val title=u.label((if(i==a.selected)"● "else"")+t.title,14f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER_VERTICAL;setPadding(u.dp(12),0,u.dp(4),0);setOnClickListener{a.dismissTabs();a.switchTab(a.tabs.indexOf(t))};isFocusable=true}
            r.addView(title,LinearLayout.LayoutParams(0,u.dp(48),1f));r.addView(u.icon("close","关闭 ${t.title.take(60)}"){a.dismissTabs();a.closeTab(t.id);renderTabs()});col.addView(r,LinearLayout.LayoutParams(-1,u.dp(48)));col.addView(u.rule())}
        col.addView(u.button("＋ 新建标签页"){a.dismissTabs();a.newHome()},LinearLayout.LayoutParams(-1,u.dp(48)))
        a.showTabs(col)
    }
    fun site(){val url=a.currentUrl;val col=page("快捷设置");lateinit var d:Dialog
        val engine=a.current?.searchOverride?:prefs.getString("search",SearchEngines.default)!!
        col.addView(u.item("搜索引擎 · ${SearchEngines.name(engine)}",if(a.current?.searchOverride!=null)"仅当前标签页"else"使用默认搜索引擎"){d.dismiss();searchEngine()})
        if(!a.isHttp(url)){d=dialog(col);return}
        col.addView(u.label(Uri.parse(url).host.orEmpty(),13f,u.muted))
        col.addView(u.button("Cookie 管理",true){d.dismiss();cookies()})
        fun toggle(title:String,key:String,default:Boolean){val s=Switch(a).apply{text=title;setTextColor(u.text);setPadding(u.dp(4),u.dp(9),u.dp(4),u.dp(9));minimumHeight=u.dp(52);isChecked=store.siteBool(url,key,default);setOnCheckedChangeListener{_,checked->store.setSiteBool(url,key,checked);a.current?.web?.let{a.configure(it,url)}}};col.addView(s)}
        toggle("电脑模式","desktop",false);toggle("JavaScript","js",true);toggle("允许第三方 Cookie","thirdParty",prefs.getBoolean("thirdParty",false));toggle("网页深色（夜间时）","dark",true);toggle("无图模式","noImages",prefs.getBoolean("noImages",false))
        col.addView(u.label("设置仅对此域名生效。登录异常时可检查 JavaScript 与第三方 Cookie。",12f,u.muted))
        col.addView(u.button("应用并刷新",true){d.dismiss();a.reload()});d=dialog(col)
    }
    private fun bookmarkCurrent(){if(!a.isHttp(a.currentUrl)){a.toast("请先打开网页");return};val title=u.edit("书签名称",a.current?.title.orEmpty());AlertDialog.Builder(a).setTitle("添加书签").setView(title).setNegativeButton("取消",null).setPositiveButton("保存"){_,_->store.bookmarks.removeAll{it.url==a.currentUrl};store.bookmarks.add(0,Visit(title.text.toString().ifBlank{a.currentUrl},a.currentUrl));store.save();a.toast("已收藏")}.show()}
    private fun addCurrentHome(){if(!a.isHttp(a.currentUrl)){a.addHomeChoice();return};val folders=store.home.filter{it.folder};AlertDialog.Builder(a).setTitle("添加到").setItems((listOf("主页")+folders.map{it.title}).toTypedArray()){_,i->a.editHome(null,if(i==0)""else folders[i-1].id,false,a.current?.title.orEmpty(),a.currentUrl)}.show()}
    fun library(history:Boolean){val col=page(if(history)"历史"else"书签");lateinit var d:Dialog;val search=u.edit("搜索名称或网址");col.addView(search);val list=u.column();col.addView(list)
        fun render(q:String=""){list.removeAllViews();val data=(if(history)store.history else store.bookmarks).filter{it.title.contains(q,true)||it.url.contains(q,true)}
            if(data.isEmpty())list.addView(u.label("暂无内容",14f,u.muted))
            data.take(200).forEach{v->val item=u.item(v.title,if(history)DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(v.time))+" · "+v.url else v.url){d.dismiss();a.open(v.url)}
                item.setOnLongClickListener{AlertDialog.Builder(a).setTitle(v.title).setItems(if(history)arrayOf("删除记录","添加到主页")else arrayOf("删除书签","添加到主页","编辑书签")){_,i->when(i){0->{(if(history)store.history else store.bookmarks).remove(v);store.save();render(search.text.toString())};1->{d.dismiss();a.editHome(null,"",false,v.title,v.url)};2->{val wrap=u.column(20);val name=u.edit("名称",v.title);val url=u.edit("网址",v.url);wrap.addView(name);wrap.addView(url);val edit=AlertDialog.Builder(a).setTitle("编辑书签").setView(wrap).setNegativeButton("取消",null).setPositiveButton("保存",null).create();edit.setOnShowListener{edit.getButton(-1).setOnClickListener{if(!a.isHttp(url.text.toString())){url.error="网址无效"}else{val index=store.bookmarks.indexOf(v);if(index>=0)store.bookmarks[index]=Visit(name.text.toString(),url.text.toString());store.save();edit.dismiss();render()}}};edit.show()}}}.show();true};list.addView(item)}
            if(data.size>200)list.addView(u.label("显示前 200 项，请搜索缩小范围",12f,u.muted))
        }
        if(!history){val actions=u.row();actions.addView(u.button("导入 HTML"){d.dismiss();importBookmarks()},LinearLayout.LayoutParams(0,-2,1f));actions.addView(u.button("导出 HTML"){a.writeDocument("qinglan-bookmarks.html","text/html",store.bookmarkHtml())},LinearLayout.LayoutParams(0,-2,1f));col.addView(actions)}
        search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render(s.toString())};override fun afterTextChanged(s:android.text.Editable?){} });render();d=pageDialog(col)
    }
    private fun importBookmarks(){a.readDocument{html->
        val links=Regex("<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>",RegexOption.IGNORE_CASE).findAll(html).map{m->Visit(android.text.Html.fromHtml(m.groupValues[2],0).toString(),android.text.Html.fromHtml(m.groupValues[1],0).toString())}.filter{a.isHttp(it.url)}.toList()
        AlertDialog.Builder(a).setTitle("导入书签").setMessage("检测到 ${links.size} 个网站，将合并到书签并跳过重复网址。文件夹结构暂转为平铺列表。").setNegativeButton("取消",null).setPositiveButton("导入"){_,_->val urls=store.bookmarks.map{it.url}.toMutableSet();var count=0;links.forEach{if(urls.add(it.url)){store.bookmarks.add(it);count++}};store.save();a.toast("已导入 $count 项");library(false)}.show()
    }}
    fun settings(){val col=page("设置");lateinit var d:Dialog
        fun choice(title:String,options:Array<String>,current:Int,onSelect:(Int)->Unit){col.addView(u.item(title,options[current]){AlertDialog.Builder(a).setTitle(title).setSingleChoiceItems(options,current){pick,i->pick.dismiss();d.dismiss();onSelect(i)}.setNegativeButton("取消",null).show()})}
        fun toggle(title:String,key:String,default:Boolean){col.addView(Switch(a).apply{text=title;setTextColor(u.text);minimumHeight=u.dp(52);setPadding(u.dp(4),u.dp(8),u.dp(4),u.dp(8));isChecked=prefs.getBoolean(key,default);setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean(key,checked).apply();if(key=="autoHideAddress"&&!checked)a.showAddress()}})}
        choice("界面主题",arrayOf("跟随系统","浅色","深色"),listOf("system","light","dark").indexOf(prefs.getString("theme","system")).coerceAtLeast(0)){prefs.edit().putString("theme",listOf("system","light","dark")[it]).apply();a.retheme()}
        choice("地址栏位置",arrayOf("顶部（默认）","底部"),if(prefs.getBoolean("bottomAddress",false))1 else 0){prefs.edit().putBoolean("bottomAddress",it==1).apply();a.retheme()}
        toggle("向下浏览时隐藏地址栏","autoHideAddress",true)
        val sizes=listOf(80,90,100,110,125,150,175,200)
        choice("网页字号",sizes.map{"$it%"}.toTypedArray(),sizes.indexOf(prefs.getInt("textZoom",100)).coerceAtLeast(0)){prefs.edit().putInt("textZoom",sizes[it]).apply();a.tabs.forEach{t->t.web?.let{w->a.configure(w,t.url)}}}
        col.addView(u.item("搜索引擎",SearchEngines.name(prefs.getString("search",SearchEngines.default)!!)){d.dismiss();searchEngine(true)})
        col.addView(u.item("备份与恢复","可选书签、主页和设置，不含 Cookie 与历史"){d.dismiss();backup()})
        toggle("恢复上次标签页","restore",true);toggle("默认允许第三方 Cookie","thirdParty",false)
        col.addView(u.item("清理浏览数据","历史、缓存、Cookie 可分别选择"){d.dismiss();clearData()})
        col.addView(u.item("设置为默认浏览器"){try{a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}catch(e:Exception){a.toast("请在系统设置中选择默认浏览器")}})
        col.addView(u.item("关于清岚","${BuildConfig.VERSION_NAME} · 原生 WebView"){val provider=WebView.getCurrentWebViewPackage();info("关于清岚","版本：${BuildConfig.VERSION_NAME}\n系统：Android ${Build.VERSION.RELEASE}\nWebView：${provider?.versionName?:"未知"}\n完整 Cookie 属性：${if(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO))"支持"else"不支持"}\n\n二维码识别使用 ZXing（Apache License 2.0）。")})
        col.addView(u.item("开源许可","ZXing · Apache License 2.0"){val license=page("ZXing 开源许可");license.addView(u.label(a.assets.open("zxing-LICENSE.txt").bufferedReader().use{it.readText()},12f));pageDialog(license)})
        d=pageDialog(col)
    }
    fun searchEngine(permanent:Boolean=false){
        val col=page("搜索引擎");val names=SearchEngines.presets.keys.toList()+"自定义"
        val active=a.current?.searchOverride?:prefs.getString("search",SearchEngines.default)!!
        val spinner=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,names);setSelection(names.indexOf(SearchEngines.name(active)))};col.addView(spinner)
        val input=u.edit("搜索网址，%s 表示关键词",active);col.addView(input)
        spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){if(position<SearchEngines.presets.size){input.setText(SearchEngines.presets.values.elementAt(position));input.visibility=View.GONE}else input.visibility=View.VISIBLE}
            override fun onNothingSelected(parent:AdapterView<*>?){}
        }
        val scope=RadioGroup(a);val temporary=RadioButton(a).apply{id=View.generateViewId();text="仅当前标签页（关闭后失效）";setTextColor(u.text)};val default=RadioButton(a).apply{id=View.generateViewId();text="设为默认搜索引擎";setTextColor(u.text)}
        scope.addView(temporary);scope.addView(default);scope.check(if(permanent)default.id else temporary.id);col.addView(scope)
        lateinit var d:Dialog
        col.addView(u.button("应用",true){val template=input.text.toString().trim();if(!SearchEngines.valid(template)){input.error="请输入包含 %s 的 HTTP/HTTPS 搜索网址";return@button}
            if(scope.checkedRadioButtonId==default.id){prefs.edit().putString("search",template).apply();a.current?.searchOverride=null}else a.current?.searchOverride=template
            d.dismiss();a.toast("搜索引擎已更新")})
        if(a.current?.searchOverride!=null)col.addView(u.button("当前标签页恢复默认"){a.current?.searchOverride=null;d.dismiss();a.toast("已恢复默认搜索引擎")})
        d=dialog(col)
    }
    fun backup(){
        val col=page("备份与恢复");col.addView(u.label("备份仅包含勾选的项目。不包含 Cookie、历史、下载记录、标签页或网站存储。",14f,u.muted))
        val boxes=listOf("书签","主页网站和文件夹","浏览器与网站设置").map{label->CheckBox(a).apply{text=label;isChecked=true;setTextColor(u.text);minimumHeight=u.dp(48)}.also{col.addView(it)}}
        col.addView(u.button("导出选中项目",true){
            if(boxes.none{it.isChecked}){a.toast("请至少选择一项");return@button}
            runCatching{BackupCodec.export(if(boxes[1].isChecked)store.home else null,if(boxes[0].isChecked)store.bookmarks else null,if(boxes[2].isChecked)prefs.all else null)}.onSuccess{a.writeDocument("qinglan-backup.json","application/json",it)}.onFailure{info("导出失败",it.message.orEmpty())}
        })
        lateinit var d:Dialog
        col.addView(u.button("选择备份文件恢复"){
            a.readDocument{raw->
                val snapshot=runCatching{BackupCodec.parse(raw)}.getOrElse{info("无法恢复",it.message?:"文件无效");return@readDocument}
                val keys=mutableListOf<String>();val labels=mutableListOf<String>()
                snapshot.bookmarks?.let{keys.add("bookmarks");labels.add("书签（${it.size} 项）")};snapshot.home?.let{keys.add("home");labels.add("主页（${it.size} 项）")};snapshot.settings?.let{keys.add("settings");labels.add("设置（${it.size} 项）")}
                val selected=BooleanArray(keys.size){true}
                AlertDialog.Builder(a).setTitle("恢复备份 · 替换勾选项目").setMultiChoiceItems(labels.toTypedArray(),selected){_,i,b->selected[i]=b}.setNegativeButton("取消",null).setPositiveButton("恢复") {_,_->
                    val chosen=keys.filterIndexed{i,_->selected[i]}.toSet();if(chosen.isEmpty()){a.toast("未选择恢复项目");return@setPositiveButton}
                    val oldHome=store.home.toList();val oldBookmarks=store.bookmarks.toList()
                    try {
                        if("home" in chosen){store.home.clear();store.home.addAll(snapshot.home!!)}
                        if("bookmarks" in chosen){store.bookmarks.clear();store.bookmarks.addAll(snapshot.bookmarks!!)}
                        store.save()
                        if("settings" in chosen){val edit=prefs.edit();prefs.all.keys.filter(BackupCodec::allowed).forEach{edit.remove(it)};snapshot.settings!!.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v)}};edit.apply()}
                        d.dismiss();a.retheme();a.toast("已恢复所选项目")
                    }catch(e:Exception){store.home.clear();store.home.addAll(oldHome);store.bookmarks.clear();store.bookmarks.addAll(oldBookmarks);info("恢复失败",e.message.orEmpty())}
                }.show()
            }
        });d=pageDialog(col)
    }
    private fun clearData(){val checks=booleanArrayOf(false,false,false,false);AlertDialog.Builder(a).setTitle("选择要清理的数据").setMultiChoiceItems(arrayOf("历史记录","网页缓存","所有网站 Cookie（会退出登录）","网站本地存储"),checks){_,i,c->checks[i]=c}.setNegativeButton("取消",null).setPositiveButton("清理"){_,_->
        if(checks[0]){store.history.clear();store.save()};if(checks[1])a.tabs.forEach{it.web?.clearCache(true)};if(checks[2])CookieManager.getInstance().removeAllCookies{CookieManager.getInstance().flush();a.toast("Cookie 已清理")};if(checks[3])WebStorage.getInstance().deleteAllData();if(!checks[2])a.toast("已清理所选数据")
    }.show()}
    fun downloads(){val col=page("下载");val manager=a.getSystemService(Context.DOWNLOAD_SERVICE)as DownloadManager;val ids=prefs.getStringSet("downloads",emptySet()).orEmpty().mapNotNull{it.toLongOrNull()}.toLongArray()
        if(ids.isEmpty())col.addView(u.label("暂无下载记录",14f,u.muted))else manager.query(DownloadManager.Query().setFilterById(*ids))?.use{c->while(c.moveToNext()){
            val id=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));val title=c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));val status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));val downloaded=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));val total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val state=when(status){DownloadManager.STATUS_SUCCESSFUL->"已完成";DownloadManager.STATUS_FAILED->"失败";DownloadManager.STATUS_PAUSED->"已暂停";DownloadManager.STATUS_PENDING->"等待中";else->"下载中"}
            col.addView(u.item(title,"$state · ${downloaded.coerceAtLeast(0)/1024} / ${if(total<0)"?"else(total/1024).toString()} KB"){
                if(status==DownloadManager.STATUS_SUCCESSFUL){val uri=manager.getUriForDownloadedFile(id);try{a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,manager.getMimeTypeForDownloadedFile(id)?:"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}catch(e:Exception){a.toast("未找到可以打开此文件的应用")}}
                else if(status==DownloadManager.STATUS_FAILED){val query=manager.query(DownloadManager.Query().setFilterById(id));query?.use{if(it.moveToFirst())a.toast("下载失败，代码 ${it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}")}}
            })
        }}
        lateinit var d:Dialog;col.addView(u.button("刷新列表"){d.dismiss();downloads()});d=pageDialog(col)
    }
    fun cookies(){val url=a.currentUrl;if(!a.isHttp(url)){a.toast("请先打开需要管理 Cookie 的网站");return}
        val col=page("Cookie 管理");col.addView(u.label(Uri.parse(url).host.orEmpty(),14f,u.accent));col.addView(u.label("当前 URL 可访问的 Cookie。其他路径、子域名需分别打开；不会读取其他浏览器的数据。",12f,u.muted));lateinit var d:Dialog
        val manager=CookieManager.getInstance();val supported=WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)
        val entries=mutableListOf<CookieCodec.Entry>();val errors=mutableListOf<String>()
        if(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)){
            runCatching{CookieManagerCompat.getCookieInfo(manager,url)}
                .onSuccess{raw->
                    raw.forEachIndexed{i,s->
                        runCatching{CookieCodec.parseSetCookie(s,url)}
                            .onSuccess{entries.add(it)}
                            .onFailure{errors.add("第 ${i+1} 项：${it.message}")}
                    }
                }
                .onFailure{errors.add("无法读取 Cookie 属性")}
        }
        else errors.add("当前 WebView 不支持完整属性读取。请更新 Android System WebView；仍可导入 JSON。")
        col.addView(u.label("${entries.size} 项",13f,u.muted));if(entries.isEmpty())col.addView(u.label("此页面没有可读取的 Cookie",14f,u.muted))
        entries.forEach{entry->val r=u.item(entry.name,"${entry.domain}${entry.path} · ${if(entry.httpOnly)"HttpOnly · "else""}${if(entry.expiry==null)"会话"else"持久"}"){d.dismiss();editCookie(entry,url)};col.addView(r)}
        if(errors.isNotEmpty())col.addView(u.label(errors.joinToString("\n"),12f,u.muted))
        val actions=u.row();actions.addView(u.button("导入",true){d.dismiss();importCookies(url)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(u.button("导出 JSON"){if(!supported){a.toast("需要支持完整属性的 WebView");return@button};AlertDialog.Builder(a).setTitle("导出 Cookie").setMessage("导出 ${entries.size} 项可读取的 Cookie${if(errors.isNotEmpty())"；${errors.size} 项未导出"else""}。文件包含登录凭据，请保存在可信位置。").setNegativeButton("取消",null).setPositiveButton("保存文件"){_,_->a.writeDocument("cookies-${Uri.parse(url).host}.json","application/json",CookieCodec.export(entries))}.show()},LinearLayout.LayoutParams(0,-2,1f));col.addView(actions)
        col.addView(u.button("新增 Cookie"){d.dismiss();editCookie(null,url)});d=dialog(col)
    }
    private fun importCookies(url:String){val col=page("导入 Cookie");col.addView(u.label("目标：${Uri.parse(url).host}\n支持 Cookie-Editor JSON；默认合并，相同名称、域名与路径会更新。",12f,u.muted));val input=u.edit("在这里粘贴 JSON 数组",multiline=true);input.maxLines=8;col.addView(input);lateinit var d:Dialog
        col.addView(u.button("检查并预览",true){previewImport(input.text.toString(),url){d.dismiss()}})
        col.addView(u.button("选择 JSON 文件"){a.readDocument{raw->previewImport(raw,url){d.dismiss()}}});d=dialog(col)
    }
    private fun previewImport(text:String,url:String,onAccept:()->Unit){
        val parsed=try{CookieCodec.parseImport(text,url)}catch(e:Exception){info("无法导入",e.message?:"不是有效的 JSON 数组");return}
        val summary="可导入 ${parsed.entries.size} 项，提示 ${parsed.warnings.size} 项。\n\n"+parsed.entries.groupingBy{it.domain}.eachCount().entries.joinToString("\n"){"${it.key}：${it.value} 项"}+if(parsed.warnings.isEmpty())""else"\n\n"+parsed.warnings.take(12).joinToString("\n")
        AlertDialog.Builder(a).setTitle("导入预览").setMessage(summary).setNegativeButton("取消",null).apply{if(parsed.entries.isNotEmpty())setPositiveButton("导入有效条目"){_,_->onAccept();writeCookies(parsed.entries,url)}}.show()
    }
    private fun writeCookies(entries:List<CookieCodec.Entry>,url:String){val manager=CookieManager.getInstance();var remaining=entries.size;var success=0
        entries.forEach{e->manager.setCookie(e.url(),e.header()){ok->if(ok)success++;remaining--;if(remaining==0){manager.flush();AlertDialog.Builder(a).setTitle("导入完成").setMessage("写入成功 $success 项，浏览器拒绝 ${entries.size-success} 项。\n\n写入成功不代表网站一定恢复登录；网站仍可能要求验证。").setNegativeButton("关闭",null).setPositiveButton("打开并刷新目标网站"){_,_->a.open(url)}.show()}}}
    }
    private fun editCookie(entry:CookieCodec.Entry?,url:String){val col=page(if(entry==null)"新增 Cookie"else"编辑 Cookie");val name=u.edit("名称",entry?.name.orEmpty());val value=u.edit("值",entry?.value.orEmpty(),true);value.minLines=2
        val domain=u.edit("域名",entry?.domain?:Uri.parse(url).host.orEmpty());val path=u.edit("路径",entry?.path?:"/")
        val secure=CheckBox(a).apply{text="Secure";isChecked=entry?.secure?:true;setTextColor(u.text)};val httpOnly=CheckBox(a).apply{text="HttpOnly";isChecked=entry?.httpOnly?:false;setTextColor(u.text)}
        listOf(name,value,domain,path,secure,httpOnly).forEach{col.addView(it)}
        if(entry!=null){name.isEnabled=false;domain.isEnabled=false;path.isEnabled=false;col.addView(u.label("保留原 Cookie 的 SameSite、有效期及 hostOnly 属性。",12f,u.muted))}
        lateinit var d:Dialog;col.addView(u.button("保存",true){
            val e=CookieCodec.Entry(name.text.toString(),value.text.toString(),domain.text.toString(),path.text.toString(),secure.isChecked,httpOnly.isChecked,entry?.hostOnly?:!domain.text.startsWith('.'),entry?.sameSite?:"unspecified",entry?.expiry)
            val parsed=runCatching{CookieCodec.parseImport(CookieCodec.export(listOf(e)),url)}.getOrElse{info("无法保存",it.message.orEmpty());return@button}
            if(parsed.entries.size!=1){info("无法保存",parsed.warnings.joinToString("\n"));return@button}
            CookieManager.getInstance().setCookie(e.url(),e.header()){ok->CookieManager.getInstance().flush();a.toast(if(ok)"已保存"else"浏览器拒绝此 Cookie");if(ok){d.dismiss();cookies()}}
        })
        if(entry!=null)col.addView(u.button("删除此 Cookie"){AlertDialog.Builder(a).setTitle("删除 ${entry.name}？").setMessage("可能使当前网站退出登录。").setNegativeButton("取消",null).setPositiveButton("删除"){_,_->CookieManager.getInstance().setCookie(entry.url(),entry.header(true)){ok->CookieManager.getInstance().flush();a.toast(if(ok)"已删除"else"删除失败");d.dismiss();cookies()}}.show()});d=dialog(col)
    }
}
