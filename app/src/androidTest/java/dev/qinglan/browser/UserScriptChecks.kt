package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object UserScriptChecks {
    fun run(r:Instrumentation) {
        var web:WebView?=null;var scripts:UserScripts?=null;val result=Bundle();var passed=false;var clipboardText="";var openedTab=false;var closedTab=false
        fun eval(code:String):String {val latch=CountDownLatch(1);var out="";r.runOnMainSync{web!!.evaluateJavascript(code){out=it;latch.countDown()}};check(latch.await(10,TimeUnit.SECONDS)){"Evaluation timed out"};return out}
        fun await(code:String){val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<deadline){if(eval(code)=="true")return;Thread.sleep(100)};error("Timed out: $code; state="+eval("JSON.stringify({early:window.qaEarly,storage:window.qaStorage,request:window.qaRequest,done:window.qaDone,frame:window.qaFrameMessage,error:window.qaError})"))}
        fun make(id:String,body:String,meta:String="",match:String="https://scripts.example.test/*")=UserScript(id,"// ==UserScript==\n// @name $id\n// @match $match\n$meta\n// ==/UserScript==\n$body",true)
        try {
            val primary=make("qa-common","""
                const clone='script-owned-helper';
                window.qaStart=dependencyValue===42;
                window.qaInitial=GM_getValue('persist',null);
                GM_addValueChangeListener('persist',(key,oldValue,newValue,remote)=>{if(!remote&&newValue.x===3)window.qaChange=true;});
                window.addEventListener('urlchange',()=>{window.qaUrlChange=true;});history.pushState(null,'','#qa-change');
                GM_addStyle('body { color: rgb(10, 20, 30); }');
                window.qaResource=GM_getResourceText('css')==='body{}'&&GM_getResourceURL('css').startsWith('data:');
                GM_registerMenuCommand('Synthetic command',()=>{window.qaMenu=true;});
                window.qaAfterAttach=async()=>{await GM.setValue('continued',true);window.qaContinued=GM_getValue('continued')===true;};
                try {
                  const tab=await GM.openInTab('https://opened.example.test/',{active:false});await tab.close();window.qaOpenedTab=tab.closed;await GM.setClipboard('synthetic-clipboard');window.qaClipboard=true;await GM.saveTab({number:7});window.qaTab=(await GM.getTab()).number===7&&Object.values(await GM.getTabs()).some(tab=>tab.number===7);
                  await GM.setValue('persist',{x:3});
                  window.qaStorage=GM_getValue('persist').x===3&&GM_listValues().includes('persist');
                  const request=GM.xmlHttpRequest({url:'http://127.0.0.1:8895/api',responseType:'json'});history.replaceState(null,'','?qa-spa=true#qa-change');const response=await request;
                  window.qaRequest=response.status===200&&response.response.synthetic===true&&response.response.number===42;
                  try {await GM.xmlHttpRequest({url:'https://outside.example.test/blocked'});}catch(e){window.qaBlocked=true;}
                  await GM.setValue('temporary',2);await GM.deleteValue('temporary');window.qaDelete=GM_getValue('temporary','missing')==='missing';
                  window.qaDone=true;
                } catch(e) {window.qaError=String(e);}
            """.trimIndent(),"// @match https://other.example.test/*\n// @run-at document-start\n// @grant window.onurlchange\n// @grant GM_openInTab\n// @grant GM_setClipboard\n// @grant GM_getTab\n// @grant GM_saveTab\n// @grant GM_getTabs\n// @grant GM_addValueChangeListener\n// @grant GM_setValue\n// @grant GM_getValue\n// @grant GM_deleteValue\n// @grant GM_listValues\n// @grant GM.xmlHttpRequest\n// @grant GM_registerMenuCommand\n// @connect 127.0.0.1\n// @require https://dependency.example.test/lib.js\n// @resource css https://dependency.example.test/a.css").copy(dependencies=mapOf("https://dependency.example.test/lib.js" to "const dependencyValue=42;"),resources=mapOf("css" to "body{}"),resourceUrls=mapOf("css" to "data:text/css;base64,Ym9keXt9"))
            r.runOnMainSync{scripts=UserScripts(r.targetContext,"qa-userscript.json",openTab={_,url,active->check(url=="https://opened.example.test/"&&!active);openedTab=true;99L},closeTab={id->check(id==99L);closedTab=true},clipboardWriter={text,_->clipboardText=text});scripts!!.save(emptyList());scripts!!.save(listOf(primary,make("qa-frame","window.qaFrame=true;","// @run-at document-end", "https://frame.example.test/allowed"),make("qa-noframe","window.qaNoFrame=true;","// @noframes\n// @run-at document-end", "https://frame.example.test/allowed"),make("qa-excluded","window.qaExcluded=true;","// @exclude *://*/allowed")))
                web=WebView(r.targetContext).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;scripts!!.attach(this);webViewClient=object:WebViewClient(){override fun shouldInterceptRequest(v:WebView,request:android.webkit.WebResourceRequest):android.webkit.WebResourceResponse? {
                    if(request.url.toString()!="https://frame.example.test/allowed")return null
                    val html="<html><body>Frame<script>document.addEventListener('DOMContentLoaded',()=>parent.postMessage({fixtureFrame:true,ran:window.qaFrame===true,forbidden:window.qaNoFrame===true},'*'));</script></body></html>"
                    return android.webkit.WebResourceResponse("text/html","UTF-8",java.io.ByteArrayInputStream(html.toByteArray()))
                };override fun onPageStarted(v:WebView,url:String,icon:android.graphics.Bitmap?){};override fun onPageFinished(v:WebView,url:String){scripts!!.navigated(v);scripts!!.finished(v)}}}
                web!!.loadDataWithBaseURL("https://scripts.example.test/allowed","<html><head><script>window.qaEarly=window.qaStart===true;window.addEventListener('message',e=>{if(e.data.fixtureFrame)window.qaFrameMessage=e.data;});</script></head><body>Synthetic only<iframe src='https://frame.example.test/allowed'></iframe></body></html>","text/html","UTF-8",null)
            }
            await("window.qaDone===true&&!!window.qaFrameMessage")
            val state=JSONArray(JSONTokener(eval("JSON.stringify([qaEarly,qaStorage,qaRequest,qaBlocked,qaDelete,qaResource,qaInitial===null,qaFrameMessage.ran,qaFrameMessage.forbidden,window.qaExcluded===true,getComputedStyle(document.body).color])")).nextValue() as String)
            check(eval("window.qaOpenedTab===true&&window.qaClipboard===true&&window.qaTab===true&&window.qaChange===true&&window.qaUrlChange===true")=="true"){"Tab data, value listener or URL change hook failed"};check(openedTab&&closedTab){"Native tab bridge failed"};check(clipboardText=="synthetic-clipboard"){"Clipboard bridge failed"};for(i in 0..7)check(state.getBoolean(i)){"Compatibility assertion $i failed: $state"};check(!state.getBoolean(8)&&!state.getBoolean(9)){"@noframes or exclusion failed"};check(state.getString(10)=="rgb(10, 20, 30)"){"Style helper failed"}
            r.runOnMainSync{val command=scripts!!.commands(web).single();command.run()};await("window.qaMenu===true")
            r.runOnMainSync{scripts!!.attach(web!!)};eval("window.qaAfterAttach()");await("window.qaContinued===true")
            r.runOnMainSync{scripts!!.detach(web!!);scripts!!.close();web!!.destroy();scripts=UserScripts(r.targetContext,"qa-userscript.json",openTab={_,url,active->check(url=="https://opened.example.test/"&&!active);openedTab=true;99L},closeTab={id->check(id==99L);closedTab=true},clipboardWriter={text,_->clipboardText=text});web=WebView(r.targetContext).apply{settings.javaScriptEnabled=true;scripts!!.attach(this);loadDataWithBaseURL("https://other.example.test/allowed","<html><body>Other origin</body></html>","text/html","UTF-8",null)}}
            await("window.qaDone===true");check(eval("window.qaInitial.x===3")=="true"){"Storage did not survive manager recreation / origin change"}
            result.putString("stream","PASS: cached require/resource, early injection, granted clipboard bridge (synthetic sink), tab data, URL change hook, value listener, sync/async persistent storage across origins, deletion, real native cross-origin JSON request, @connect denial, menu callback, iframe and @noframes, exclusions.\n");passed=true
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}finally{r.runOnMainSync{scripts?.close();web?.destroy()};r.targetContext.deleteFile("qa-userscript.json");r.targetContext.deleteFile("qa-userscript.json.values")}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
