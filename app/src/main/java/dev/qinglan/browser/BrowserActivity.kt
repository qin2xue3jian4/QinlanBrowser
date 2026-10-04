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

data class BrowserTab(val id:Long=System.nanoTime(),var url:String="about:home",var title:String="主页",var web:WebView?=null,var saved:Bundle?=null,var used:Long=0,var committedUrl:String="about:home",var searchOverride:String?=null,var openerId:Long?=null,var error:String?=null,val filterSession:FilterSession=FilterSession(),val resources:ResourceSession=ResourceSession())

class BrowserActivity:Activity() {
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
    lateinit var root:LinearLayout
    lateinit var content:FrameLayout
    lateinit var address:EditText
    lateinit var top:LinearLayout
    lateinit var bottom:LinearLayout
    lateinit var progress:ProgressBar
    lateinit var tabCount:TextView
    private lateinit var refreshButton:View
    val tabs=mutableListOf<BrowserTab>()
    val closedTabs=ClosedTabs()
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

    override fun onCreate(savedInstanceState:Bundle?) {
        store=BrowserStore(this)
        ui=Ui(this,isDark());setTheme(if(ui.dark)R.style.AppThemeDark else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        icons=SiteIcons(this);speech=PageSpeech(this);scripts=UserScripts(this)
        filtering=AdFiltering(this);sniffer=ResourceSniffer(this)
        panels=BrowserPanels(this)
        filtering.subscriptions.update(automatic=true)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        buildChrome()
        if(prefs.getBoolean("restore",true))runCatching{
            val a=JSONArray(prefs.getString("tabs","[]"));for(i in 0 until minOf(a.length(),50)){val o=a.getJSONObject(i);val url=o.optString("url","about:home");if(url=="about:home"||isHttp(url))tabs.add(BrowserTab(url=url,title=o.optString("title","网页")))}
            selected=prefs.getInt("selected",0).coerceIn(0,maxOf(0,tabs.lastIndex))
        }
        if(tabs.isEmpty())tabs.add(BrowserTab())
        switchTab(selected)
        handleIntent(intent)
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){goBack()}
    }
    fun isDark():Boolean=when(store.prefs.getString("theme","system")){"dark"->true;"light"->false;else->resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);if(isDark()!=ui.dark)retheme()}
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
        addressBox.addView(ui.icon("site","网站设置"){panels.site()})
        address=ui.edit("搜索或输入网址",field=AddressField(this)).apply{
            setPadding(0,0,0,0);background=null;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setImeActionLabel("前往",android.view.inputmethod.EditorInfo.IME_ACTION_GO)
            setSelectAllOnFocus(true)
            setOnFocusChangeListener{_,focused->if(focused)setText(currentUrl.takeIf{isHttp(it)}.orEmpty()) else {suggestions.dismiss();syncAddress()};updateAddressAction()}
            setOnEditorActionListener{_,action,event->
                if(event?.keyCode==KeyEvent.KEYCODE_ENTER){if(event.action==KeyEvent.ACTION_UP)navigateInput(text.toString());true}
                else if(action in listOf(android.view.inputmethod.EditorInfo.IME_ACTION_GO,android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,android.view.inputmethod.EditorInfo.IME_ACTION_DONE)){navigateInput(text.toString());true}else false
            }
        }
        address.onChange{suggestions.update(it)}
        addressBox.addView(address,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        refreshButton=ui.icon("refresh","刷新网页"){when{address.hasFocus()->address.setText("");prefs.getString("toolbarAction","refresh")=="qr"->scanQr();progress.visibility==View.VISIBLE->{current?.web?.stopLoading();progress.visibility=View.GONE;updateAddressAction()};else->reload()}};addressBox.addView(refreshButton)
        top.addView(addressBox,LinearLayout.LayoutParams(-1,-2))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE;progressTintList=android.content.res.ColorStateList.valueOf(ui.accent)}
        content=FrameLayout(this).apply{isFocusableInTouchMode=true}
        bottom=ui.row().apply{setBackgroundColor(ui.panel);setPadding(ui.dp(7),ui.dp(3),ui.dp(7),ui.dp(3))}
        fun nav(name:String,label:String,run:()->Unit){bottom.addView(ui.icon(name,label,run),LinearLayout.LayoutParams(0,ui.dp(48),1f))}
        nav("back","后退"){goBack()};nav("forward","前进"){current?.web?.takeIf{it.canGoForward()}?.goForward()};nav("home","主页"){goHome()}
        val tabButton=FrameLayout(this).apply{contentDescription="标签页";isClickable=true;isFocusable=true;setOnClickListener{panels.tabs()}}
        tabButton.setOnLongClickListener{dismissTabs();newHome();true}
        tabCount=ui.label("1",15f,ui.accent).apply{gravity=Gravity.CENTER;background=ui.round(ui.soft,8)}
        tabButton.addView(tabCount,FrameLayout.LayoutParams(ui.dp(28),ui.dp(30),Gravity.CENTER));bottom.addView(tabButton,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        nav("menu","菜单"){panels.menu()}
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
        address.clearFocus();content.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
        dismissTabs();showAddress()
        if(!isHttp(url)){toast("只支持 HTTP / HTTPS 网页");return}
        if(newTab){if(tabs.size>=50){toast("最多打开 50 个标签页");return};tabs.add(BrowserTab(url=url,openerId=current?.id));switchTab(tabs.lastIndex);return}
        val t=current?:return;t.error=null;t.url=url;folderOpen="";if(t.web==null){t.saved=null;attach(t)}else{configure(t.web!!,url);content.removeAllViews();(t.web!!.parent as? android.view.ViewGroup)?.removeView(t.web);content.addView(t.web,FrameLayout.LayoutParams(-1,-1));t.web!!.loadUrl(url)}
        syncAddress();persistSession()
    }
    fun goHome(){dismissTabs();showAddress();val t=current?:return;t.web?.stopLoading();t.web?.onPause();t.error=null;t.url="about:home";t.committedUrl="about:home";t.title="主页";t.resources.start("about:home","");t.filterSession.start("about:home");folderOpen="";closeFind();renderHome();syncAddress();persistSession()}
    fun switchTab(index:Int){
        if(index !in tabs.indices)return;webPermissions.cancel();address.clearFocus();content.requestFocus();dismissTabs();showAddress();closeFind();current?.web?.onPause();selected=index;val t=tabs[index];t.used=System.currentTimeMillis()
        if(t.url=="about:home")renderHome()else attach(t)
        tabCount.text=tabs.size.toString();syncAddress();progress.visibility=View.GONE;persistSession();trimTabs()
    }
    fun openBackground(url:String){
        if(!isHttp(url)){toast("只支持 HTTP / HTTPS 网页");return}
        if(tabs.size>=50){toast("最多 50 个标签页");return}
        val t=BrowserTab(url=url,title=Uri.parse(url).host?:"网页",openerId=current?.id,used=System.currentTimeMillis())
        tabs.add(t);t.web=createWeb(t).also{configure(it,url);it.loadUrl(url);it.onPause()}
        tabCount.text=tabs.size.toString();persistSession();trimTabs();toast("已在后台打开")
    }
    fun closeTab(id:Long){
        val i=tabs.indexOfFirst{it.id==id};if(i<0)return;if(current?.id==id)webPermissions.cancel()
        val activeId=current?.id;val t=tabs.removeAt(i)
        if(isHttp(t.url)||t.url=="about:home")closedTabs.push(ClosedTab(t.url,t.title,i,t.openerId,t.searchOverride))
        (t.web?.parent as? android.view.ViewGroup)?.removeView(t.web);t.web?.destroy()
        if(tabs.isEmpty()){val home=BrowserTab();tabs.add(home);emptyReplacementId=home.id}
        val next=if(activeId!=id)tabs.indexOfFirst{it.id==activeId}else tabs.indexOfFirst{it.id==t.openerId}
        switchTab(if(next>=0)next else i.coerceAtMost(tabs.lastIndex))
    }
    fun closeOtherTabs(){val keep=current?:return;emptyReplacementId=null;val closing=tabs.withIndex().filter{it.value!==keep}
        closing.forEach{(index,t)->if(isHttp(t.url)||t.url=="about:home")closedTabs.push(ClosedTab(t.url,t.title,index,t.openerId,t.searchOverride));(t.web?.parent as? ViewGroup)?.removeView(t.web);t.web?.destroy()}
        tabs.removeAll{it!==keep};selected=0;switchTab(0)
    }
    fun undoCloseTab(){
        if(tabs.size>=50){toast("请先关闭一个标签");return}
        val closed=closedTabs.pop()?:run{toast("没有可恢复的标签");return}
        if(tabs.size==1&&tabs[0].id==emptyReplacementId&&tabs[0].url=="about:home"){tabs[0].web?.destroy();tabs.clear()}
        emptyReplacementId=null
        val index=closed.index.coerceIn(0,tabs.size)
        tabs.add(index,BrowserTab(url=closed.url,title=closed.title,openerId=closed.openerId,searchOverride=closed.searchOverride));switchTab(index)
    }
    fun clearHistoryData(){store.history.clear();store.save();closedTabs.clear();tabs.forEach{it.web?.clearHistory();it.saved=null}}
    fun newHome(){if(tabs.size>=50){toast("最多 50 个标签页");return};tabs.add(BrowserTab());switchTab(tabs.lastIndex)}
    internal fun trimTabs(){tabs.filter{it!=current&&it.web!=null}.sortedByDescending{it.used}.drop(prefs.getInt("activeWebViews",4).coerceIn(2,8)-1).forEach{t->t.saved=Bundle().also{t.web?.saveState(it)};t.web?.destroy();t.web=null}}
    private fun attach(t:BrowserTab){
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
            disabledActionModeMenuItems=WebSettings.MENU_ITEM_PROCESS_TEXT
            mediaPlaybackRequiresUserGesture=!store.siteBool(url,"autoplay",prefs.getBoolean("autoplay",false));loadsImagesAutomatically=!store.siteBool(url,"noImages",prefs.getBoolean("noImages",false));blockNetworkImage=!loadsImagesAutomatically
            textZoom=store.siteZoom(url)
            val ua=if(desktop)"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${WebView.getCurrentWebViewPackage()?.versionName?:"130.0.0.0"} Safari/537.36" else WebSettings.getDefaultUserAgent(this@BrowserActivity)
            if(userAgentString!=ua)userAgentString=ua
        }
        val thirdParty=store.siteBool(url,"thirdParty",prefs.getBoolean("thirdParty",false))
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,thirdParty)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings,ui.dark&&store.siteBool(url,"dark",prefs.getBoolean("webDark",true)))
        web.setBackgroundColor(ui.bg)
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // WebView handles clicks; this observer never consumes touch events.
    private fun createWeb(tab:BrowserTab):WebView=WebView(this).apply {
        scripts.attach(this)
        tab.filterSession.start(tab.url)
        tab.resources.start(tab.url,settings.userAgentString)
        sniffer.attach(this,tab.resources)
        filtering.attach(this,tab.filterSession)
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
            // A download is not a committed page: don't restore/re-download it on launch.
            tab.url=tab.committedUrl
            if(tab==current){syncAddress();if(tab.url=="about:home")renderHome()}
            persistSession();requestDownload(url,ua,disposition,mime,contentLength=length)
        }
        setOnLongClickListener {
            val h=hitTestResult;val u=h.extra
            if(!u.isNullOrBlank()&&h.type in listOf(WebView.HitTestResult.SRC_ANCHOR_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,WebView.HitTestResult.IMAGE_TYPE)){
                val isImage=h.type==WebView.HitTestResult.IMAGE_TYPE
                val handler=Handler(Looper.getMainLooper()){message->val link=if(isImage)u else message.data.getString("url")?:u;val text=message.data.getString("title").orEmpty()
                    if(current!==tab||tab.web!==this||!hasWindowFocus())return@Handler true
                    val col=ui.column(16);lateinit var sheet:Dialog
                    fun action(label:String,run:()->Unit){col.addView(ui.item(label){sheet.dismiss();run()})}
                    col.addView(ui.title(if(isImage)"图片操作"else"链接操作"))
                        col.addView(ui.label(link.take(200),12f,ui.muted))
                        action("在新标签页打开"){open(link,true)}
                        action("在后台打开"){openBackground(link)}
                        action(if(isImage)"保存图片"else"添加到书签"){if(isImage)requestDownload(link,settings.userAgentString,"","")else panels.library.collect(text.ifBlank{link},link)}
                        action("复制链接"){copy("链接",link)}
                        if(text.isNotBlank())action("复制文本"){copy("链接文本",text)}
                        action("添加到主页"){panels.library.collect(text.ifBlank{link},link,false,true)}
                    sheet=panels.dialog(col);true};requestFocusNodeHref(handler.obtainMessage());true
            }else false
        }
        webViewClient=object:WebViewClient(){
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                val blocked=filtering.intercept(tab.filterSession,request)
                if(blocked==null)sniffer.observe(tab.resources,request)
                return blocked
            }
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                val url=request.url.toString()
                if(isHttp(url)){if(request.isForMainFrame){tab.url=url;configure(view,url)};return false}
                if(request.isForMainFrame&&request.hasGesture()&&tab==current&&hasWindowFocus())openExternal(url);return true
            }
            override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?){if(tab.url=="about:home"){view.stopLoading();return};if(tab==current)webPermissions.cancel();tab.url=url;tab.error=null;configure(view,url);tab.filterSession.start(url);tab.resources.start(url,view.settings.userAgentString);if(tab==current){syncAddress();this@BrowserActivity.progress.visibility=View.VISIBLE}}
            override fun onPageFinished(view:WebView,url:String){
                scripts.finished(view)
                filtering.finished(view,tab.filterSession)
                sniffer.scan(view,tab.resources)
                if(tab.url=="about:home"||tab.error!=null)return;tab.url=url;tab.committedUrl=url;tab.title=view.title?.takeIf{it.isNotBlank()}?:Uri.parse(url).host?:"网页"
                if(tab==current){syncAddress();this@BrowserActivity.progress.visibility=View.GONE};if(prefs.getBoolean("recordHistory",true))store.visit(tab.title,url);CookieManager.getInstance().flush();persistSession()
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();if(error.url==view.url||error.url==tab.url){tab.error="证书验证失败，连接已停止。请检查网址及设备日期时间，或稍后重试。";if(tab==current)showPageError(tab)}else if(tab==current)toast("部分网页资源证书异常，已阻止加载")}
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame&&tab.url!="about:home"){tab.error=when(error.errorCode){ERROR_HOST_LOOKUP->"找不到这个网站，请检查网址或网络。";ERROR_CONNECT,ERROR_TIMEOUT->"连接失败或超时，请检查网络后重试。";else->"网页暂时无法加载：${error.description}"};if(tab==current)showPageError(tab)}}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean{(view.parent as? android.view.ViewGroup)?.removeView(view);view.destroy();tab.web=null;tab.saved=null;tab.error="网页进程已退出。点击重新加载可恢复；未提交的表单可能无法找回。";if(tab==current)showPageError(tab);return true}
        }
        webChromeClient=object:WebChromeClient(){
            override fun onReceivedIcon(view:WebView,icon:Bitmap){icons.save(view.url?:tab.url,icon)}
            override fun onProgressChanged(view:WebView,value:Int){if(tab==current){this@BrowserActivity.progress.progress=value;this@BrowserActivity.progress.visibility=if(value==100||tab.error!=null)View.GONE else View.VISIBLE;updateAddressAction()}}
            override fun onReceivedTitle(view:WebView,title:String?){if(tab.url!="about:home")tab.title=title?:tab.title}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean{
                if(!isUserGesture||tabs.size>=50)return false
                val t=BrowserTab(url="about:blank",title="新页面",openerId=tab.id);val web=createWeb(t);t.web=web;configure(web,tab.url);tabs.add(t)
                (resultMsg.obj as WebView.WebViewTransport).webView=web;resultMsg.sendToTarget();switchTab(tabs.lastIndex);return true
            }
            override fun onCloseWindow(window:WebView){tabs.find{it.web==window}?.let{closeTab(it.id)}}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean{
                uploadCallback?.onReceiveValue(null);uploadCallback=callback
                val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.mode==FileChooserParams.MODE_OPEN_MULTIPLE)
                val types=params.acceptTypes.filter{it.contains('/')};if(types.isNotEmpty())intent.putExtra(Intent.EXTRA_MIME_TYPES,types.toTypedArray())
                try{startActivityForResult(intent,103)}catch(e:Exception){uploadCallback?.onReceiveValue(null);uploadCallback=null;toast("无法打开文件选择器")};return true
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
    fun goBack(){when{tabsOverlay!=null->dismissTabs();customView!=null->hideVideo();fullScreen->setFullscreen(false);findBar!=null->closeFind();folderOpen.isNotEmpty()->{folderOpen="";renderHome()};currentUrl=="about:home"->moveTaskToBack(true);current?.web?.canGoBack()==true->{showAddress();current!!.web!!.goBack()};else->goHome()}}
    fun showAddress(){if(!fullScreen)top.visibility=View.VISIBLE;scrollTravel=0}
    fun dismissTabs():Boolean {val overlay=tabsOverlay?:return false;content.removeView(overlay);tabsOverlay=null;overlayKind="";return true}
    fun showTabs(view:View,kind:String="tabs"){
        dismissTabs();address.clearFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
        val overlay=FrameLayout(this).apply{setBackgroundColor(0x33000000);setOnClickListener{dismissTabs()}}
        val scroll=ScrollView(this).apply{setBackgroundColor(ui.panel);addView(view);isFillViewport=false;isClickable=true}
        overlay.addView(scroll,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));content.addView(overlay,FrameLayout.LayoutParams(-1,-1));tabsOverlay=overlay;overlayKind=kind
        scroll.post{if(scroll.height>content.height*3/4){scroll.layoutParams=(scroll.layoutParams as FrameLayout.LayoutParams).apply{height=content.height*3/4}}}
    }
    // API 33+ uses the native OnBackInvokedDispatcher registered in onCreate.
    // This override is only the legacy Android 8-12 path.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed(){goBack()}
    fun reload(){if(currentUrl=="about:home")renderHome()else current?.let{t->t.error=null;val existed=t.web!=null;attach(t);if(existed)t.web?.let{configure(it,currentUrl);it.reload()}}}
    private fun showPageError(tab:BrowserTab){
        if(tab!=current)return;progress.visibility=View.GONE;updateAddressAction();content.removeAllViews()
        val col=ui.column(24);col.addView(ui.title("网页暂时无法打开"));col.addView(ui.label(tab.error.orEmpty()));col.addView(ui.label(tab.url,13f,ui.muted).apply{setTextIsSelectable(true)})
        col.addView(ui.button("重新加载",true){reload()});col.addView(ui.button("编辑网址"){focusAddress()});col.addView(ui.button("返回主页"){goHome()});content.addView(ScrollView(this).apply{addView(col)})
    }
    fun focusAddress(){showAddress();address.requestFocus();address.selectAll();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT)}
    fun scanQr(){startActivityForResult(Intent(this,QrScanActivity::class.java),104)}
    fun openCollection(url:String){when(prefs.getString("collectionOpen","current")){"new"->open(url,true);"background"->openBackground(url);else->open(url)}}
    private fun updateAddressAction(){if(!::refreshButton.isInitialized||!::progress.isInitialized)return
        val state=when{address.hasFocus()->"close" to "清空地址";prefs.getString("toolbarAction","refresh")=="qr"->"qr" to "扫描二维码";progress.visibility==View.VISIBLE->"close" to "停止加载";else->"refresh" to "刷新网页"}
        (refreshButton as? IconView)?.setIcon(state.first);refreshButton.contentDescription=state.second
    }
    private fun syncAddress(){updateAddressAction();if(!address.hasFocus())address.setText(if(isHttp(currentUrl))currentUrl.removePrefix("https://").trimEnd('/')else "")}
    fun setFullscreen(enabled:Boolean){fullScreen=enabled;top.visibility=if(enabled)View.GONE else View.VISIBLE;bottom.visibility=if(enabled)View.GONE else View.VISIBLE
        if(Build.VERSION.SDK_INT>=28)window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=if(enabled){if(Build.VERSION.SDK_INT>=30)WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT}
        window.statusBarColor=if(enabled)android.graphics.Color.TRANSPARENT else ui.panel
        if(Build.VERSION.SDK_INT>=30){window.insetsController?.apply{systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;if(enabled)hide(WindowInsets.Type.systemBars())else show(WindowInsets.Type.systemBars())};root.requestApplyInsets()}
        else window.decorView.systemUiVisibility=if(enabled)View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else if(ui.dark)0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if(enabled)toast("按系统返回键退出全屏")
    }
    fun showFind(){val web=current?.web?.takeIf{currentUrl!="about:home"}?:return;closeFind();val bar=ui.row();val input=ui.edit("在页面中查找");val status=ui.label("0/0",12f);bar.addView(input,LinearLayout.LayoutParams(0,ui.dp(48),1f));bar.addView(status)
        bar.addView(ui.icon("back","上一个匹配"){web.findNext(false)});bar.addView(ui.icon("forward","下一个匹配"){web.findNext(true)});bar.addView(ui.icon("close","关闭查找"){closeFind()})
        input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){web.findAllAsync(s.toString())};override fun afterTextChanged(s:android.text.Editable?){} })
        web.setFindListener{active,total,done->if(done)status.text="${if(total==0)0 else active+1}/$total"};root.addView(bar,root.indexOfChild(content));findBar=bar;input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
    }
    private fun closeFind(){if(findBar==null)return;findBar?.let{root.removeView(it);current?.web?.clearMatches();current?.web?.setFindListener(null)};findBar=null;content.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)}
    private fun openExternal(url:String){
        if(prefs.getString("externalApps","ask")=="block"){toast("已阻止网页唤起外部应用");return}
        if(url.startsWith("javascript:")||url.startsWith("file:")||url.startsWith("content:")||url.startsWith("data:"))return
        val external=runCatching{if(url.startsWith("intent:"))Intent.parseUri(url,Intent.URI_INTENT_SCHEME).apply{component=null;selector=null;action=Intent.ACTION_VIEW;flags=0;addCategory(Intent.CATEGORY_BROWSABLE)}else Intent(Intent.ACTION_VIEW,Uri.parse(url))}.getOrNull()?:return
        AlertDialog.Builder(this).setTitle("打开外部应用？").setMessage(Uri.parse(url).scheme?:"外部链接").setNegativeButton("取消",null).setPositiveButton("打开"){_,_->try{startActivity(external)}catch(e:Exception){external.getStringExtra("browser_fallback_url")?.takeIf(::isHttp)?.let{open(it)}?:toast("未找到对应应用")}}.show()
    }
    fun requestDownload(url:String,ua:String,disposition:String,mime:String,referer:String=currentUrl,contentLength:Long=-1,suggestedName:String?=null){
        if(!isHttp(url)){toast("支持 HTTP/HTTPS 下载，Blob 地址不能直接下载");return}
        val guessed=(suggestedName?:URLUtil.guessFileName(url,disposition,mime)).replace(Regex("[\\\\/:*?\"<>|]"),"_")
        val name=ui.edit("文件名",guessed)
        val details=ui.column(16);details.addView(ui.label("来源：${Uri.parse(url).host.orEmpty()}\n类型：${mime.ifBlank{"未知"}}\n大小：${if(contentLength>=0)android.text.format.Formatter.formatFileSize(this,contentLength)else"未知（以下载结果为准）"}",13f,ui.muted));details.addView(name)
        AlertDialog.Builder(this).setTitle("下载文件").setView(details).setNegativeButton("取消",null).setPositiveButton("下载"){_,_->
            val fileName=name.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"),"_").take(180).ifBlank{"download"}
            try{
                val req=DownloadManager.Request(Uri.parse(url)).setTitle(fileName).setDescription(Uri.parse(url).host).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,fileName)
                if(prefs.getBoolean("downloadWifiOnly",false))req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                if(mime.isNotBlank())req.setMimeType(mime);req.addRequestHeader("User-Agent",ua)
                CookieManager.getInstance().getCookie(url)?.let{req.addRequestHeader("Cookie",it)}
                if(isHttp(referer))req.addRequestHeader("Referer",referer)
                val id=(getSystemService(DOWNLOAD_SERVICE)as DownloadManager).enqueue(req)
                val ids=prefs.getStringSet("downloads",emptySet())!!.toMutableSet();ids.add(id.toString());prefs.edit().putStringSet("downloads",ids).putString("download.$id.referer",referer).putString("download.$id.ua",ua).apply();toast("已开始下载")
            }catch(e:Exception){toast("下载失败：${e.message}")}
        }.show()
    }
    fun readDocument(mime:String="*/*",callback:(String)->Unit){documentRead=callback;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime),101)}
    fun writeDocument(name:String,mime:String,text:String){documentWrite=text;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),102)}
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,permissions,results);if(code==105)webPermissions.result()}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        when(requestCode){
            101->{val callback=documentRead;documentRead=null;if(resultCode==RESULT_OK)data?.data?.let{uri->try{val bytes=contentResolver.openInputStream(uri)?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2_097_152){"文件超过 2 MB"};out.write(buffer,0,n)};out.toByteArray()}?:throw Exception("读取失败");callback?.invoke(bytes.toString(Charsets.UTF_8))}catch(e:Exception){toast(e.message?:"读取失败")}}}
            102->{val text=documentWrite;documentWrite=null;if(resultCode==RESULT_OK&&text!=null)data?.data?.let{uri->try{contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray())}?:throw Exception("无法写入");toast("已导出")}catch(e:Exception){toast("导出失败")}}}
            103->{val result=if(resultCode==RESULT_OK){data?.clipData?.let{c->Array(c.itemCount){c.getItemAt(it).uri}}?:data?.data?.let{arrayOf(it)}}else null;uploadCallback?.onReceiveValue(result);uploadCallback=null}
            104->{if(resultCode==RESULT_OK)data?.getStringExtra("url")?.takeIf(::isHttp)?.let{open(it)}}
        }
    }
    fun persistSession(){val a=JSONArray();tabs.forEach{a.put(JSONObject().put("url",it.url).put("title",it.title))};prefs.edit().putString("tabs",a.toString()).putInt("selected",selected).apply()}
    override fun onPause(){suggestions.dismiss();super.onPause();speech.pause();current?.web?.onPause();persistSession();CookieManager.getInstance().flush()}
    override fun onStop(){webPermissions.cancel();super.onStop()}
    override fun onResume(){super.onResume();current?.web?.onResume()}
    override fun onDestroy(){webPermissions.cancel();suggestions.dismiss();panels.pages.close();panels.reader.close();speech.close();filtering.subscriptions.close();uploadCallback?.onReceiveValue(null);tabs.forEach{t->(t.web?.parent as? android.view.ViewGroup)?.removeView(t.web);t.web?.destroy()};super.onDestroy()}
    fun toast(message:String){Toast.makeText(this,message,Toast.LENGTH_SHORT).show()}
    fun copy(label:String,text:String){(getSystemService(CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label,text));toast("已复制")}

    fun renderHome(){
        content.removeAllViews();folderOverlay=null
        val scroll=ScrollView(this);val column=ui.column(20);scroll.addView(column);content.addView(scroll)
        val heading=ui.row();heading.setPadding(0,ui.dp(40),0,ui.dp(25));val names=ui.column();names.addView(ui.title("清岚"));heading.addView(names,LinearLayout.LayoutParams(0,-2,1f));if(prefs.getBoolean("homeTitle",true))column.addView(heading)
        val grid=GridLayout(this).apply{columnCount=prefs.getInt("homeColumns",4).coerceIn(3,5)};column.addView(grid,LinearLayout.LayoutParams(-1,-2));populateHome(grid,"")
        if(folderOpen.isNotEmpty())showFolder(folderOpen)
    }
    private fun moveHome(id:String,parent:String,target:String?=null,after:Boolean=false){
        LibraryOrder.home(store.home,id,parent,target,after);store.save();folderOpen=parent;renderHome()
    }
    private fun populateHome(grid:GridLayout,parent:String){
        grid.removeAllViews()
        store.home.filter{it.parent==parent}.forEach{item->
            val tile=ui.column(3).apply{gravity=Gravity.CENTER;minimumHeight=ui.dp(99);isClickable=true;isFocusable=true;contentDescription=if(item.folder)"文件夹：${item.title}" else item.title}
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
        val add=ui.column(3).apply{gravity=Gravity.CENTER;isFocusable=true;contentDescription="添加网站或文件夹";setOnClickListener{addHomeChoice(parent)}}
        add.addView(IconView(this,"plus",ui.accent).apply{background=ui.round(ui.soft,17)},LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));add.addView(ui.label("添加",12f,ui.muted).apply{gravity=Gravity.CENTER})
        DragSupport.target(add,"home"){drag,_,_->moveHome(drag.id,parent)}
        grid.addView(add,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})
        repeat((grid.columnCount-grid.childCount%grid.columnCount)%grid.columnCount){grid.addView(View(this),GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})}
    }
    fun showFolder(id:String){
        val folder=store.home.find{it.id==id&&it.folder}?:return;folderOpen=id
        folderOverlay?.let(content::removeView)
        val overlay=FrameLayout(this).apply{setBackgroundColor(0x66000000);setOnClickListener{folderOpen="";renderHome()}}
        val wrap=ui.column(16).apply{background=ui.round(ui.panel,22);isClickable=true};val heading=ui.row();heading.addView(ui.title(folder.title),LinearLayout.LayoutParams(0,-2,1f));heading.addView(ui.icon("close","关闭文件夹"){folderOpen="";renderHome()});wrap.addView(heading)
        val out=ui.label("拖到这里移回主页",14f,ui.accent).apply{gravity=Gravity.CENTER;minimumHeight=ui.dp(52);background=ui.round(ui.soft)};wrap.addView(out)
        DragSupport.target(out,"home"){drag,_,_->moveHome(drag.id,"")}
        wrap.addView(ui.label("拖到文件夹中央可移入；拖到两侧可排序",12f,ui.muted))
        val grid=GridLayout(this).apply{columnCount=3};val scroll=ScrollView(this).apply{addView(grid)};wrap.addView(scroll,LinearLayout.LayoutParams(-1,-2));populateHome(grid,id)
        overlay.addView(wrap,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER).apply{leftMargin=ui.dp(20);rightMargin=ui.dp(20)});content.addView(overlay,FrameLayout.LayoutParams(-1,-1));folderOverlay=overlay
        wrap.post{val max=content.height-ui.dp(60);if(wrap.height>max){wrap.layoutParams=wrap.layoutParams.apply{height=max};scroll.layoutParams=LinearLayout.LayoutParams(-1,0,1f)}}
    }
    fun addHomeChoice(parent:String=""){if(parent.isNotEmpty()){editHome(null,parent,false);return};panels.choose("添加到主页",listOf("网站","文件夹")){i->editHome(null,parent,i==1)}}
    fun editHome(item:HomeItem?,parent:String="",folder:Boolean=false,initialTitle:String="",initialUrl:String=""){
        ItemEditor.show(this,if(folder)"编辑主页文件夹"else"编辑主页网站",item?.title?:initialTitle,if(folder)null else item?.url?:initialUrl){name,address->
            if(item==null)store.home.add(HomeItem(title=name,url=if(folder)""else address,parent=parent,folder=folder))else{item.title=name;item.url=if(folder)""else address}
            store.save();if(currentUrl=="about:home")renderHome();toast("已保存")
        }
    }
    private fun homeItemActions(item:HomeItem,anchor:View){
        PopupMenu(this,anchor).apply{
            menu.add("编辑").setOnMenuItemClickListener{editHome(item,item.parent,item.folder);true}
            menu.add("删除").setOnMenuItemClickListener{
                AlertDialog.Builder(this@BrowserActivity).setTitle("删除 ${item.title}？").setMessage(if(item.folder)"文件夹内的网站会移回主页。"else"从主页移除此网站。").setNegativeButton("取消",null).setPositiveButton("删除"){_,_->if(item.folder)store.home.filter{it.parent==item.id}.forEach{it.parent=""};store.home.remove(item);store.save();renderHome()}.show();true
            };show()
        }
    }
}
