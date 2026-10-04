package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object RevisionChecks {
    fun seed(r:Instrumentation){val s=BrowserStore(r.targetContext)
        if(s.home.none{it.id=="qa4-folder"})s.home.addAll(0,listOf(HomeItem("qa4-a","测试甲","https://a.example.test/"),HomeItem("qa4-b","测试乙","https://b.example.test/"),HomeItem("qa4-folder","测试文件夹",folder=true),HomeItem("qa4-c","测试丙","https://c.example.test/","qa4-folder")))
        if(s.bookmarks.none{it.id=="qa4-folder"})s.bookmarks.addAll(0,listOf(Visit("测试甲","https://a.example.test/",id="qa4-a"),Visit("测试乙","https://b.example.test/",id="qa4-b"),Visit("测试文件夹","",id="qa4-folder",folder=true),Visit("测试丙","https://c.example.test/",id="qa4-c",parent="qa4-folder")))
        if(!s.prefs.contains("qa4-original-palette"))s.prefs.edit().putString("qa4-original-palette",s.prefs.getString("palette","green")).putString("qa4-original-color",s.prefs.getString("customColor",null)).commit()
        s.save();r.finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Seeded only qa4 fixtures.\n")})
    }
    fun cleanup(r:Instrumentation){val s=BrowserStore(r.targetContext);s.home.filter{!it.id.startsWith("qa4-")&&it.parent.startsWith("qa4-")}.forEach{it.parent=""};s.home.removeAll{it.id.startsWith("qa4-")};LibraryOrder.deleteBookmarks(s.bookmarks,s.bookmarks.filter{it.id.startsWith("qa4-")}.map{it.id}.toSet());s.history.removeAll{it.url in listOf("https://a.example.test/","https://b.example.test/","https://c.example.test/")};s.save()
        val edit=s.prefs.edit();if(s.prefs.contains("qa4-original-palette")){edit.putString("palette",s.prefs.getString("qa4-original-palette","green"));edit.putString("customColor",s.prefs.getString("qa4-original-color",null));edit.remove("qa4-original-palette");edit.remove("qa4-original-color")};edit.commit();r.targetContext.deleteFile("qa4-scripts.json");r.finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Cleaned qa4 fixtures, restored color preferences.\n")})
    }
    fun run(r:Instrumentation){var web:WebView?=null;val result=Bundle()
        try{
            val scripts=UserScripts(r.targetContext,"qa4-scripts.json")
            fun script(id:String,timing:String,body:String,extra:String="",enabled:Boolean=true)=UserScript(id,"// ==UserScript==\n// @name $id\n// @match https://scripts.example.test/*\n// @run-at $timing\n$extra\n// ==/UserScript==\n$body",enabled)
            scripts.save(listOf(script("start","document-start","window.qaStart=true;GM_addStyle('body { color: rgb(10, 20, 30); }');"),script("end","document-end","document.body.dataset.qaEnd='yes';window.qaCount=(window.qaCount||0)+1;"),script("idle","document-idle","document.body.dataset.qaIdle='yes';"),script("disabled","document-end","window.qaDisabled=true;",enabled=false),script("excluded","document-end","window.qaExcluded=true;","// @exclude-match https://scripts.example.test/allowed")))
            val loaded=CountDownLatch(1)
            r.runOnMainSync{web=WebView(r.targetContext).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;scripts.attach(this);webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){scripts.finished(v);loaded.countDown()}};loadDataWithBaseURL("https://scripts.example.test/allowed","<html><head><script>window.qaEarly=window.qaStart===true;</script></head><body>synthetic only<iframe srcdoc=\"<script>window.qaFrame=true;</script>\"></iframe></body></html>","text/html","UTF-8",null)}}
            check(loaded.await(15,TimeUnit.SECONDS)){"page load timeout"}
            val checked=CountDownLatch(1);var values=""
            r.runOnMainSync{android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({web!!.evaluateJavascript("JSON.stringify([window.qaEarly===true,document.body.dataset.qaEnd,document.body.dataset.qaIdle,window.qaCount,window.qaDisabled===true,window.qaExcluded===true,getComputedStyle(document.body).color,document.querySelector('iframe').contentWindow.qaStart===true])"){values=it;checked.countDown()}},300)}
            check(checked.await(10,TimeUnit.SECONDS)){"script check timeout"}
            val decoded=org.json.JSONTokener(values).nextValue() as String;val v=org.json.JSONArray(decoded)
            check(v.getBoolean(0)){"document-start did not precede inline JS"};check(v.getString(1)=="yes"&&v.getString(2)=="yes"){"DOM timing"};check(v.getInt(3)==1){"duplicate execution"};check(!v.getBoolean(4)&&!v.getBoolean(5)){"disabled or excluded script ran"};check(v.getString(6)=="rgb(10, 20, 30)"){"style helper"};check(!v.getBoolean(7)){"script executed inside iframe"}
            result.putString("stream","PASS: real WebView document-start/end/idle, single execution, disabled/excluded rules, GM_addStyle, top frame only.\n");r.finish(Activity.RESULT_OK,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n");r.finish(Activity.RESULT_CANCELED,result)}finally{r.runOnMainSync{web?.destroy()};r.targetContext.deleteFile("qa4-scripts.json")}
    }
}
