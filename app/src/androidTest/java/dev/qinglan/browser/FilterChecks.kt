package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic .test resources only. Does not change browser preferences or real-site data. */
object FilterChecks {
    fun run(r: Instrumentation) {
        var web: WebView?=null
        try {
            val psl=PublicSuffixes(r.targetContext.assets.open("public_suffix_list.dat").bufferedReader().use{it.readText()})
            check(psl.site("a.example.co.uk")=="example.co.uk"){"bundled public suffix list"}
            val engine=FilterEngine(listOf("||filters.example.test/blocked.js\nfilters.example.test###ad\nfilters.example.test##.sponsored\nfilters.example.test#@#.allowed"))
            val loaded=CountDownLatch(1);val session=FilterSession();session.start("https://filters.example.test/")
            val styleScript=r.targetContext.assets.open("filter-page.js").bufferedReader().use { it.readText() }
            r.runOnMainSync {
                web=WebView(r.targetContext).apply {
                    settings.javaScriptEnabled=true
                    WebViewCompat.addWebMessageListener(this,"qinglanFilter",setOf("*")) { _, msg, origin, _, reply -> if(msg.data=="styles")reply.postMessage(JSONArray(engine.selectors(origin.toString())).toString()) }
                    if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(this,styleScript,setOf("*"))
                    webViewClient=object:WebViewClient() {
                        override fun shouldInterceptRequest(v: WebView,q: WebResourceRequest): WebResourceResponse {
                            val url=q.url.toString();val rule=engine.blocked(FilterEngine.Request(url,session.page,mainFrame=q.isForMainFrame))
                            if(rule!=null){session.record(url,rule);return response("", "text/plain")}
                            val path=q.url.path.orEmpty()
                            return when(path) {
                                "/" -> response("<html><body><div id='ad'>advert</div><div class='allowed'>content</div><script src='/blocked.js'></script><script src='/allowed.js'></script><script>setTimeout(()=>{let e=document.createElement('div');e.className='sponsored';document.body.appendChild(e)},20)</script></body></html>","text/html")
                                "/blocked.js" -> response("window.adExecuted=true;","text/javascript")
                                "/allowed.js" -> response("window.contentExecuted=true;","text/javascript")
                                else -> response("","text/plain")
                            }
                        }
                        override fun onPageFinished(v: WebView,url: String){v.evaluateJavascript(styleScript,null);loaded.countDown()}
                    }
                    loadUrl(session.page)
                }
            }
            check(loaded.await(15,TimeUnit.SECONDS)){"load timeout"}
            val evaluated=CountDownLatch(1);var result=""
            r.runOnMainSync { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({web!!.evaluateJavascript("JSON.stringify([window.adExecuted===true,window.contentExecuted===true,getComputedStyle(document.querySelector('#ad')).display,getComputedStyle(document.querySelector('.sponsored')).display,getComputedStyle(document.querySelector('.allowed')).display])"){result=it;evaluated.countDown()}},500) }
            check(evaluated.await(10,TimeUnit.SECONDS)){"evaluate timeout"}
            val data=JSONArray(JSONTokener(result).nextValue() as String)
            check(!data.getBoolean(0)&&data.getBoolean(1)){"request filtering"}
            check(data.getString(2)=="none"&&data.getString(3)=="none"&&data.getString(4)!="none"){"cosmetic rules: $data"}
            check(session.count()==1&&session.logs().size==1){"request log"}
            session.start("https://filters.example.test/next");check(session.count()==0&&session.logs().isEmpty())
            r.finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: WebView request blocking, normal scripts, early CSS and dynamic elements, message origins, per-page logs.\n")})
        } catch(e: Throwable) { r.finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}) }
        finally { r.runOnMainSync { web?.destroy() } }
    }
    private fun response(body: String,mime: String)=WebResourceResponse(mime,"UTF-8",ByteArrayInputStream(body.toByteArray()))
}
