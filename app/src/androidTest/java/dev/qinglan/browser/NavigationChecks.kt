package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise native home -> existing WebView navigation, including actual visibility and pixels. */
object NavigationChecks {
    private class Probe(context:Context):WebView(context){
        var paused=false
        override fun onPause(){super.onPause();paused=true}
        override fun onResume(){super.onResume();paused=false}
    }
    fun run(r:Instrumentation){
        val result=Bundle();var passed=true;var activity:BrowserActivity?=null;var oldTab=0L;var testTab=0L
        fun main(run:()->Unit){var failure:Throwable?=null;r.runOnMainSync{try{run()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
        try {
            val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;activity=a
            var web:Probe?=null;var loaded=CountDownLatch(1);var expectedUrl="https://navigation-check.example.test/start"
            main {
                check(!a.isIncognito){"Run navigation checks outside private mode"}
                oldTab=a.current!!.id;a.newHome();testTab=a.current!!.id
                web=Probe(a).apply {
                    webViewClient=object:WebViewClient(){
                        override fun shouldInterceptRequest(v:WebView,request:WebResourceRequest):WebResourceResponse? {
                            if(request.url.host!="navigation-check.example.test")return null
                            val html="<html><head><meta name='viewport' content='width=device-width'><title>Navigation check</title></head><body style='margin:0;background:#187747;min-height:100vh'>Visible fixture</body></html>"
                            return WebResourceResponse("text/html","UTF-8",html.byteInputStream())
                        }
                        override fun onPageFinished(v:WebView,url:String){if(url==expectedUrl)loaded.countDown()}
                    }
                }
                a.current!!.web=web;a.open(expectedUrl)
            }
            check(loaded.await(15,TimeUnit.SECONDS)){"Initial fixture did not load"}
            repeat(3){iteration->
                loaded=CountDownLatch(1)
                main {
                    expectedUrl="https://navigation-check.example.test/reopen/$iteration"
                    a.goHome();check(web!!.paused){"Home did not pause the cached WebView"}
                    if(iteration==2)a.navigateInput(expectedUrl)else a.open(expectedUrl)
                    check(a.current!!.web===web){"Navigation replaced the cached WebView"}
                    check(!web!!.paused){"Opening from home left the visible WebView paused"}
                }
                check(loaded.await(15,TimeUnit.SECONDS)){"Reopened fixture did not load"}
                val visible=CountDownLatch(1);var state=""
                main{web!!.evaluateJavascript("document.visibilityState"){state=it;visible.countDown()}}
                check(visible.await(5,TimeUnit.SECONDS)&&state=="\"visible\""){"Loaded document remained hidden: $state"}
                val painted=CountDownLatch(1)
                main{web!!.postVisualStateCallback(iteration.toLong(),object:WebView.VisualStateCallback(){override fun onComplete(id:Long){painted.countDown()}})}
                check(painted.await(10,TimeUnit.SECONDS)){"Page did not become ready to draw"}
                r.waitForIdleSync()
                val pos=IntArray(2);var width=0;var height=0
                main{web!!.getLocationOnScreen(pos);width=web!!.width;height=web!!.height}
                // VisualStateCallback promises readiness for the next draw, not a presented frame.
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);var pixel=0;var rendered=false
                while(!rendered&&System.nanoTime()<deadline){
                    val bitmap=r.uiAutomation.takeScreenshot()?:error("Screenshot unavailable")
                    try {pixel=bitmap.getPixel(pos[0]+width/2,pos[1]+height/2);rendered=Color.green(pixel)>90&&Color.red(pixel)<60&&Color.blue(pixel)<100}finally{bitmap.recycle()}
                    if(!rendered)Thread.sleep(100)
                }
                check(rendered){"Reopened page rendered blank: ${Integer.toHexString(pixel)}"}
            }
            result.putString("stream","PASS: three home -> cached WebView navigations; resumed native lifecycle; visible document; actual rendered pixels, including address input.\n")
        } catch(e:Throwable){passed=false;result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}
        finally {activity?.let{a->main{
            a.tabs.find{it.id==testTab}?.let{tab->tab.web?.let{web->(web.parent as? android.view.ViewGroup)?.removeView(web);web.stopLoading();web.destroy()};a.tabs.remove(tab)}
            a.tabs.indexOfFirst{it.id==oldTab}.takeIf{it>=0}?.let(a::switchTab)
            a.persistSession();check(a.prefs.edit().commit()){"Could not flush restored session before instrumentation exits"}
        }}}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
