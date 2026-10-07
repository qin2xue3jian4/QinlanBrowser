package dev.qinglan.browser

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import java.net.URI
import java.util.WeakHashMap

/** Greasy Fork otherwise displays extension-install help before following its install link. */
class ScriptInstaller(private val a:BrowserActivity) {
    private val handles=WeakHashMap<WebView,ScriptHandler>()
    private val hosts=setOf("greasyfork.org","www.greasyfork.org","sleazyfork.org","www.sleazyfork.org","update.greasyfork.org","update.sleazyfork.org")
    private val origins=setOf("https://greasyfork.org","https://www.greasyfork.org","https://sleazyfork.org","https://www.sleazyfork.org")
    private val documentStart get()=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    @android.annotation.SuppressLint("RequiresFeature")
    fun attach(web:WebView) {
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return
        WebViewCompat.addWebMessageListener(web,"qinglanScriptInstall",origins){view,message,origin,main,_->
            if(!main||a.isDestroyed||a.current?.web!==view||!a.hasWindowFocus()||origin.scheme!="https"||origin.host !in hosts)return@addWebMessageListener
            if(message.type!=WebMessageCompat.TYPE_STRING)return@addWebMessageListener
            val url=message.data.orEmpty();if(url.length>8192||!ScriptNetwork.isInstallUrl(url))return@addWebMessageListener
            val uri=runCatching{URI(url)}.getOrNull()?:return@addWebMessageListener
            if(uri.scheme=="https"&&uri.host in hosts&&uri.port in listOf(-1,443))a.panels.scripts.fromUrl(url)
        }
        if(documentStart)handles[web]=WebViewCompat.addDocumentStartJavaScript(web,script(),origins)
    }
    fun finished(web:WebView){if(!documentStart&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))web.evaluateJavascript(script(),null)}
    fun detach(web:WebView){handles.remove(web)?.remove()}
    private fun script()="""
        (()=>{
          if(window.top!==window||!window.qinglanScriptInstall||window[Symbol.for('qinglan.install.hook')])return;
          window[Symbol.for('qinglan.install.hook')]=true;
          const post=qinglanScriptInstall.postMessage.bind(qinglanScriptInstall);
          window.addEventListener('click',event=>{
            if(!event.isTrusted)return;const link=event.target.closest&&event.target.closest('a[href]');if(!link)return;
            let url;try{url=new URL(link.href);}catch(e){return;}
            if(url.protocol!=='https:'||!['greasyfork.org','www.greasyfork.org','sleazyfork.org','www.sleazyfork.org','update.greasyfork.org','update.sleazyfork.org'].includes(url.hostname)||!url.pathname.endsWith('.user.js'))return;
            event.preventDefault();event.stopImmediatePropagation();post(url.href);
          },true);
        })();
    """.trimIndent()
}
