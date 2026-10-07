@file:Suppress("DEPRECATION")
package dev.qinglan.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class BrowserTab(val id:Long=System.nanoTime(),val incognito:Boolean=false,val accountId:String="",var url:String="about:home",var title:String=tr("主页"),var web:WebView?=null,var saved:Bundle?=null,var used:Long=0,var committedUrl:String="about:home",var searchOverride:String?=null,var openerId:Long?=null,var error:(()->String)?=null,val filterSession:FilterSession=FilterSession(),val resources:ResourceSession=ResourceSession())

class BrowserActivity:Activity() {
    lateinit var accounts:AccountProfiles
    private lateinit var accountBadge:TextView
    lateinit var store:BrowserStore
    lateinit var ui:Ui
    lateinit var panels:BrowserPanels
    lateinit var icons:SiteIcons
    lateinit var speech:PageSpeech
    lateinit var scripts:UserScripts
    lateinit var filtering:AdFiltering
    lateinit var sniffer:ResourceSniffer
    private val suggestions=AddressSuggestions(this)
    internal val webPermissions=WebPermissions(this)
    internal val security=WebSecurity(this)
    internal val exports=PageExports(this)
    internal val blobs=BlobDownloads(this)
    internal val scriptInstaller=ScriptInstaller(this)
    lateinit var root:LinearLayout
    lateinit var content:FrameLayout
    lateinit var address:EditText
    lateinit var top:LinearLayout
    lateinit var bottom:LinearLayout
    lateinit var progress:ProgressBar
    lateinit var tabCount:TextView
    private lateinit var refreshButton:View
    private lateinit var backButton:View
    private lateinit var forwardButton:View
    private var scrollButtons:View?=null
    val tabs=mutableListOf<BrowserTab>()
    val closedTabs=ClosedTabs()
    internal var privateSession:PrivateSession?=null;private set
    val isIncognito get()=privateSession!=null
    private var regularTabs=listOf<BrowserTab>()
    private var regularSelected=0
    fun cookieManager(accountId:String=current?.accountId.orEmpty())=privateSession?.cookies?:accounts.cookies(accountId)
    fun updateAccountBadge(){if(!::accountBadge.isInitialized)return;val id=current?.accountId.orEmpty();val name=accounts.label(id,store.siteKey(currentUrl));accountBadge.visibility=if(!isIncognito&&(id.isNotEmpty()||accounts.hasDefaultName(store.siteKey(currentUrl))))View.VISIBLE else View.GONE;accountBadge.text=name;accountBadge.contentDescription=tr("当前账号：%1\$s，点击切换", name)}
    fun accountTabTitle(tab:BrowserTab):String {val name=accounts.label(tab.accountId,store.siteKey(tab.url));return tab.title+if(!tab.incognito&&(tab.accountId.isNotEmpty()||accounts.hasDefaultName(store.siteKey(tab.url))))" · $name"else""}
    internal fun switchAccount(id:String,url:String=currentUrl){
        check(!isIncognito){tr("请先退出无痕模式")};require(accounts.available(id)){tr("账号已移除或当前 WebView 不支持")};require(isHttp(url))
        if(id.isNotEmpty())accounts.profile(id) // Validate before discarding the old page.
        val old=current?:return
        panels.pages.close();webPermissions.cancel();speech.stop();hideVideo();dismissTabs();closeFind()
        uploadCallback?.onReceiveValue(null);uploadCallback=null
        old.web?.let{(it.parent as? ViewGroup)?.removeView(it);it.stopLoading();it.destroy()}
        val replacement=BrowserTab(url=url,accountId=id,openerId=old.openerId,searchOverride=old.searchOverride)
        tabs[selected]=replacement;tabs.filter{it.openerId==old.id}.forEach{it.openerId=replacement.id}
        switchTab(selected)
    }
    internal fun openAccount(id:String){
        check(!isIncognito);val account=requireNotNull(accounts.find(id)){tr("账号已移除")};require(accounts.available(id)){tr("请更新系统 WebView")}
        require(tabs.size<50){tr("最多打开 50 个标签页")};accounts.profile(id);panels.pages.close()
        tabs.add(BrowserTab(url=account.startUrl,accountId=id));switchTab(tabs.lastIndex)
    }
    internal fun deleteAccount(id:String,done:(Boolean)->Unit={}){
        check(!isIncognito);requireNotNull(accounts.find(id));accounts.profile(id)
        val activeId=current?.id
        panels.pages.close();webPermissions.cancel();speech.stop();hideVideo();dismissTabs();closeFind()
        val closing=tabs.filter{it.accountId==id};closing.forEach{t->t.web?.let{(it.parent as? ViewGroup)?.removeView(it);it.stopLoading();it.destroy()}}
        tabs.removeAll{it.accountId==id};closedTabs.removeAccount(id)
        if(tabs.isEmpty())tabs.add(BrowserTab())
        selected=tabs.indexOfFirst{it.id==activeId}.takeIf{it>=0}?:0;switchTab(selected)
        accounts.remove(id){ok->if(!isDestroyed&&!isFinishing)toast(if(ok)tr("账号空间已删除")else tr("账号已移除，残余数据将在下次启动清理"));done(ok)}
    }
    fun privateMode(){if(isIncognito){
        AlertDialog.Builder(this).setTitle(tr("退出无痕模式？")).setMessage(tr("关闭全部无痕标签并清理网站数据，返回普通标签。主动保存的文件和书签会保留。"))
            .setNegativeButton(tr("取消"),null).setPositiveButton(tr("退出并清理")){_,_->exitPrivate()}.show().window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }else enterPrivate()}
    internal fun enterPrivate():Boolean {
        if(isIncognito)return true
        val session=runCatching{PrivateSession.create()}.getOrElse{toast(tr("此 WebView 暂不支持安全的无痕隔离，请更新 Android System WebView"));return false}
        persistSession();panels.pages.close();webPermissions.cancel();speech.stop();hideVideo();dismissTabs();closeFind();folderOpen=""
        regularTabs=tabs.toList();regularSelected=selected
        tabs.forEach{t->t.web?.let{web->t.saved=Bundle().also{web.saveState(it)};(web.parent as? ViewGroup)?.removeView(web);web.stopLoading();web.destroy();t.web=null}}
        tabs.clear();privateSession=session;tabs.add(BrowserTab(incognito=true));selected=0
        WebView.setWebContentsDebuggingEnabled(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);buildChrome();switchTab(0);return true
    }
    internal fun exitPrivate(done:(Boolean)->Unit={}){
        val session=privateSession?:return
        panels.pages.close();webPermissions.cancel();speech.stop();hideVideo();dismissTabs();closeFind();folderOpen=""
        uploadCallback?.onReceiveValue(null);uploadCallback=null
        tabs.forEach{t->t.web?.let{web->(web.parent as? ViewGroup)?.removeView(web);web.stopLoading();web.destroy()};t.web=null;t.saved=null}
        tabs.clear();tabs.addAll(regularTabs);regularTabs=emptyList();privateSession=null
        if(tabs.isEmpty())tabs.add(BrowserTab())
        selected=regularSelected.coerceIn(0,tabs.lastIndex);window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);buildChrome();switchTab(selected)
        val owner=java.lang.ref.WeakReference(this)
        session.close{ok->owner.get()?.takeUnless{it.isDestroyed||it.isFinishing}?.toast(if(ok)tr("无痕网站数据已清理")else tr("无痕清理未完成，将在下次启动重试"));done(ok)}
    }
    private var emptyReplacementId:Long?=null
    var selected=0
    var fullScreen=false
    private var customView:View?=null
    private var customViewCallback:WebChromeClient.CustomViewCallback?=null
    private var uploadCallback:ValueCallback<Array<Uri>>?=null
    private var documentRead:((String)->Unit)?=null
    private var documentWrite:String?=null
    private var findBar:LinearLayout?=null
    private var folderOpen:String=""
    private var folderOverlay:View?=null
    private var tabsOverlay:View?=null
    var overlayKind="";private set
    private var scrollTravel=0
    private var lastScrollDirection=0
    val current get()=tabs.getOrNull(selected)
    val currentUrl get()=current?.url.orEmpty()
    val prefs get()=store.prefs

    private var uiLanguage=""
    override fun attachBaseContext(base:Context){super.attachBaseContext(AppLanguage.wrap(base))}
    override fun onCreate(savedInstanceState:Bundle?) {
        WebView.enableSlowWholeDocumentDraw()
        AppLanguage.bind(this);uiLanguage=AppLanguage.effective(this)
        PrivateSession.discardStale()
        accounts=AccountProfiles(this);accounts.discardOrphans()
        store=BrowserStore(this)
        ui=Ui(this,isDark());setTheme(if(ui.dark)R.style.AppThemeDark else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        icons=SiteIcons(this);speech=PageSpeech(this);scripts=UserScripts(this,httpsOnly={prefs.getBoolean("httpsOnly",false)},openTab={web,url,active->openScriptTab(web,url,active)},closeTab={closeTab(it)})
        filtering=AdFiltering(this);sniffer=ResourceSniffer(this)
        panels=BrowserPanels(this)
        filtering.subscriptions.update(automatic=true)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        buildChrome()
        if(prefs.getBoolean("restore",true))runCatching{
            val a=JSONArray(prefs.getString("tabs","[]"));for(i in 0 until minOf(a.length(),50)){val o=a.getJSONObject(i);val url=o.optString("url","about:home");val account=o.optString("account");if((url=="about:home"||isHttp(url))&&accounts.available(account))tabs.add(BrowserTab(url=url,title=o.optString("title",tr("网页")),accountId=account))}
            selected=prefs.getInt("selected",0).coerceIn(0,maxOf(0,tabs.lastIndex))
        }
        if(tabs.isEmpty())tabs.add(BrowserTab())
        tabs.filter{it.url=="about:home"}.forEach{it.title=tr("主页")}
        switchTab(selected)
        handleIntent(intent)
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){goBack()}
    }
    fun isDark():Boolean=when(store.prefs.getString("theme","system")){"dark"->true;"light"->false;else->resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
    override fun onConfigurationChanged(newConfig:Configuration){
        super.onConfigurationChanged(newConfig)
        resources.updateConfiguration(AppLanguage.configuration(this),resources.displayMetrics)
        if(uiLanguage!=AppLanguage.effective(this))refreshLanguage()else if(isDark()!=ui.dark)retheme()
    }
    fun refreshLanguage(){
        val settingsVisible=panels.pages.visible
        webPermissions.cancel();speech.stop();hideVideo();panels.pages.close();dismissTabs();closeFind()
        resources.updateConfiguration(AppLanguage.configuration(this),resources.displayMetrics)
        AppLanguage.bind(this);uiLanguage=AppLanguage.effective(this)
        tabs.filter{it.url=="about:home"}.forEach{it.title=tr("主页")}
        regularTabs.filter{it.url=="about:home"}.forEach{it.title=tr("主页")}
        filtering.subscriptions.rebuild()
        retheme()
        if(settingsVisible)panels.settingsUi.open("language")
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent)}
    private fun handleIntent(intent:Intent){
        if(intent.action in listOf(Intent.ACTION_WEB_SEARCH,Intent.ACTION_PROCESS_TEXT,Intent.ACTION_SEARCH)){
            val query=(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?:intent.getStringExtra(android.app.SearchManager.QUERY)).orEmpty().take(4096)
            if(query.isNotBlank()){panels.pages.close();open(searchUrl(query),true)}
        }else if(intent.action==Intent.ACTION_VIEW)intent.dataString?.takeIf(::isHttp)?.let{panels.pages.close();open(it,prefs.getBoolean("externalNewTab",true))}
    }
    fun buildChrome() {
        suggestions.dismiss()
        root=ui.column();root.setBackgroundColor(ui.bg)
        top=ui.row().apply{setPadding(ui.dp(8),ui.dp(5),ui.dp(8),ui.dp(5));setBackgroundColor(ui.panel)}
        val addressBox=ui.row().apply{background=ui.round(ui.soft,15)}
        addressBox.addView(ui.icon("site",tr("网站设置")){panels.site()})
        accountBadge=ui.label("",11f,ui.accent).apply{maxLines=1;maxWidth=ui.dp(76);minimumHeight=ui.dp(48);minimumWidth=ui.dp(48);gravity=Gravity.CENTER;ellipsize=android.text.TextUtils.TruncateAt.END;background=ui.round(ui.soft,8);isFocusable=true;setOnClickListener{panels.accounts.current()}}
        addressBox.addView(accountBadge);updateAccountBadge()
        address=ui.edit(tr("搜索或输入网址"),field=AddressField(this)).apply{
            setPadding(0,0,0,0);background=null;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setImeActionLabel(tr("前往"),android.view.inputmethod.EditorInfo.IME_ACTION_GO)
            if(isIncognito){imeOptions=imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
            setSelectAllOnFocus(true)
            setOnFocusChangeListener{_,focused->if(focused){setText(currentUrl.takeIf{isHttp(it)}.orEmpty());post{if(hasFocus())selectAll()}} else {suggestions.dismiss();syncAddress()};updateAddressAction()}
            setOnEditorActionListener{_,action,event->
                if(event?.keyCode==KeyEvent.KEYCODE_ENTER){if(event.action==KeyEvent.ACTION_UP)navigateInput(text.toString());true}
                else if(action in listOf(android.view.inputmethod.EditorInfo.IME_ACTION_GO,android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,android.view.inputmethod.EditorInfo.IME_ACTION_DONE)){navigateInput(text.toString());true}else false
            }
        }
        address.onChange{suggestions.update(it)}
        addressBox.addView(address,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        refreshButton=ui.icon("refresh",tr("刷新网页")){when{address.hasFocus()->address.setText("");prefs.getString("toolbarAction","refresh")=="qr"->scanQr();progress.visibility==View.VISIBLE->{current?.web?.stopLoading();progress.visibility=View.GONE;updateAddressAction()};else->reload()}};addressBox.addView(refreshButton)
        if(isIncognito)top.addView(ui.icon("incognito",tr("无痕模式 · 点击退出")){privateMode()})
        address.hint=if(isIncognito)tr("无痕搜索或输入网址")else tr("搜索或输入网址")
        top.addView(addressBox,LinearLayout.LayoutParams(if(isIncognito)0 else -1,-2,if(isIncognito)1f else 0f))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE;progressTintList=android.content.res.ColorStateList.valueOf(ui.accent)}
        content=FrameLayout(this).apply{isFocusableInTouchMode=true}
        bottom=ui.row().apply{setBackgroundColor(ui.panel);setPadding(ui.dp(7),ui.dp(3),ui.dp(7),ui.dp(3))}
        fun nav(name:String,label:String,run:()->Unit){bottom.addView(ui.icon(name,label,run),LinearLayout.LayoutParams(0,ui.dp(48),1f))}
        backButton=ui.icon("back",tr("后退")){current?.web?.takeIf{currentUrl!="about:home"&&it.canGoBack()}?.goBack()};bottom.addView(backButton,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        forwardButton=ui.icon("forward",tr("前进")){current?.web?.takeIf{currentUrl!="about:home"&&it.canGoForward()}?.goForward()};bottom.addView(forwardButton,LinearLayout.LayoutParams(0,ui.dp(48),1f));nav("home",tr("主页")){goHome()}
        val tabButton=FrameLayout(this).apply{contentDescription=tr("标签页");isClickable=true;isFocusable=true;setOnClickListener{panels.tabs()}}
        tabButton.setOnLongClickListener{dismissTabs();newHome();true}
        tabCount=ui.label("1",15f,ui.accent).apply{gravity=Gravity.CENTER;background=ui.round(ui.soft,8)}
        tabButton.addView(tabCount,FrameLayout.LayoutParams(ui.dp(28),ui.dp(30),Gravity.CENTER));bottom.addView(tabButton,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        nav("menu",tr("菜单")){panels.menu()}
        if(prefs.getBoolean("bottomAddress",false)){root.addView(progress,LinearLayout.LayoutParams(-1,ui.dp(2)));root.addView(content,LinearLayout.LayoutParams(-1,0,1f));root.addView(top)}
        else{root.addView(top);root.addView(progress,LinearLayout.LayoutParams(-1,ui.dp(2)));root.addView(content,LinearLayout.LayoutParams(-1,0,1f))}
        root.addView(bottom);setContentView(root);content.requestFocus()
        if(Build.VERSION.SDK_INT>=30){
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener{view,insets->val bars=insets.getInsets(WindowInsets.Type.systemBars());val ime=insets.getInsets(WindowInsets.Type.ime());view.setPadding(if(fullScreen)0 else bars.left,if(fullScreen)0 else bars.top,if(fullScreen)0 else bars.right,maxOf(if(fullScreen)0 else bars.bottom,ime.bottom));WindowInsets.CONSUMED}
            window.insetsController?.setSystemBarsAppearance(if(ui.dark)0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }else{root.fitsSystemWindows=true;window.decorView.systemUiVisibility=if(ui.dark)0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR}
        window.statusBarColor=ui.panel;window.navigationBarColor=ui.panel
    }
    fun retheme(){
        (current?.web?.parent as? android.view.ViewGroup)?.removeView(current?.web)
        ui=Ui(this,isDark());theme.applyStyle(if(ui.dark)R.style.AppThemeDark else R.style.AppTheme,true)
        findBar=null;buildChrome();tabs.forEach{t->t.web?.let{configure(it,t.url)}};switchTab(selected);setFullscreen(fullScreen)
    }
    fun isHttp(s:String)=runCatching{val u=Uri.parse(s);u.scheme in listOf("http","https")&&!u.host.isNullOrBlank()&&u.userInfo==null}.getOrDefault(false)
    fun navigateInput(input:String) {
        when(val result=AddressInput.resolve(input)){
            is AddressInput.Result.Navigate->open(result.url)
            is AddressInput.Result.Search->open(searchUrl(result.text))
            is AddressInput.Result.Invalid->toast(result.message)
        }
    }

    fun searchUrl(query:String):String {
        val template=current?.searchOverride?:prefs.getString("search",SearchEngines.default)!!
        return AddressInput.searchUrl(template,query)
    }
    fun open(url:String,newTab:Boolean=false){
        if(ScriptNetwork.isInstallUrl(url)){panels.scripts.fromUrl(url);return}
        val url=NavigationPolicy.secure(url,prefs.getBoolean("httpsOnly",false))
        security.cancel()
        address.clearFocus();content.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
        dismissTabs();showAddress()
        if(!isHttp(url)){toast(tr("只支持 HTTP / HTTPS 网页"));return}
        if(newTab){if(tabs.size>=50){toast(tr("最多打开 50 个标签页"));return};tabs.add(BrowserTab(url=url,incognito=isIncognito,accountId=current?.accountId.orEmpty(),openerId=current?.id));switchTab(tabs.lastIndex);return}
        val t=current?:return;security.begin(t);t.error=null;t.url=url;folderOpen="";if(t.web==null){t.saved=null;attach(t)}else{configure(t.web!!,url);content.removeAllViews();(t.web!!.parent as? android.view.ViewGroup)?.removeView(t.web);content.addView(t.web,FrameLayout.LayoutParams(-1,-1));t.web!!.loadUrl(url)}
        syncAddress();updateScrollButtons();persistSession()
    }
    fun goHome(){dismissTabs();showAddress();val t=current?:return;t.web?.stopLoading();t.web?.onPause();t.error=null;t.url="about:home";t.committedUrl="about:home";t.title=tr("主页");t.resources.start("about:home","");t.filterSession.start("about:home");folderOpen="";closeFind();renderHome();syncAddress();persistSession()}
    fun switchTab(index:Int){
        if(index !in tabs.indices)return;security.cancel();webPermissions.cancel();address.clearFocus();content.requestFocus();dismissTabs();showAddress();closeFind();current?.web?.onPause();selected=index;val t=tabs[index];t.used=System.currentTimeMillis()
        if(t.url=="about:home")renderHome()else attach(t)
        tabCount.text=tabs.size.toString();syncAddress();updateScrollButtons();progress.visibility=View.GONE;persistSession();trimTabs()
    }
    private fun openScriptTab(source:WebView,url:String,active:Boolean):Long? {
        val parent=tabs.find{it.web===source&&!it.incognito}?:return null
        if(isIncognito||tabs.size>=50||!accounts.available(parent.accountId))return null
        val tab=BrowserTab(url=url,accountId=parent.accountId,openerId=parent.id,title=Uri.parse(url).host.orEmpty(),used=System.currentTimeMillis())
        tabs.add(tab)
        if(active){panels.pages.close();switchTab(tabs.lastIndex)}else {tab.web=createWeb(tab).also{configure(it,url);it.loadUrl(url);it.onPause()};tabCount.text=tabs.size.toString();persistSession();trimTabs()}
        return tab.id
    }
    fun openBackground(url:String){
        val url=NavigationPolicy.secure(url,prefs.getBoolean("httpsOnly",false))
        if(!isHttp(url)){toast(tr("只支持 HTTP / HTTPS 网页"));return}
        if(tabs.size>=50){toast(tr("最多 50 个标签页"));return}
        val t=BrowserTab(url=url,incognito=isIncognito,accountId=current?.accountId.orEmpty(),title=Uri.parse(url).host?:tr("网页"),openerId=current?.id,used=System.currentTimeMillis())
        tabs.add(t);t.web=createWeb(t).also{configure(it,url);it.loadUrl(url);it.onPause()}
        tabCount.text=tabs.size.toString();persistSession();trimTabs();toast(tr("已在后台打开"))
    }
    fun closeTab(id:Long){
        val i=tabs.indexOfFirst{it.id==id};if(i<0)return;if(current?.id==id)webPermissions.cancel()
        val activeId=current?.id;val t=tabs.removeAt(i)
        if(!t.incognito&&(isHttp(t.url)||t.url=="about:home"))closedTabs.push(ClosedTab(t.url,t.title,i,t.openerId,t.searchOverride,t.accountId))
        (t.web?.parent as? android.view.ViewGroup)?.removeView(t.web);t.web?.destroy()
        if(tabs.isEmpty()&&isIncognito){exitPrivate();return}
        if(tabs.isEmpty()){val home=BrowserTab();tabs.add(home);emptyReplacementId=home.id}
        val next=if(activeId!=id)tabs.indexOfFirst{it.id==activeId}else tabs.indexOfFirst{it.id==t.openerId}
        switchTab(if(next>=0)next else i.coerceAtMost(tabs.lastIndex))
    }
    fun closeOtherTabs(){val keep=current?:return;emptyReplacementId=null;val closing=tabs.withIndex().filter{it.value!==keep}
        closing.forEach{(index,t)->if(!t.incognito&&(isHttp(t.url)||t.url=="about:home"))closedTabs.push(ClosedTab(t.url,t.title,index,t.openerId,t.searchOverride,t.accountId));(t.web?.parent as? ViewGroup)?.removeView(t.web);t.web?.destroy()}
        tabs.removeAll{it!==keep};selected=0;switchTab(0)
    }
    fun closeAllTabs(){
        security.cancel();webPermissions.cancel();speech.stop();dismissTabs()
        if(isIncognito){exitPrivate();return}
        tabs.forEachIndexed{index,t->closedTabs.push(ClosedTab(t.url,t.title,index,t.openerId,t.searchOverride,t.accountId));(t.web?.parent as? ViewGroup)?.removeView(t.web);t.web?.destroy()}
        tabs.clear();val home=BrowserTab();tabs.add(home);emptyReplacementId=home.id;selected=0;switchTab(0)
    }
    fun exitBrowser(){security.cancel();panels.pages.close();persistSession();if(isIncognito)exitPrivate{finishAndRemoveTask()}else finishAndRemoveTask()}
    fun openWithAccount(url:String,id:String,newTab:Boolean){
        if(isIncognito){open(url,newTab);return}
        if(!accounts.available(id)){toast(tr("账号已移除或当前 WebView 不支持"));return}
        if(newTab){if(tabs.size>=50){toast(tr("最多 50 个标签页"));return};panels.pages.close();tabs.add(BrowserTab(url=NavigationPolicy.secure(url,prefs.getBoolean("httpsOnly",false)),accountId=id,openerId=current?.id));switchTab(tabs.lastIndex)}
        else {panels.pages.close();switchAccount(id,NavigationPolicy.secure(url,prefs.getBoolean("httpsOnly",false)))}
    }
    fun undoCloseTab(){
        if(isIncognito){toast(tr("无痕标签关闭后不保留恢复记录"));return}
        if(tabs.size>=50){toast(tr("请先关闭一个标签"));return}
        val closed=closedTabs.pop()?:run{toast(tr("没有可恢复的标签"));return}
        if(!accounts.available(closed.accountId)){toast(tr("该标签的账号已移除或 WebView 不支持，未在默认账号中打开"));return}
        if(tabs.size==1&&tabs[0].id==emptyReplacementId&&tabs[0].url=="about:home"){tabs[0].web?.destroy();tabs.clear()}
        emptyReplacementId=null
        val index=closed.index.coerceIn(0,tabs.size)
        tabs.add(index,BrowserTab(url=closed.url,title=closed.title,openerId=closed.openerId,searchOverride=closed.searchOverride,accountId=closed.accountId));switchTab(index)
    }
    fun clearHistoryData(){store.history.clear();store.save();closedTabs.clear();tabs.forEach{it.web?.clearHistory();it.saved=null}}
    fun newHome(){if(tabs.size>=50){toast(tr("最多 50 个标签页"));return};tabs.add(BrowserTab(incognito=isIncognito));switchTab(tabs.lastIndex)}
    internal fun trimTabs(){tabs.filter{it!=current&&it.web!=null}.sortedByDescending{it.used}.drop(prefs.getInt("activeWebViews",4).coerceIn(2,8)-1).forEach{t->t.saved=Bundle().also{t.web?.saveState(it)};t.web?.destroy();t.web=null}}
    private fun attach(t:BrowserTab){
        t.url=NavigationPolicy.secure(t.url,prefs.getBoolean("httpsOnly",false))
        if(t.error!=null){showPageError(t);return}
        content.removeAllViews()
        if(t.web==null){val web=createWeb(t);t.web=web;configure(web,t.url);val restored=t.saved?.let{web.restoreState(it)};t.saved=null;if(restored==null)web.loadUrl(t.url)}
        (t.web!!.parent as? android.view.ViewGroup)?.removeView(t.web);content.addView(t.web,FrameLayout.LayoutParams(-1,-1));t.web!!.onResume()
    }
    fun configure(web:WebView,url:String){
        val desktop=store.siteBool(url,"desktop",prefs.getBoolean("desktop",false))
        web.settings.apply{
            javaScriptEnabled=store.siteBool(url,"js",prefs.getBoolean("js",true));domStorageEnabled=true
            allowFileAccess=false;allowContentAccess=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true);builtInZoomControls=true;displayZoomControls=false;useWideViewPort=true;loadWithOverviewMode=true
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(true)
            disabledActionModeMenuItems=WebSettings.MENU_ITEM_PROCESS_TEXT or (if(isIncognito)WebSettings.MENU_ITEM_WEB_SEARCH else 0)
            mediaPlaybackRequiresUserGesture=!store.siteBool(url,"autoplay",prefs.getBoolean("autoplay",false));loadsImagesAutomatically=!store.siteBool(url,"noImages",prefs.getBoolean("noImages",false));blockNetworkImage=!loadsImagesAutomatically
            textZoom=store.siteZoom(url)
            val custom=prefs.getString("site.${store.siteKey(url)}.ua","").orEmpty()
            val ua=custom.ifBlank{if(desktop)"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${WebView.getCurrentWebViewPackage()?.versionName?:"130.0.0.0"} Safari/537.36" else WebSettings.getDefaultUserAgent(this@BrowserActivity)}
            if(userAgentString!=ua)userAgentString=ua
        }
        val thirdParty=store.siteBool(url,"thirdParty",prefs.getBoolean("thirdParty",false))
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,thirdParty)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings,ui.dark&&store.siteBool(url,"dark",prefs.getBoolean("webDark",true)))
        web.setBackgroundColor(ui.bg)
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // WebView handles clicks; this observer never consumes touch events.
    private fun createWeb(tab:BrowserTab):WebView=object:WebView(this){
        override fun destroy(){scriptInstaller.detach(this);blobs.detach(this);if(this@BrowserActivity::scripts.isInitialized)scripts.detach(this);super.destroy()}
        override fun startActionMode(callback:ActionMode.Callback,type:Int):ActionMode? = super.startActionMode(SelectionActions.wrap(this@BrowserActivity,tab,this,callback),type)
        override fun onCreateInputConnection(outAttrs:android.view.inputmethod.EditorInfo):android.view.inputmethod.InputConnection? {
            val connection=super.onCreateInputConnection(outAttrs)
            if(tab.incognito)outAttrs.imeOptions=outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            return connection
        }
    }.apply {
        if(tab.incognito){checkNotNull(privateSession).attach(this);isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;settings.saveFormData=false}else {accounts.attach(this,tab.accountId);scripts.attach(this)}
        tab.filterSession.start(tab.url)
        tab.resources.start(tab.url,settings.userAgentString)
        sniffer.attach(this,tab.resources)
        filtering.attach(this,tab.filterSession)
        blobs.attach(this,tab)
        scriptInstaller.attach(this)
        var gestureY=0f
        // Use finger movement rather than layout-generated scroll events: changing toolbar
        // height can itself move scrollY, which otherwise immediately reverses the hide.
        setOnTouchListener{_,event->
            if(event.actionMasked==MotionEvent.ACTION_DOWN){gestureY=event.rawY;scrollTravel=0;lastScrollDirection=0}
            else if(event.actionMasked==MotionEvent.ACTION_MOVE&&event.pointerCount==1){
                val delta=(gestureY-event.rawY).toInt();gestureY=event.rawY
                if(tab==current&&!fullScreen&&!address.hasFocus()&&findBar==null&&tabsOverlay==null&&prefs.getBoolean("autoHideAddress",true)){
                    val direction=delta.compareTo(0);if(direction!=lastScrollDirection){scrollTravel=0;lastScrollDirection=direction};scrollTravel+=delta
                    if(scrollTravel < -ui.dp(24))showAddress()
                    else if(scrollTravel>ui.dp(32)){this@BrowserActivity.top.visibility=View.GONE;scrollTravel=0}
                }
            };false
        }
        setDownloadListener{url,ua,disposition,mime,length->
            if(tab !in tabs)return@setDownloadListener
            // A download is not a committed page: don't restore/re-download it on launch.
            tab.url=tab.committedUrl
            if(tab==current){syncAddress();if(tab.url=="about:home")renderHome()}
            persistSession();if(ScriptNetwork.isInstallUrl(url)){if(tab===current)panels.scripts.fromUrl(url);return@setDownloadListener};if(url.startsWith("blob:"))blobs.request(tab,this,url,disposition,mime)else requestDownload(url,ua,disposition,mime,referer=tab.committedUrl,contentLength=length,downloadScope=if(tab.incognito)null else tab.accountId)
        }
        setOnLongClickListener {
            val h=hitTestResult;val u=h.extra
            if(!u.isNullOrBlank()&&h.type in listOf(WebView.HitTestResult.SRC_ANCHOR_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,WebView.HitTestResult.IMAGE_TYPE)){
                val isImage=h.type==WebView.HitTestResult.IMAGE_TYPE
                val handler=Handler(Looper.getMainLooper()){message->val link=if(isImage)u else message.data.getString("url")?:u;val text=message.data.getString("title").orEmpty()
                    if(current!==tab||tab.web!==this||!hasWindowFocus())return@Handler true
                    val col=ui.column(16);lateinit var sheet:Dialog
                    fun action(label:String,run:()->Unit){col.addView(ui.item(label){sheet.dismiss();run()})}
                    col.addView(ui.title(if(isImage)tr("图片操作")else tr("链接操作")))
                        col.addView(ui.label(link.take(200),12f,ui.muted))
                        action(tr("在新标签页打开")){open(link,true)}
                        action(tr("在后台打开")){openBackground(link)}
                        action(if(isImage)tr("保存图片")else tr("添加到书签")){if(isImage)requestDownload(link,settings.userAgentString,"","")else panels.library.collect(text.ifBlank{link},link)}
                        action(tr("复制链接")){copy(tr("链接"),link)}
                        if(text.isNotBlank())action(tr("复制文本")){copy(tr("链接文本"),text)}
                        action(tr("添加到主页")){panels.library.collect(text.ifBlank{link},link,false,true)}
                    sheet=panels.dialog(col);true};requestFocusNodeHref(handler.obtainMessage());true
            }else false
        }
        webViewClient=object:WebViewClient(){
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                if(NavigationPolicy.blocked(request.url.toString(),prefs.getBoolean("httpsOnly",false)))return WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
                val blocked=filtering.intercept(tab.filterSession,request)
                if(blocked==null)sniffer.observe(tab.resources,request)
                return blocked
            }
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                val url=request.url.toString()
                if(request.isForMainFrame&&tab===current&&ScriptNetwork.isInstallUrl(url)){panels.scripts.fromUrl(url);return true}
                if(isHttp(url)){if(request.isForMainFrame){val secure=NavigationPolicy.secure(url,prefs.getBoolean("httpsOnly",false));if(secure!=url){view.loadUrl(secure);return true};tab.url=url;configure(view,url)}else if(NavigationPolicy.blocked(url,prefs.getBoolean("httpsOnly",false)))return true;return false}
                if(url.startsWith("blob:")&&request.isForMainFrame&&request.hasGesture()){blobs.request(tab,view,url,"","");return true}
                if(request.isForMainFrame&&request.hasGesture()&&tab==current&&hasWindowFocus())openExternal(url);return true
            }
            override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?){if(tab !in tabs)return;if(tab.url=="about:home"){view.stopLoading();return};val secure=NavigationPolicy.secure(url,prefs.getBoolean("httpsOnly",false));if(secure!=url){view.stopLoading();view.loadUrl(secure);return};security.started(tab,url);blobs.navigated(view);if(tab==current)webPermissions.cancel();tab.url=url;tab.error=null;configure(view,url);tab.filterSession.start(url);tab.resources.start(url,view.settings.userAgentString);if(tab==current){syncAddress();this@BrowserActivity.progress.visibility=View.VISIBLE}}
            override fun doUpdateVisitedHistory(view:WebView,url:String,isReload:Boolean){if(tab==current)updateNavigation()}
            override fun onPageFinished(view:WebView,url:String){
                if(tab !in tabs)return
                scriptInstaller.finished(view)
                if(!tab.incognito){scripts.navigated(view);scripts.finished(view)}
                filtering.finished(view,tab.filterSession)
                sniffer.scan(view,tab.resources)
                blobs.finished(view)
                security.finished(tab,view)
                if(tab.url=="about:home"||tab.error!=null)return;tab.url=url;tab.committedUrl=url;tab.title=view.title?.takeIf{it.isNotBlank()}?:Uri.parse(url).host?:tr("网页")
                if(tab==current){syncAddress();this@BrowserActivity.progress.visibility=View.GONE};if(!tab.incognito&&prefs.getBoolean("recordHistory",true))store.visit(tab.title,url);if(!tab.incognito)accounts.cookies(tab.accountId).flush();persistSession()
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){security.error(tab,view,handler,error)}
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame&&tab.url!="about:home"){val code=error.errorCode;val description=error.description.toString();tab.error={when(code){ERROR_HOST_LOOKUP->tr("找不到这个网站，请检查网址或网络。");ERROR_CONNECT,ERROR_TIMEOUT->tr("连接失败或超时，请检查网络后重试。");else->tr("网页暂时无法加载：%1\$s", description)}};if(tab==current)showPageError(tab)}}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean{(view.parent as? android.view.ViewGroup)?.removeView(view);view.destroy();tab.web=null;tab.saved=null;tab.error={tr("网页进程已退出。点击重新加载可恢复；未提交的表单可能无法找回。")};if(tab==current)showPageError(tab);return true}
        }
        webChromeClient=object:WebChromeClient(){
            override fun onReceivedIcon(view:WebView,icon:Bitmap){if(!tab.incognito&&tab in tabs)icons.save(view.url?:tab.url,icon)}
            override fun onProgressChanged(view:WebView,value:Int){if(tab==current){this@BrowserActivity.progress.progress=value;this@BrowserActivity.progress.visibility=if(value==100||tab.error!=null)View.GONE else View.VISIBLE;updateAddressAction()}}
            override fun onReceivedTitle(view:WebView,title:String?){if(tab.url!="about:home"){tab.title=title?.takeIf{it.isNotBlank()}?:tab.title;if(tab===current)syncAddress()}}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean{
                if(!isUserGesture||tabs.size>=50||tab !in tabs)return false
                val t=BrowserTab(url="about:blank",incognito=tab.incognito,accountId=tab.accountId,title=tr("新页面"),openerId=tab.id);val web=createWeb(t);t.web=web;configure(web,tab.url);tabs.add(t)
                (resultMsg.obj as WebView.WebViewTransport).webView=web;resultMsg.sendToTarget();switchTab(tabs.lastIndex);return true
            }
            override fun onCloseWindow(window:WebView){tabs.find{it.web==window}?.let{closeTab(it.id)}}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean{
                uploadCallback?.onReceiveValue(null);uploadCallback=callback
                val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.mode==FileChooserParams.MODE_OPEN_MULTIPLE)
                val types=params.acceptTypes.filter{it.contains('/')};if(types.isNotEmpty())intent.putExtra(Intent.EXTRA_MIME_TYPES,types.toTypedArray())
                try{startActivityForResult(intent,103)}catch(e:Exception){uploadCallback?.onReceiveValue(null);uploadCallback=null;toast(tr("无法打开文件选择器"))};return true
            }
            override fun onShowCustomView(view:View,callback:CustomViewCallback){if(customView!=null){callback.onCustomViewHidden();return};customView=view;customViewCallback=callback;(window.decorView as android.view.ViewGroup).addView(view,android.view.ViewGroup.LayoutParams(-1,-1));setFullscreen(true)}
            override fun onHideCustomView(){hideVideo()}
            override fun onPermissionRequest(request:PermissionRequest){webPermissions.media(tab,request)}
            override fun onPermissionRequestCanceled(request:PermissionRequest){webPermissions.cancel(request)}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){webPermissions.location(tab,origin,callback)}
            override fun onGeolocationPermissionsHidePrompt(){webPermissions.cancel()}
        }
    }
    fun hideVideo(){customView?.let{(it.parent as? android.view.ViewGroup)?.removeView(it)};customView=null;customViewCallback?.onCustomViewHidden();customViewCallback=null;setFullscreen(false)}
    fun goBack(){when{tabsOverlay!=null->dismissTabs();customView!=null->hideVideo();fullScreen->setFullscreen(false);findBar!=null->closeFind();folderOpen.isNotEmpty()->{folderOpen="";renderHome()};currentUrl=="about:home"&&isIncognito->privateMode();currentUrl=="about:home"->moveTaskToBack(true);current?.web?.canGoBack()==true->{showAddress();current!!.web!!.goBack()};else->goHome()}}
    fun showAddress(){if(!fullScreen)top.visibility=View.VISIBLE;scrollTravel=0}
    fun dismissTabs():Boolean {val overlay=tabsOverlay?:return false;content.removeView(overlay);tabsOverlay=null;overlayKind="";return true}
    fun showTabs(view:View,kind:String="tabs"){
        dismissTabs();address.clearFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
        val overlay=FrameLayout(this).apply{setBackgroundColor(0x33000000);setOnClickListener{dismissTabs()}}
        val scroll=ScrollView(this).apply{setBackgroundColor(ui.panel);addView(view);isFillViewport=false;isClickable=true}
        overlay.addView(scroll,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));content.addView(overlay,FrameLayout.LayoutParams(-1,-1));tabsOverlay=overlay;overlayKind=kind
        // Elevation also controls drawing order; the menu must cover the floating page controls.
        overlay.elevation=ui.dp(12).toFloat()
        scroll.post{if(scroll.height>content.height*3/4){scroll.layoutParams=(scroll.layoutParams as FrameLayout.LayoutParams).apply{height=content.height*3/4}}}
    }
    // API 33+ uses the native OnBackInvokedDispatcher registered in onCreate.
    // This override is only the legacy Android 8-12 path.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed(){goBack()}
    fun reload(){security.cancel();current?.let(security::begin);if(currentUrl=="about:home")renderHome()else current?.let{t->val failed=t.error!=null;t.error=null;val existed=t.web!=null;attach(t);if(existed)t.web?.let{configure(it,currentUrl);if(failed)it.loadUrl(currentUrl)else it.reload()};updateScrollButtons()}}
    internal fun showPageError(tab:BrowserTab){
        if(tab!=current)return;progress.visibility=View.GONE;updateAddressAction();content.removeAllViews()
        val col=ui.column(24);col.addView(ui.title(tr("网页暂时无法打开")));col.addView(ui.label(tab.error?.invoke().orEmpty()));col.addView(ui.label(tab.url,13f,ui.muted).apply{setTextIsSelectable(true)})
        col.addView(ui.button(tr("重新加载"),true){reload()});col.addView(ui.button(tr("编辑网址")){focusAddress()});col.addView(ui.button(tr("证书异常处理")){panels.settingsUi.open("certificateExceptions")});col.addView(ui.button(tr("返回主页")){goHome()});content.addView(ScrollView(this).apply{addView(col)});updateNavigation()
    }
    fun focusAddress(){showAddress();address.requestFocus();address.selectAll();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT)}
    fun scanQr(){startActivityForResult(Intent(this,QrScanActivity::class.java),104)}
    fun openCollection(url:String){when(prefs.getString("collectionOpen","current")){"new"->open(url,true);"background"->openBackground(url);else->open(url)}}
    private fun updateAddressAction(){if(!::refreshButton.isInitialized||!::progress.isInitialized)return
        val state=when{address.hasFocus()->"close" to tr("清空地址");prefs.getString("toolbarAction","refresh")=="qr"->"qr" to tr("扫描二维码");progress.visibility==View.VISIBLE->"close" to tr("停止加载");else->"refresh" to tr("刷新网页")}
        (refreshButton as? IconView)?.setIcon(state.first);refreshButton.contentDescription=state.second
    }
    private fun syncAddress(){updateAccountBadge();updateAddressAction();updateNavigation();if(!address.hasFocus())address.setText(if(isHttp(currentUrl))current?.title?.takeIf{it.isNotBlank()&&it!=tr("主页")&&it!=tr("网页")}?:currentUrl else "")}
    internal fun updateNavigation(){if(!::backButton.isInitialized)return
        val web=current?.web?.takeIf{currentUrl!="about:home"}
        backButton.isEnabled=web?.canGoBack()==true;backButton.alpha=if(backButton.isEnabled)1f else .3f
        forwardButton.isEnabled=web?.canGoForward()==true;forwardButton.alpha=if(forwardButton.isEnabled)1f else .3f
    }
    internal fun updateScrollButtons(){scrollButtons?.let(content::removeView);scrollButtons=null
        val web=current?.web?.takeIf{isHttp(currentUrl)&&current?.error==null}?:return
        if(!prefs.getBoolean("edgeScroll",false))return
        val controls=ui.column().apply{background=ui.round(ui.panel,12);elevation=ui.dp(4).toFloat();alpha=.88f}
        controls.addView(ui.icon("pageTop",tr("回到顶部")){web.pageUp(true)})
        controls.addView(ui.icon("up",tr("上滑一页")){web.pageUp(false)})
        controls.addView(ui.icon("down",tr("下滑一页")){web.pageDown(false)})
        controls.addView(ui.icon("pageBottom",tr("跳到页底")){web.pageDown(true)})
        content.addView(controls,FrameLayout.LayoutParams(ui.dp(48),-2,Gravity.END or Gravity.CENTER_VERTICAL));scrollButtons=controls
    }
    fun setFullscreen(enabled:Boolean){fullScreen=enabled;top.visibility=if(enabled)View.GONE else View.VISIBLE;bottom.visibility=if(enabled)View.GONE else View.VISIBLE
        if(Build.VERSION.SDK_INT>=28)window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=if(enabled){if(Build.VERSION.SDK_INT>=30)WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT}
        window.statusBarColor=if(enabled)android.graphics.Color.TRANSPARENT else ui.panel
        if(Build.VERSION.SDK_INT>=30){window.insetsController?.apply{systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;if(enabled)hide(WindowInsets.Type.systemBars())else show(WindowInsets.Type.systemBars())};root.requestApplyInsets()}
        else window.decorView.systemUiVisibility=if(enabled)View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else if(ui.dark)0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if(enabled)toast(tr("按系统返回键退出全屏"))
    }
    fun showFind(query:String=""){val web=current?.web?.takeIf{currentUrl!="about:home"}?:return;closeFind();showAddress();val bar=ui.row();val input=ui.edit(tr("在页面中查找"));val status=ui.label("0/0",12f);bar.addView(input,LinearLayout.LayoutParams(0,ui.dp(48),1f));bar.addView(status)
        bar.addView(ui.icon("back",tr("上一个匹配")){web.findNext(false)});bar.addView(ui.icon("forward",tr("下一个匹配")){web.findNext(true)});bar.addView(ui.icon("close",tr("关闭查找")){closeFind()})
        input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){web.findAllAsync(s.toString())};override fun afterTextChanged(s:android.text.Editable?){} })
        web.setFindListener{active,total,done->if(done)status.text="${if(total==0)0 else active+1}/$total"};root.addView(bar,root.indexOfChild(content));findBar=bar;input.setText(query);input.selectAll();input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
    }
    private fun closeFind(){if(findBar==null)return;findBar?.let{root.removeView(it);current?.web?.clearMatches();current?.web?.setFindListener(null)};findBar=null;content.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)}
    private fun openExternal(url:String){
        if(prefs.getString("externalApps","ask")=="block"){toast(tr("已阻止网页唤起外部应用"));return}
        if(url.startsWith("javascript:")||url.startsWith("file:")||url.startsWith("content:")||url.startsWith("data:"))return
        val external=runCatching{if(url.startsWith("intent:"))Intent.parseUri(url,Intent.URI_INTENT_SCHEME).apply{component=null;selector=null;action=Intent.ACTION_VIEW;flags=0;addCategory(Intent.CATEGORY_BROWSABLE)}else Intent(Intent.ACTION_VIEW,Uri.parse(url))}.getOrNull()?:return
        AlertDialog.Builder(this).setTitle(tr("打开外部应用？")).setMessage(Uri.parse(url).scheme?:tr("外部链接")).setNegativeButton(tr("取消"),null).setPositiveButton(tr("打开")){_,_->try{startActivity(external)}catch(e:Exception){external.getStringExtra("browser_fallback_url")?.takeIf(::isHttp)?.let{open(it)}?:toast(tr("未找到对应应用"))}}.show()
    }
    fun requestDownload(url:String,ua:String,disposition:String,mime:String,referer:String=currentUrl,contentLength:Long=-1,suggestedName:String?=null,downloadScope:String?=null){
        if(url.startsWith("blob:")){val tab=current;val web=tab?.web;if(tab!=null&&web!=null){panels.pages.close();blobs.request(tab,web,url,disposition,mime,suggestedName.orEmpty())};return}
        if(NavigationPolicy.blocked(url,prefs.getBoolean("httpsOnly",false))){toast(tr("仅 HTTPS 模式已阻止 HTTP 下载"));return}
        if(!isHttp(url)){toast(tr("支持 HTTP/HTTPS 下载，Blob 地址不能直接下载"));return}
        val guessed=(suggestedName?:URLUtil.guessFileName(url,disposition,mime)).replace(Regex("[\\\\/:*?\"<>|]"),"_")
        if(downloadScope=="#private"){toast(tr("无痕下载请回到原网页重新发起，旧会话登录不会恢复"));return}
        val privateDownload=downloadScope==null&&isIncognito
        val scope=downloadScope?:current?.accountId.orEmpty()
        val downloadCookies=runCatching{if(privateDownload)checkNotNull(privateSession).cookies else accounts.cookies(scope)}.getOrElse{toast(tr("原下载账号已移除或暂不可用，请回到网页重新下载"));return}
        val name=ui.edit(tr("文件名"),guessed)
        val details=ui.column(16);details.addView(ui.label(tr("来源：%1\$s\n类型：%2\$s\n大小：%3\$s", Uri.parse(url).host.orEmpty(), mime.ifBlank{tr("未知")}, if(contentLength>=0)android.text.format.Formatter.formatFileSize(this,contentLength)else tr("未知（以下载结果为准）")),13f,ui.muted));details.addView(name);if(!privateDownload)details.addView(ui.label(tr("下载账号：%1\$s", accounts.label(scope)),13f,ui.muted));if(privateDownload)details.addView(ui.label(tr("无痕下载的文件及系统下载记录会保留，退出无痕不会删除。"),13f,ui.muted))
        AlertDialog.Builder(this).setTitle(tr("下载文件")).setView(details).setNegativeButton(tr("取消"),null).setPositiveButton(tr("下载")){_,_->
            val fileName=name.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"),"_").take(180).ifBlank{"download"}
            if(prefs.getBoolean("httpsOnly",false)){SecureDownloads.start(this,url,ua,referer,downloadCookies,fileName,mime);return@setPositiveButton}
            try{
                val req=DownloadManager.Request(Uri.parse(url)).setTitle(fileName).setDescription(Uri.parse(url).host).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,fileName)
                if(prefs.getBoolean("downloadWifiOnly",false))req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                if(mime.isNotBlank())req.setMimeType(mime);req.addRequestHeader("User-Agent",ua)
                downloadCookies.getCookie(url)?.let{req.addRequestHeader("Cookie",it)}
                if(isHttp(referer))req.addRequestHeader("Referer",referer)
                val id=(getSystemService(DOWNLOAD_SERVICE)as DownloadManager).enqueue(req)
                val ids=prefs.getStringSet("downloads",emptySet())!!.toMutableSet();ids.add(id.toString());prefs.edit().putStringSet("downloads",ids).putString("download.$id.referer",referer).putString("download.$id.ua",ua).putString("download.$id.account",if(privateDownload)"#private"else scope).apply();toast(tr("已开始下载"))
            }catch(e:Exception){toast(tr("下载失败：%1\$s", e.message))}
        }.show().apply{if(privateDownload)window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)}
    }
    fun readDocument(mime:String="*/*",callback:(String)->Unit){documentRead=callback;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime),101)}
    fun writeDocument(name:String,mime:String,text:String){documentWrite=text;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),102)}
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,permissions,results);if(code==105)webPermissions.result()}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        when(requestCode){
            101->{val callback=documentRead;documentRead=null;if(resultCode==RESULT_OK)data?.data?.let{uri->try{val bytes=contentResolver.openInputStream(uri)?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2_097_152){tr("文件超过 2 MB")};out.write(buffer,0,n)};out.toByteArray()}?:throw Exception(tr("读取失败"));callback?.invoke(bytes.toString(Charsets.UTF_8))}catch(e:Exception){toast(e.message?:tr("读取失败"))}}}
            102->{val text=documentWrite;documentWrite=null;if(resultCode==RESULT_OK&&text!=null)data?.data?.let{uri->try{contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray())}?:throw Exception(tr("无法写入"));toast(tr("已导出"))}catch(e:Exception){toast(tr("导出失败"))}}}
            103->{val result=if(resultCode==RESULT_OK){data?.clipData?.let{c->Array(c.itemCount){c.getItemAt(it).uri}}?:data?.data?.let{arrayOf(it)}}else null;uploadCallback?.onReceiveValue(result);uploadCallback=null}
            104->{if(resultCode==RESULT_OK)data?.getStringExtra("url")?.takeIf(::isHttp)?.let{open(it)}}
            107->exports.result(resultCode,data?.data)
        }
    }
    fun persistSession(){if(isIncognito||tabs.any{it.incognito})return;val a=JSONArray();tabs.forEach{a.put(JSONObject().put("url",it.url).put("title",it.title).put("account",it.accountId))};prefs.edit().putString("tabs",a.toString()).putInt("selected",selected).apply()}
    override fun onPause(){suggestions.dismiss();super.onPause();speech.pause();current?.web?.onPause();persistSession();if(!isIncognito)accounts.flush()}
    override fun onStop(){security.cancel();webPermissions.cancel();super.onStop()}
    override fun onResume(){super.onResume();if(uiLanguage!=AppLanguage.effective(this))refreshLanguage();current?.web?.onResume()}
    override fun onDestroy(){scripts.close();security.clear();blobs.close();exports.close();webPermissions.cancel();suggestions.dismiss();panels.pages.close();panels.reader.close();speech.close();filtering.subscriptions.close();uploadCallback?.onReceiveValue(null);tabs.forEach{t->(t.web?.parent as? android.view.ViewGroup)?.removeView(t.web);t.web?.destroy()};tabs.clear();privateSession?.close();privateSession=null;super.onDestroy()}
    fun toast(message:String){Toast.makeText(this,message,Toast.LENGTH_SHORT).show()}
    fun copy(label:String,text:String){(getSystemService(CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label,text));toast(tr("已复制"))}

    fun renderHome(){
        if(isIncognito){
            content.removeAllViews();val col=ui.column(24)
            col.addView(ui.title(tr("无痕浏览")));col.addView(ui.label(tr("此会话与普通浏览的 Cookie 和网站存储隔离，不记录历史、不保存标签及最近关闭记录。")))
            col.addView(ui.label(tr("关闭最后一个无痕标签或选择退出时清理网站数据。切到后台不会自动退出。下载、书签、离线文章和主动导出会保留。"),14f,ui.muted))
            col.addView(ui.label(tr("无痕不会隐藏你的 IP，也不能阻止网站、网络提供者或登录账号识别你。无痕中不运行用户脚本。"),14f,ui.muted))
            col.addView(ui.button(tr("开始无痕搜索"),true){focusAddress()});col.addView(ui.button(tr("退出无痕模式")){privateMode()})
            content.addView(ScrollView(this).apply{addView(col)});return
        }
        content.removeAllViews();folderOverlay=null
        val scroll=ScrollView(this);val column=ui.column(20);scroll.addView(column);content.addView(scroll)
        val heading=ui.row();heading.setPadding(0,ui.dp(40),0,ui.dp(25));val names=ui.column();names.addView(ui.title(tr("清岚")));heading.addView(names,LinearLayout.LayoutParams(0,-2,1f));if(prefs.getBoolean("homeTitle",true))column.addView(heading)
        val grid=GridLayout(this).apply{columnCount=prefs.getInt("homeColumns",4).coerceIn(3,5)};column.addView(grid,LinearLayout.LayoutParams(-1,-2));populateHome(grid,"")
        if(folderOpen.isNotEmpty())showFolder(folderOpen)
    }
    private fun moveHome(id:String,parent:String,target:String?=null,after:Boolean=false){
        LibraryOrder.home(store.home,id,parent,target,after);store.save();folderOpen=parent;renderHome()
    }
    private fun populateHome(grid:GridLayout,parent:String){
        grid.removeAllViews()
        store.home.filter{it.parent==parent}.forEach{item->
            val tile=ui.column(3).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;minimumHeight=ui.dp(99);isClickable=true;isFocusable=true;contentDescription=if(item.folder)tr("文件夹：%1\$s", item.title) else item.title}
            val badge=FrameLayout(this).apply{background=ui.round(ui.soft,17)}
            if(item.folder){val mini=GridLayout(this).apply{columnCount=2;setPadding(ui.dp(6),ui.dp(6),ui.dp(6),ui.dp(6))};val children=store.home.filter{it.parent==item.id}.take(4)
                if(children.isEmpty())badge.addView(IconView(this,"folder",ui.accent),FrameLayout.LayoutParams(-1,-1))
                else{children.forEach{child->mini.addView(icons.view(ui,child.title,child.url,20),GridLayout.LayoutParams().apply{width=ui.dp(20);height=ui.dp(20)})};badge.addView(mini)}
            }else badge.addView(icons.view(ui,item.title,item.url,48),FrameLayout.LayoutParams(-1,-1))
            tile.addView(badge,LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));tile.addView(ui.label(item.title,12f).apply{maxLines=2;gravity=Gravity.CENTER;ellipsize=android.text.TextUtils.TruncateAt.END})
            tile.setOnClickListener{if(item.folder)showFolder(item.id)else openCollection(item.url)}
            DragSupport.source(tile,DragSupport.Item("home",item.id)){homeItemActions(item,tile)}
            DragSupport.target(tile,"home",{it.id!=item.id}){drag,x,_->
                val moving=store.home.find{it.id==drag.id}
                if(item.folder&&moving?.folder==false&&x>tile.width*.25f&&x<tile.width*.75f)moveHome(drag.id,item.id)
                else moveHome(drag.id,parent,item.id,x>=tile.width/2f)
            }
            grid.addView(tile,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})
        }
        val add=ui.column(3).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;isFocusable=true;contentDescription=tr("添加网站或文件夹");setOnClickListener{addHomeChoice(parent)}}
        add.addView(IconView(this,"plus",ui.accent).apply{background=ui.round(ui.soft,17)},LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));add.addView(ui.label(tr("添加"),12f,ui.muted).apply{gravity=Gravity.CENTER})
        DragSupport.target(add,"home"){drag,_,_->moveHome(drag.id,parent)}
        grid.addView(add,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})
        repeat((grid.columnCount-grid.childCount%grid.columnCount)%grid.columnCount){grid.addView(View(this),GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})}
    }
    fun showFolder(id:String){
        val folder=store.home.find{it.id==id&&it.folder}?:return;folderOpen=id
        folderOverlay?.let(content::removeView)
        val overlay=FrameLayout(this).apply{setBackgroundColor(0x66000000);setOnClickListener{folderOpen="";renderHome()}}
        val wrap=ui.column(16).apply{background=ui.round(ui.panel,22);isClickable=true};val heading=ui.row();heading.addView(ui.title(folder.title),LinearLayout.LayoutParams(0,-2,1f));heading.addView(ui.icon("close",tr("关闭文件夹")){folderOpen="";renderHome()});wrap.addView(heading)
        val out=ui.label(tr("拖到这里移回主页"),14f,ui.accent).apply{gravity=Gravity.CENTER;minimumHeight=ui.dp(52);background=ui.round(ui.soft)};wrap.addView(out)
        DragSupport.target(out,"home"){drag,_,_->moveHome(drag.id,"")}
        wrap.addView(ui.label(tr("拖到文件夹中央可移入；拖到两侧可排序"),12f,ui.muted))
        val grid=GridLayout(this).apply{columnCount=3};val scroll=ScrollView(this).apply{addView(grid)};wrap.addView(scroll,LinearLayout.LayoutParams(-1,-2));populateHome(grid,id)
        overlay.addView(wrap,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER).apply{leftMargin=ui.dp(20);rightMargin=ui.dp(20)});content.addView(overlay,FrameLayout.LayoutParams(-1,-1));folderOverlay=overlay
        wrap.post{val max=content.height-ui.dp(60);if(wrap.height>max){wrap.layoutParams=wrap.layoutParams.apply{height=max};scroll.layoutParams=LinearLayout.LayoutParams(-1,0,1f)}}
    }
    fun addHomeChoice(parent:String=""){if(parent.isNotEmpty()){editHome(null,parent,false);return};panels.choose(tr("添加到主页"),listOf(tr("网站"),tr("文件夹"))){i->editHome(null,parent,i==1)}}
    fun editHome(item:HomeItem?,parent:String="",folder:Boolean=false,initialTitle:String="",initialUrl:String=""){
        ItemEditor.show(this,if(folder)tr("编辑主页文件夹")else tr("编辑主页网站"),item?.title?:initialTitle,if(folder)null else item?.url?:initialUrl){name,address->
            if(item==null)store.home.add(HomeItem(title=name,url=if(folder)""else address,parent=parent,folder=folder))else{item.title=name;item.url=if(folder)""else address}
            store.save();if(currentUrl=="about:home")renderHome();toast(tr("已保存"))
        }
    }
    private fun homeItemActions(item:HomeItem,anchor:View){
        PopupMenu(this,anchor).apply{
            if(!item.folder)CollectionActions.add(this@BrowserActivity,menu,item.url)
            menu.add(tr("编辑")).setOnMenuItemClickListener{editHome(item,item.parent,item.folder);true}
            menu.add(tr("删除")).setOnMenuItemClickListener{
                AlertDialog.Builder(this@BrowserActivity).setTitle(tr("删除 %1\$s？", item.title)).setMessage(if(item.folder)tr("文件夹内的网站会移回主页。")else tr("从主页移除此网站。")).setNegativeButton(tr("取消"),null).setPositiveButton(tr("删除")){_,_->if(item.folder)store.home.filter{it.parent==item.id}.forEach{it.parent=""};store.home.remove(item);store.save();renderHome()}.show();true
            };show()
        }
    }
}
