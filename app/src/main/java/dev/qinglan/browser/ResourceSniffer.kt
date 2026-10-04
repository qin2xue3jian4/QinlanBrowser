package dev.qinglan.browser

import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class ResourceSniffer(private val context: android.content.Context,private val prefs: android.content.SharedPreferences) {
    constructor(a: BrowserActivity):this(a,a.prefs)
    private val script by lazy { context.assets.open("resource-observer.js").bufferedReader().use { it.readText() } }
    private val snapshot by lazy { context.assets.open("resource-snapshot.js").bufferedReader().use { it.readText() } }
    fun enabled()=prefs.getBoolean("resourceSniffing",true)
    fun observe(session: ResourceSession, request: WebResourceRequest) {
        if(!enabled()||FilterEngine.host(session.page).isBlank()||request.isForMainFrame||request.method!="GET")return
        val epoch=session.epoch;val url=request.url.toString()
        val hint=request.requestHeaders.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest",true) }?.value.orEmpty()
        val kind=ResourceClassifier.classify(url,hint=hint) ?: return
        val referer=request.requestHeaders.entries.firstOrNull { it.key.equals("Referer",true) }?.value.orEmpty().ifBlank { session.page }
        session.add(epoch,WebResource(url,kind,referer=referer,userAgent=session.userAgent))
    }
    fun attach(web: WebView, session: ResourceSession) {
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return
        WebViewCompat.addWebMessageListener(web,"qinglanResources",setOf("*")) { _, message, origin, main, reply ->
            if(FilterEngine.host(origin.toString()).isBlank())return@addWebMessageListener
            val raw=message.data.orEmpty();if(raw.length>65_536)return@addWebMessageListener
            runCatching {
                val data=JSONObject(raw);val frame=data.optString("page")
                if(!ResourceClassifier.sameOrigin(frame,origin.toString()))return@runCatching
                if(main&&!ResourceClassifier.sameDocument(frame,session.page))return@runCatching
                if(data.optString("op")=="hello")reply.postMessage(JSONObject().put("epoch",session.epoch).put("enabled",enabled()).toString())
                else if(enabled()&&data.optString("op")=="resources"&&data.optLong("epoch",-1)==session.epoch) {
                    consume(session,session.epoch,data.optJSONArray("items") ?: JSONArray(),frame)
                }
            }
        }
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,script,setOf("*"))
    }
    private fun consume(session: ResourceSession,epoch: Long,items: JSONArray,frame: String) {
        for(i in 0 until minOf(items.length(),100)) {
            val item=items.optJSONObject(i) ?: continue
            val url=item.optString("url");val mime=item.optString("mime").take(120);val hint=item.optString("hint")
            val kind=ResourceClassifier.classify(url,mime,hint) ?: continue
            val source=when(item.optString("source")){"dom"->"页面元素";"fetch","xhr"->"页面接口";else->"加载记录"}
            session.add(epoch,WebResource(url,kind,mime,frame,session.userAgent,source))
        }
    }
    fun scan(web: WebView,session: ResourceSession,done: (() -> Unit)?=null) {
        if(!enabled()||FilterEngine.host(session.page).isBlank()){done?.invoke();return}
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))web.evaluateJavascript(script,null)
        val epoch=session.epoch;val page=session.page
        web.evaluateJavascript(snapshot) { raw ->
            runCatching { val text=JSONTokener(raw).nextValue() as? String ?: return@runCatching; consume(session,epoch,JSONArray(text),page) }
            done?.invoke()
        }
    }
}
