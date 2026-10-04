package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ImprovementChecks {
    fun run(r:Instrumentation){var activity:BrowserActivity?=null;var web:WebView?=null;val result=Bundle()
        try{
            val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;activity=a
            val loaded=CountDownLatch(1)
            r.runOnMainSync{web=WebView(a).apply{settings.javaScriptEnabled=true;webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){loaded.countDown()}};loadDataWithBaseURL("https://reader.example.test/","<html><title>Reader test</title><nav>NAV_EXCLUDE</nav><article><h1>FIRST_MARKER</h1>"+(1..30).joinToString(""){"<p>Paragraph $it: Long article content, useful text, retained in order for reading. "+"Readable prose with punctuation. ".repeat(8)+"</p>"}+"<p>LAST_MARKER</p><form><input value='PRIVATE_EXCLUDE'></form><script>window.original=42;</script></article><footer>FOOTER_EXCLUDE</footer></html>","text/html","UTF-8",null)}}
            check(loaded.await(15,TimeUnit.SECONDS)){"reader fixture load timeout"}
            fun eval(script:String):String{var out="";val latch=CountDownLatch(1);r.runOnMainSync{web!!.evaluateJavascript(script){out=it;latch.countDown()}};check(latch.await(15,TimeUnit.SECONDS)){"JS timeout"};return out}
            val before=eval("document.documentElement.outerHTML")
            val article=JSONObject(eval(a.panels.reader.extractionScript()));val text=article.getString("text")
            check(text.contains("FIRST_MARKER")&&text.contains("LAST_MARKER")&&text.contains("Paragraph 30")){"reader lost article ends"}
            check(!text.contains("NAV_EXCLUDE")&&!text.contains("PRIVATE_EXCLUDE")&&!text.contains("window.original")){"reader retained navigation/form/script"}
            check(before==eval("document.documentElement.outerHTML")){"reader mutated original document"}
            val old=a.prefs.all["site.reader.example.test.textZoom"]
            r.runOnMainSync{try{a.prefs.edit().putInt("site.reader.example.test.textZoom",135).apply();a.configure(web!!,"https://reader.example.test/");check(web!!.settings.textZoom==135);a.store.resetSite("https://reader.example.test/");a.configure(web!!,"https://reader.example.test/");check(web!!.settings.textZoom==a.prefs.getInt("textZoom",100))}finally{val edit=a.prefs.edit();if(old is Int)edit.putInt("site.reader.example.test.textZoom",old)else edit.remove("site.reader.example.test.textZoom");edit.apply()}}
            r.runOnMainSync{
                val oldId=a.current!!.id;val count=a.tabs.size
                a.newHome();val parent=a.current!!.id;a.newHome();val child=a.current!!;child.openerId=parent
                a.closeTab(child.id);check(a.current!!.id==parent){"close did not return to parent"}
                a.undoCloseTab();check(a.tabs.size==count+2&&a.current!!.openerId==parent){"undo did not restore position and parent"}
                a.closeTab(a.current!!.id);a.closeTab(parent);a.closedTabs.clear();a.switchTab(a.tabs.indexOfFirst{it.id==oldId});check(a.tabs.size==count)
            }
            result.putString("stream","PASS: real WebView Readability first/last paragraphs, excluded navigation/form/script, unchanged original DOM, site zoom/reset, tab parent return and undo.\n")
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n");r.finish(Activity.RESULT_CANCELED,result);return}
        finally{r.runOnMainSync{web?.destroy();activity?.finish()}}
        r.finish(Activity.RESULT_OK,result)
    }
}
