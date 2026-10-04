package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.webkit.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs production observer code on synthetic media; never accesses real cookies or pages. */
object ResourceChecks {
    fun cleanup(r: Instrumentation) {
        val store=BrowserStore(r.targetContext);val prefs=store.prefs
        val fixture="http://127.0.0.1:8765/resources"
        val rules=prefs.getString("filterRules","").orEmpty().lineSequence().filterNot{it=="127.0.0.1###synthetic-banner"}.joinToString("\n").trim()
        val saved=org.json.JSONArray(prefs.getString("tabs","[]"));val tabs=org.json.JSONArray()
        for(i in 0 until saved.length())if(saved.getJSONObject(i).optString("url")!=fixture)tabs.put(saved.getJSONObject(i))
        val manager=r.targetContext.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val ids=prefs.getStringSet("downloads",emptySet()).orEmpty().toMutableSet()
        ids.toList().forEach { id -> manager.query(android.app.DownloadManager.Query().setFilterById(id.toLong()))?.use { c ->
            if(c.moveToFirst()&&c.getString(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_URI))=="http://127.0.0.1:8765/protected-tone.wav") { manager.remove(id.toLong());ids.remove(id) }
        } }
        prefs.edit().putString("filterRules",rules).putString("tabs",tabs.toString()).putInt("selected",prefs.getInt("selected",0).coerceIn(0,maxOf(0,tabs.length()-1))).putStringSet("downloads",ids).commit()
        store.history.removeAll{it.url==fixture};store.save()
        r.finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Cleaned synthetic resource tab, rule, visit and test download only.\n")})
    }
    fun run(r: Instrumentation) {
        var web: WebView?=null
        val prefs=r.context.getSharedPreferences("resource-checks",0)
        prefs.edit().clear().putBoolean("resourceSniffing",true).commit()
        try {
            val sniffer=ResourceSniffer(r.targetContext,prefs);val session=ResourceSession();session.start("https://resources.example.test/","Resource Test UA")
            val loaded=CountDownLatch(1)
            r.runOnMainSync { web=WebView(r.targetContext).apply {
                settings.javaScriptEnabled=true;sniffer.attach(this,session)
                webViewClient=object:WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView,q: WebResourceRequest): WebResourceResponse {
                        sniffer.observe(session,q)
                        return when(q.url.path) {
                            "/" -> response("""<html><body><video preload="metadata" src="/clip.mp4?token=synthetic"></video><img src="/photo.svg"><script>
                              window.qaFetch=false;window.qaXhr=false;
                              setTimeout(async()=>{
                                const r=await fetch('/extensionless');window.qaFetch=(await r.text())==='synthetic body';
                                const x=new XMLHttpRequest();x.open('GET','/manifest');x.onload=()=>{window.qaXhr=x.responseText==='synthetic body'};x.send();
                                const img=document.createElement('img');img.src='/dynamic.webp';document.body.appendChild(img);
                                const v=document.createElement('video');v.src=URL.createObjectURL(new Blob(['synthetic'],{type:'video/mp4'}));document.body.appendChild(v);
                                const iframe=document.createElement('iframe');iframe.src='https://frame.example.test/frame';document.body.appendChild(iframe);
                              },600);
                            </script></body></html>""","text/html")
                            "/extensionless" -> response("synthetic body","audio/mpeg")
                            "/manifest" -> response("synthetic body","application/vnd.apple.mpegurl")
                            "/frame" -> response("<html><body><audio src='/child.mp3'></audio></body></html>","text/html")
                            "/photo.svg" -> response("<svg xmlns='http://www.w3.org/2000/svg' width='1' height='1'/>","image/svg+xml")
                            else -> response("synthetic body",if(q.url.path?.endsWith("mp3")==true)"audio/mpeg" else "video/mp4")
                        }
                    }
                    override fun onPageFinished(v: WebView,url: String) { sniffer.scan(v,session);loaded.countDown() }
                }
                loadUrl(session.page)
            } }
            check(loaded.await(15,TimeUnit.SECONDS)){"load timeout"}
            val evaluated=CountDownLatch(1);var intact=""
            r.runOnMainSync { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({web!!.evaluateJavascript("window.qaFetch && window.qaXhr"){intact=it;evaluated.countDown()}},1800) }
            check(evaluated.await(10,TimeUnit.SECONDS)){"evaluate timeout"};check(intact=="true"){"fetch/XHR body changed"}
            val resources=session.list()
            check(resources.any{it.url.contains("/clip.mp4")&&it.kind==ResourceKind.VIDEO}){"video missing"}
            check(resources.any{it.url.endsWith("/extensionless")&&it.kind==ResourceKind.AUDIO}){"MIME-only audio missing"}
            check(resources.any{it.url.endsWith("/manifest")&&it.kind==ResourceKind.HLS}){"MIME-only HLS missing"}
            check(resources.any{it.url.endsWith("/dynamic.webp")}){"dynamic image missing"}
            check(resources.any{it.kind==ResourceKind.BLOB}){"Blob hint missing"}
            check(resources.any{it.url=="https://frame.example.test/child.mp3"&&it.referer=="https://frame.example.test/frame"}){"iframe/referrer missing"}
            check(resources.map{it.url}.distinct().size==resources.size){"duplicates"}
            prefs.edit().putBoolean("resourceSniffing",false).commit();session.clear()
            val scanned=CountDownLatch(1);r.runOnMainSync{sniffer.scan(web!!,session){scanned.countDown()}};check(scanned.await(5,TimeUnit.SECONDS))
            check(session.list().isEmpty()){"disabled collection"}
            r.finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: production observer, direct media, extensionless MIME, HLS, dynamic images, Blob hints, iframe referrer, deduplication, untouched fetch/XHR bodies, disable switch.\n")})
        } catch(e: Throwable) { r.finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}) }
        finally { r.runOnMainSync{web?.destroy()};prefs.edit().clear().commit() }
    }
    private fun response(body: String,mime: String)=WebResourceResponse(mime,"UTF-8",ByteArrayInputStream(body.toByteArray()))
}
