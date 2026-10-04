@file:Suppress("DEPRECATION")
package dev.qinglan.browser

import android.app.Activity
import android.app.AlertDialog
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

data class BrowserTab(val id:Long=System.nanoTime(),var url:String="about:home",var title:String="主页",var web:WebView?=null,var saved:Bundle?=null,var used:Long=0,var committedUrl:String="about:home")

class BrowserActivity:Activity() {
    lateinit var store:BrowserStore
    lateinit var ui:Ui
    lateinit var panels:BrowserPanels
    lateinit var root:LinearLayout
    lateinit var content:FrameLayout
    lateinit var address:EditText
    lateinit var top:LinearLayout
    lateinit var bottom:LinearLayout
    lateinit var progress:ProgressBar
    lateinit var tabCount:TextView
    private lateinit var refreshButton:View
    val tabs=mutableListOf<BrowserTab>()
    var selected=0
    var fullScreen=false
    private var customView:View?=null
    private var customViewCallback:WebChromeClient.CustomViewCallback?=null
    private var uploadCallback:ValueCallback<Array<Uri>>?=null
    private var documentRead:((String)->Unit)?=null
    private var documentWrite:String?=null
    private var findBar:LinearLayout?=null
    private var folderOpen:String=""
    private var folderDialog:android.app.Dialog?=null
    val current get()=tabs.getOrNull(selected)
    val currentUrl get()=current?.url.orEmpty()
    val prefs get()=store.prefs

    override fun onCreate(savedInstanceState:Bundle?) {
        store=BrowserStore(this)
        ui=Ui(this,isDark());setTheme(if(ui.dark)R.style.AppThemeDark else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        panels=BrowserPanels(this)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        buildChrome()
        if(prefs.getBoolean("restore",true))runCatching{
            val a=JSONArray(prefs.getString("tabs","[]"));for(i in 0 until minOf(a.length(),50)){val o=a.getJSONObject(i);val url=o.optString("url","about:home");if(url=="about:home"||isHttp(url))tabs.add(BrowserTab(url=url,title=o.optString("title","网页")))}
            selected=prefs.getInt("selected",0).coerceIn(0,maxOf(0,tabs.lastIndex))
        }
        if(tabs.isEmpty())tabs.add(BrowserTab())
        switchTab(selected)
        if(intent.action==Intent.ACTION_VIEW)intent.dataString?.takeIf(::isHttp)?.let { open(it,true) }
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){goBack()}
    }
    fun isDark():Boolean=when(store.prefs.getString("theme","system")){"dark"->true;"light"->false;else->resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);if(isDark()!=ui.dark)retheme()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);intent.dataString?.takeIf(::isHttp)?.let{open(it,true)}}
    fun buildChrome() {
        root=ui.column();root.setBackgroundColor(ui.bg)
        top=ui.row().apply{setPadding(ui.dp(8),ui.dp(5),ui.dp(8),ui.dp(5));setBackgroundColor(ui.panel)}
        val addressBox=ui.row().apply{background=ui.round(ui.soft,15)}
        addressBox.addView(ui.icon("site","网站设置"){panels.site()})
        address=ui.edit("搜索或输入网址").apply{
            setPadding(0,0,0,0);background=null;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSelectAllOnFocus(true)
            setOnFocusChangeListener{_,focused->if(focused)setText(currentUrl.takeIf{isHttp(it)}.orEmpty()) else syncAddress()}
            setOnEditorActionListener{_,action,event->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO||event?.keyCode==KeyEvent.KEYCODE_ENTER&&event.action==KeyEvent.ACTION_UP){navigateInput(text.toString());true}else false}
        }
        addressBox.addView(address,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        refreshButton=ui.icon("refresh","刷新页面"){reload()};addressBox.addView(refreshButton)
        top.addView(addressBox,LinearLayout.LayoutParams(-1,-2))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE;progressTintList=android.content.res.ColorStateList.valueOf(ui.accent)}
        content=FrameLayout(this).apply{isFocusableInTouchMode=true}
        bottom=ui.row().apply{setBackgroundColor(ui.panel);setPadding(ui.dp(7),ui.dp(3),ui.dp(7),ui.dp(3))}
        fun nav(name:String,label:String,run:()->Unit){bottom.addView(ui.icon(name,label,run),LinearLayout.LayoutParams(0,ui.dp(48),1f))}
        nav("back","后退"){goBack()};nav("forward","前进"){current?.web?.takeIf{it.canGoForward()}?.goForward()};nav("home","主页"){goHome()}
        val tabButton=FrameLayout(this).apply{contentDescription="标签页";isClickable=true;isFocusable=true;setOnClickListener{panels.tabs()}}
        tabCount=ui.label("1",15f,ui.accent).apply{gravity=Gravity.CENTER;background=ui.round(ui.soft,8)}
        tabButton.addView(tabCount,FrameLayout.LayoutParams(ui.dp(28),ui.dp(30),Gravity.CENTER));bottom.addView(tabButton,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        nav("menu","菜单"){panels.menu()}
        if(prefs.getBoolean("bottomAddress",false)){root.addView(progress,LinearLayout.LayoutParams(-1,ui.dp(2)));root.addView(content,LinearLayout.LayoutParams(-1,0,1f));root.addView(top)}
        else{root.addView(top);root.addView(progress,LinearLayout.LayoutParams(-1,ui.dp(2)));root.addView(content,LinearLayout.LayoutParams(-1,0,1f))}
        root.addView(bottom);setContentView(root);content.requestFocus()
        if(Build.VERSION.SDK_INT>=30){
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener{view,insets->val bars=insets.getInsets(WindowInsets.Type.systemBars());val ime=insets.getInsets(WindowInsets.Type.ime());view.setPadding(bars.left,if(fullScreen)0 else bars.top,bars.right,maxOf(if(fullScreen)0 else bars.bottom,ime.bottom));insets}
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
        val v=input.trim();if(v.isBlank())return
        val url=when {isHttp(v)->v;v.contains(' ')||(!v.contains('.')&&!v.startsWith("localhost"))->searchUrl(v);else->"https://$v"}
        if(!isHttp(url)){toast("请输入有效的网址");return}
        address.clearFocus();content.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0);open(url)
    }
    fun searchUrl(query:String):String {
        val template=prefs.getString("search","https://www.bing.com/search?q=%s")!!
        return template.replace("%s",URLEncoder.encode(query,"UTF-8"))
    }
    fun open(url:String,newTab:Boolean=false){
        if(!isHttp(url)){toast("只支持 HTTP / HTTPS 网页");return}
        if(newTab){if(tabs.size>=50){toast("最多打开 50 个标签页");return};tabs.add(BrowserTab(url=url));switchTab(tabs.lastIndex);return}
        val t=current?:return;t.url=url;folderOpen="";if(t.web==null){t.saved=null;attach(t)}else{configure(t.web!!,url);content.removeAllViews();(t.web!!.parent as? android.view.ViewGroup)?.removeView(t.web);content.addView(t.web,FrameLayout.LayoutParams(-1,-1));t.web!!.loadUrl(url)}
        syncAddress();persistSession()
    }
    fun goHome(){val t=current?:return;t.web?.stopLoading();t.url="about:home";t.committedUrl="about:home";t.title="主页";folderOpen="";closeFind();renderHome();syncAddress();persistSession()}
    fun switchTab(index:Int){
        if(index !in tabs.indices)return;closeFind();current?.web?.onPause();selected=index;val t=tabs[index];t.used=System.currentTimeMillis()
        if(t.url=="about:home")renderHome()else attach(t)
        tabCount.text=tabs.size.toString();syncAddress();progress.visibility=View.GONE;persistSession();trimTabs()
    }
    fun closeTab(id:Long){val i=tabs.indexOfFirst{it.id==id};if(i<0)return;val t=tabs.removeAt(i);(t.web?.parent as? android.view.ViewGroup)?.removeView(t.web);t.web?.destroy();if(i<selected)selected--;if(tabs.isEmpty())tabs.add(BrowserTab());switchTab(selected.coerceIn(0,tabs.lastIndex))}
    fun newHome(){if(tabs.size>=50){toast("最多 50 个标签页");return};tabs.add(BrowserTab());switchTab(tabs.lastIndex)}
    private fun trimTabs(){tabs.filter{it!=current&&it.web!=null}.sortedByDescending{it.used}.drop(3).forEach{t->t.saved=Bundle().also{t.web?.saveState(it)};t.web?.destroy();t.web=null}}
    private fun attach(t:BrowserTab){
        content.removeAllViews()
        if(t.web==null){val web=createWeb(t);t.web=web;configure(web,t.url);val restored=t.saved?.let{web.restoreState(it)};t.saved=null;if(restored==null)web.loadUrl(t.url)}
        (t.web!!.parent as? android.view.ViewGroup)?.removeView(t.web);content.addView(t.web,FrameLayout.LayoutParams(-1,-1));t.web!!.onResume()
    }
    fun configure(web:WebView,url:String){
        val desktop=store.siteBool(url,"desktop",false)
        web.settings.apply{
            javaScriptEnabled=store.siteBool(url,"js",true);domStorageEnabled=true
            allowFileAccess=false;allowContentAccess=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true);builtInZoomControls=true;displayZoomControls=false;useWideViewPort=true;loadWithOverviewMode=true
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture=true;loadsImagesAutomatically=!store.siteBool(url,"noImages",prefs.getBoolean("noImages",false));blockNetworkImage=!loadsImagesAutomatically
            textZoom=prefs.getInt("textZoom",100)
            val ua=if(desktop)"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${WebView.getCurrentWebViewPackage()?.versionName?:"130.0.0.0"} Safari/537.36" else WebSettings.getDefaultUserAgent(this@BrowserActivity)
            if(userAgentString!=ua)userAgentString=ua
        }
        val thirdParty=store.siteBool(url,"thirdParty",prefs.getBoolean("thirdParty",false))
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,thirdParty)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings,ui.dark&&store.siteBool(url,"dark",true))
        web.setBackgroundColor(ui.bg)
    }
    private fun createWeb(tab:BrowserTab):WebView=WebView(this).apply {
        setDownloadListener{url,ua,disposition,mime,_->
            // A download is not a committed page: don't restore/re-download it on launch.
            tab.url=tab.committedUrl
            if(tab==current){syncAddress();if(tab.url=="about:home")renderHome()}
            persistSession();requestDownload(url,ua,disposition,mime)
        }
        setOnLongClickListener {
            val h=hitTestResult;val u=h.extra
            if(!u.isNullOrBlank()&&h.type in listOf(WebView.HitTestResult.SRC_ANCHOR_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,WebView.HitTestResult.IMAGE_TYPE)){
                val options=if(h.type==WebView.HitTestResult.IMAGE_TYPE)arrayOf("查看图片","保存图片","复制链接")else arrayOf("在新标签页打开","添加到书签","复制链接")
                AlertDialog.Builder(this@BrowserActivity).setTitle(u.take(120)).setItems(options){_,i->when(i){0->open(u,true);1->if(h.type==WebView.HitTestResult.IMAGE_TYPE)requestDownload(u,settings.userAgentString,"","")else{store.bookmarks.add(Visit(u,u));store.save();toast("已添加书签")};2->copy("链接",u)}}.show();true
            }else false
        }
        webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                val url=request.url.toString()
                if(isHttp(url)){if(request.isForMainFrame){tab.url=url;configure(view,url)};return false}
                if(request.isForMainFrame)openExternal(url);return true
            }
            override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?){if(tab.url=="about:home"&&url=="about:blank")return;tab.url=url;if(tab==current){syncAddress();this@BrowserActivity.progress.visibility=View.VISIBLE}}
            override fun onPageFinished(view:WebView,url:String){
                if(tab.url=="about:home")return;tab.url=url;tab.committedUrl=url;tab.title=view.title?.takeIf{it.isNotBlank()}?:Uri.parse(url).host?:"网页"
                if(tab==current){syncAddress();this@BrowserActivity.progress.visibility=View.GONE};store.visit(tab.title,url);CookieManager.getInstance().flush();persistSession()
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();if(tab==current)toast("证书验证失败，已停止加载")}
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame&&tab==current){this@BrowserActivity.progress.visibility=View.GONE;toast("页面加载失败：${error.description}")}}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean{(view.parent as? android.view.ViewGroup)?.removeView(view);view.destroy();tab.web=null;tab.saved=null;if(tab==current){toast("网页进程已退出，重新加载中");attach(tab)};return true}
        }
        webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView,value:Int){if(tab==current){this@BrowserActivity.progress.progress=value;this@BrowserActivity.progress.visibility=if(value==100)View.GONE else View.VISIBLE}}
            override fun onReceivedTitle(view:WebView,title:String?){if(tab.url!="about:home")tab.title=title?:tab.title}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean{
                if(!isUserGesture||tabs.size>=50)return false
                val t=BrowserTab(url="about:blank",title="新页面");val web=createWeb(t);t.web=web;configure(web,tab.url);tabs.add(t)
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
            override fun onPermissionRequest(request:PermissionRequest){request.deny();toast("首版暂不开放网页摄像头和麦克风；可使用文件上传")}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false)}
        }
    }
    fun hideVideo(){customView?.let{(it.parent as? android.view.ViewGroup)?.removeView(it)};customView=null;customViewCallback?.onCustomViewHidden();customViewCallback=null;setFullscreen(false)}
    fun goBack(){when{customView!=null->hideVideo();fullScreen->setFullscreen(false);findBar!=null->closeFind();folderOpen.isNotEmpty()->{folderOpen="";renderHome()};currentUrl=="about:home"->moveTaskToBack(true);current?.web?.canGoBack()==true->current!!.web!!.goBack();else->goHome()}}
    // API 33+ uses the native OnBackInvokedDispatcher registered in onCreate.
    // This override is only the legacy Android 8-12 path.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed(){goBack()}
    fun reload(){if(currentUrl=="about:home")renderHome()else current?.web?.let{configure(it,currentUrl);it.reload()}}
    private fun syncAddress(){if(!address.hasFocus())address.setText(if(isHttp(currentUrl))currentUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')else "")}
    fun setFullscreen(enabled:Boolean){fullScreen=enabled;top.visibility=if(enabled)View.GONE else View.VISIBLE;bottom.visibility=if(enabled)View.GONE else View.VISIBLE
        if(Build.VERSION.SDK_INT>=30){window.insetsController?.apply{systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;if(enabled)hide(WindowInsets.Type.systemBars())else show(WindowInsets.Type.systemBars())};root.requestApplyInsets()}
        else window.decorView.systemUiVisibility=if(enabled)View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else if(ui.dark)0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if(enabled)toast("按系统返回键退出全屏")
    }
    fun showFind(){val web=current?.web?.takeIf{currentUrl!="about:home"}?:return;closeFind();val bar=ui.row();val input=ui.edit("在页面中查找");val status=ui.label("0/0",12f);bar.addView(input,LinearLayout.LayoutParams(0,ui.dp(48),1f));bar.addView(status)
        bar.addView(ui.icon("back","上一个匹配"){web.findNext(false)});bar.addView(ui.icon("forward","下一个匹配"){web.findNext(true)});bar.addView(ui.icon("close","关闭查找"){closeFind()})
        input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){web.findAllAsync(s.toString())};override fun afterTextChanged(s:android.text.Editable?){} })
        web.setFindListener{active,total,done->if(done)status.text="${if(total==0)0 else active+1}/$total"};root.addView(bar,root.indexOfChild(content));findBar=bar;input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
    }
    private fun closeFind(){findBar?.let{root.removeView(it);current?.web?.clearMatches();current?.web?.setFindListener(null)};findBar=null}
    private fun openExternal(url:String){
        if(url.startsWith("javascript:")||url.startsWith("file:")||url.startsWith("content:")||url.startsWith("data:"))return
        val external=runCatching{if(url.startsWith("intent:"))Intent.parseUri(url,Intent.URI_INTENT_SCHEME).apply{component=null;selector=null;addCategory(Intent.CATEGORY_BROWSABLE)}else Intent(Intent.ACTION_VIEW,Uri.parse(url))}.getOrNull()?:return
        AlertDialog.Builder(this).setTitle("打开外部应用？").setMessage(Uri.parse(url).scheme?:"外部链接").setNegativeButton("取消",null).setPositiveButton("打开"){_,_->try{startActivity(external)}catch(e:Exception){external.getStringExtra("browser_fallback_url")?.takeIf(::isHttp)?.let{open(it)}?:toast("未找到对应应用")}}.show()
    }
    fun requestDownload(url:String,ua:String,disposition:String,mime:String){
        if(!isHttp(url)){toast("首版支持 HTTP/HTTPS 下载，暂不支持 Blob 下载");return}
        val guessed=URLUtil.guessFileName(url,disposition,mime).replace(Regex("[\\\\/:*?\"<>|]"),"_")
        val name=ui.edit("文件名",guessed)
        AlertDialog.Builder(this).setTitle("下载文件").setView(name).setNegativeButton("取消",null).setPositiveButton("下载"){_,_->
            val fileName=name.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"),"_").take(180).ifBlank{"download"}
            try{
                val req=DownloadManager.Request(Uri.parse(url)).setTitle(fileName).setDescription(Uri.parse(url).host).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,fileName)
                if(mime.isNotBlank())req.setMimeType(mime);req.addRequestHeader("User-Agent",ua)
                CookieManager.getInstance().getCookie(url)?.let{req.addRequestHeader("Cookie",it)}
                if(isHttp(currentUrl))req.addRequestHeader("Referer",currentUrl)
                val id=(getSystemService(DOWNLOAD_SERVICE)as DownloadManager).enqueue(req)
                val ids=prefs.getStringSet("downloads",emptySet())!!.toMutableSet();ids.add(id.toString());prefs.edit().putStringSet("downloads",ids).apply();toast("已开始下载")
            }catch(e:Exception){toast("下载失败：${e.message}")}
        }.show()
    }
    fun readDocument(mime:String="*/*",callback:(String)->Unit){documentRead=callback;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime),101)}
    fun writeDocument(name:String,mime:String,text:String){documentWrite=text;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),102)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        when(requestCode){
            101->{val callback=documentRead;documentRead=null;if(resultCode==RESULT_OK)data?.data?.let{uri->try{val bytes=contentResolver.openInputStream(uri)?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=1_048_576){"文件超过 1 MB"};out.write(buffer,0,n)};out.toByteArray()}?:throw Exception("读取失败");callback?.invoke(bytes.toString(Charsets.UTF_8))}catch(e:Exception){toast(e.message?:"读取失败")}}}
            102->{val text=documentWrite;documentWrite=null;if(resultCode==RESULT_OK&&text!=null)data?.data?.let{uri->try{contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray())}?:throw Exception("无法写入");toast("已导出")}catch(e:Exception){toast("导出失败")}}}
            103->{val result=if(resultCode==RESULT_OK){data?.clipData?.let{c->Array(c.itemCount){c.getItemAt(it).uri}}?:data?.data?.let{arrayOf(it)}}else null;uploadCallback?.onReceiveValue(result);uploadCallback=null}
        }
    }
    fun persistSession(){val a=JSONArray();tabs.forEach{a.put(JSONObject().put("url",it.url).put("title",it.title))};prefs.edit().putString("tabs",a.toString()).putInt("selected",selected).apply()}
    override fun onPause(){super.onPause();current?.web?.onPause();persistSession();CookieManager.getInstance().flush()}
    override fun onResume(){super.onResume();current?.web?.onResume()}
    override fun onDestroy(){uploadCallback?.onReceiveValue(null);tabs.forEach{t->(t.web?.parent as? android.view.ViewGroup)?.removeView(t.web);t.web?.destroy()};super.onDestroy()}
    fun toast(message:String){Toast.makeText(this,message,Toast.LENGTH_SHORT).show()}
    fun copy(label:String,text:String){(getSystemService(CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label,text));toast("已复制")}

    fun renderHome(){
        content.removeAllViews();val scroll=ScrollView(this);val column=ui.column(20);scroll.addView(column);content.addView(scroll)
        val heading=ui.row();heading.setPadding(0,ui.dp(40),0,ui.dp(25));val names=ui.column();names.addView(ui.title("清岚"));names.addView(ui.label("把空间留给你喜欢的网站",13f,ui.muted));heading.addView(names,LinearLayout.LayoutParams(0,-2,1f));column.addView(heading)
        val grid=GridLayout(this).apply{columnCount=4};column.addView(grid,LinearLayout.LayoutParams(-1,-2));populateHome(grid,"")
        if(folderOpen.isNotEmpty())showFolder(folderOpen)
    }
    private fun populateHome(grid:GridLayout,parent:String){
        grid.removeAllViews()
        store.home.filter{it.parent==parent}.forEach{item->
            val tile=ui.column(3).apply{gravity=Gravity.CENTER;minimumHeight=ui.dp(99);isClickable=true;isFocusable=true;contentDescription=if(item.folder)"文件夹：${item.title}" else item.title}
            val badge=FrameLayout(this).apply{background=ui.round(ui.soft,17)}
            if(item.folder){val mini=GridLayout(this).apply{columnCount=2;setPadding(ui.dp(6),ui.dp(6),ui.dp(6),ui.dp(6))};val children=store.home.filter{it.parent==item.id}.take(4)
                if(children.isEmpty())badge.addView(IconView(this,"folder",ui.accent),FrameLayout.LayoutParams(-1,-1))
                else{children.forEach{child->mini.addView(ui.label(child.title.take(1),11f,ui.accent).apply{gravity=Gravity.CENTER},GridLayout.LayoutParams().apply{width=ui.dp(20);height=ui.dp(20)})};badge.addView(mini)}
            }else badge.addView(ui.label(item.title.take(1).uppercase(),24f,ui.accent).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
            tile.addView(badge,LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));tile.addView(ui.label(item.title,12f).apply{maxLines=2;gravity=Gravity.CENTER;ellipsize=android.text.TextUtils.TruncateAt.END})
            tile.setOnClickListener{if(item.folder)showFolder(item.id)else open(item.url)};tile.setOnLongClickListener{folderDialog?.dismiss();homeItemActions(item);true}
            grid.addView(tile,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})
        }
        val add=ui.column(3).apply{gravity=Gravity.CENTER;isFocusable=true;contentDescription="添加网站或文件夹";setOnClickListener{addHomeChoice(parent)}}
        add.addView(ui.icon("plus","添加网站或文件夹"){addHomeChoice(parent)}.apply{background=ui.round(ui.soft,17)},LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));add.addView(ui.label("添加",12f,ui.muted).apply{gravity=Gravity.CENTER})
        grid.addView(add,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})
        val empty=(grid.columnCount-grid.childCount%grid.columnCount)%grid.columnCount
        repeat(empty){grid.addView(View(this),GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=ui.dp(104)})}
    }
    fun showFolder(id:String){
        val folder=store.home.find{it.id==id&&it.folder}?:return;folderOpen=id
        val wrap=ui.column(18);wrap.addView(ui.title(folder.title));wrap.addView(ui.label("长按网站可编辑或移动",12f,ui.muted));val grid=GridLayout(this).apply{columnCount=3};wrap.addView(grid,LinearLayout.LayoutParams(-1,-2));populateHome(grid,id)
        folderDialog?.dismiss();val dialog=panels.dialog(wrap,false);folderDialog=dialog;dialog.setOnDismissListener{folderOpen="";folderDialog=null}
        wrap.scaleX=.92f;wrap.scaleY=.92f;wrap.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        val items=store.home.filter{it.parent==id}
        for(i in 0..items.size){val v=grid.getChildAt(i);v.setOnClickListener{dialog.dismiss();val item=items.getOrNull(i);if(item!=null)open(item.url)else editHome(null,id,false)}}
    }
    fun addHomeChoice(parent:String=""){if(parent.isNotEmpty()){folderDialog?.dismiss();editHome(null,parent,false);return};AlertDialog.Builder(this).setTitle("添加到主页").setItems(arrayOf("网站","文件夹")){_,i->editHome(null,parent,i==1)}.show()}
    fun editHome(item:HomeItem?,parent:String="",folder:Boolean=false,initialTitle:String="",initialUrl:String=""){
        val col=ui.column(20);val title=ui.edit(if(folder)"文件夹名称"else"网站名称",item?.title?:initialTitle);col.addView(title)
        val url=ui.edit("https://example.com",item?.url?:initialUrl);if(!folder)col.addView(url)
        val dialog=AlertDialog.Builder(this).setTitle(if(item==null)"添加${if(folder)"文件夹"else"网站"}"else"编辑${if(folder)"文件夹"else"网站"}").setView(col).setNegativeButton("取消",null).setPositiveButton("保存",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val name=title.text.toString().trim();var address=url.text.toString().trim();if(!folder&&!address.contains("://"))address="https://$address"
            if(name.isEmpty()){title.error="请输入名称";return@setOnClickListener};if(!folder&&!isHttp(address)){url.error="请输入 HTTP/HTTPS 网址";return@setOnClickListener}
            if(item==null)store.home.add(HomeItem(title=name,url=if(folder)""else address,parent=parent,folder=folder))else{item.title=name;item.url=if(folder)""else address}
            store.save();dialog.dismiss();if(currentUrl=="about:home"){folderOpen="";renderHome()};toast("已保存")
        }};dialog.show()
    }
    private fun homeItemActions(item:HomeItem){
        val actions=if(item.folder)arrayOf("重命名","向前移动","删除文件夹（网站移回主页）")else arrayOf("编辑","移动到文件夹 / 主页","向前移动","删除")
        AlertDialog.Builder(this).setTitle(item.title).setItems(actions){_,i->when{
            i==0->editHome(item,item.parent,item.folder)
            !item.folder&&i==1->{val folders=store.home.filter{it.folder};AlertDialog.Builder(this).setTitle("移动到").setItems((listOf("主页")+folders.map{it.title}).toTypedArray()){_,pos->item.parent=if(pos==0)""else folders[pos-1].id;store.save();folderOpen="";renderHome()}.show()}
            (item.folder&&i==1)||(!item.folder&&i==2)->{val index=store.home.indexOf(item);val prior=(index-1 downTo 0).firstOrNull{store.home[it].parent==item.parent};if(prior!=null)java.util.Collections.swap(store.home,index,prior);store.save();folderOpen="";renderHome()}
            else->AlertDialog.Builder(this).setTitle("删除 ${item.title}？").setNegativeButton("取消",null).setPositiveButton("删除"){_,_->if(item.folder)store.home.filter{it.parent==item.id}.forEach{it.parent=""};store.home.remove(item);store.save();folderOpen="";renderHome()}.show()
        }}.show()
    }
}
