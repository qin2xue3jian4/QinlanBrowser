package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic reserved origin only. Never reads or clears real-site cookies/storage. */
object IncognitoChecks {
    private const val ORIGIN="https://qinglan-private.example.test/"
    private const val MARKER="incognito-check-profile.txt"
    fun run(r:Instrumentation,mode:String){
        val result=Bundle();var a:BrowserActivity?=null;var probe:WebView?=null
        var originalIds=setOf<Long>();var originalSelected=0L
        fun main(action:()->Unit){var failure:Throwable?=null;r.runOnMainSync{try{action()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
        fun eval(web:WebView,js:String):String {var value="";val ready=CountDownLatch(1);main{web.evaluateJavascript(js){value=it;ready.countDown()}};check(ready.await(10,TimeUnit.SECONDS)){"JavaScript timeout"};return value}
        fun awaitJS(web:WebView,js:String){repeat(80){if(eval(web,js)=="true")return;Thread.sleep(100)};error("Fixture condition timeout: $js")}
        fun load(web:WebView,url:String){main{web.stopLoading();web.loadDataWithBaseURL(url,"<html><head><title>Incognito fixture</title></head><body><p>Synthetic incognito test</p></body></html>","text/html","UTF-8",null)};awaitJS(web,"document.title==='Incognito fixture'")}
        fun seed(web:WebView,value:String){
            eval(web,"""(()=>{window.fixtureReady=false;document.cookie='ql_private=$value;path=/;SameSite=Lax';localStorage.setItem('ql_private','$value');const req=indexedDB.open('ql_private',1);req.onupgradeneeded=()=>req.result.createObjectStore('values');req.onsuccess=()=>{const db=req.result;const tx=db.transaction('values','readwrite');tx.objectStore('values').put('$value','key');tx.oncomplete=()=>{db.close();caches.open('ql_private').then(c=>c.put('/synthetic',new Response('$value'))).then(()=>window.fixtureReady=true)}};return true})()""")
            awaitJS(web,"window.fixtureReady===true")
        }
        fun assertData(web:WebView,value:String?){
            val data=JSONObject(eval(web,"({cookie:document.cookie,local:localStorage.getItem('ql_private')})"))
            if(value==null){check(!data.getString("cookie").contains("ql_private="));check(data.isNull("local"))}
            else {check(data.getString("cookie").contains("ql_private=$value"));check(data.getString("local")==value)}
            eval(web,"window.fixtureCheck=null;Promise.all([indexedDB.databases(),caches.keys()]).then(([d,c])=>window.fixtureCheck={db:d.map(x=>x.name),cache:c})")
            awaitJS(web,"window.fixtureCheck!==null")
            val state=JSONObject(eval(web,"window.fixtureCheck"));check(state.getJSONArray("db").toString().contains("ql_private")== (value!=null)){"IndexedDB isolation/cleanup"};check(state.getJSONArray("cache").toString().contains("ql_private")== (value!=null)){"CacheStorage isolation/cleanup"}
        }
        try{
            val activity=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;a=activity
            main{if(mode=="core")activity.tabs.filter{it.url.startsWith(ORIGIN)}.toList().forEach{activity.closeTab(it.id)};originalIds=activity.tabs.map{it.id}.toSet();originalSelected=activity.current!!.id}
            check(PrivateSession.supported()){"Profile isolation or deletion unsupported on this WebView"}
            if(mode=="recover"){
                val old=File(activity.filesDir,MARKER).readText()
                main{check(old !in ProfileStore.getInstance().allProfileNames){"Stale private profile survived startup"};check(!activity.isIncognito){"Mode restored as private"};check(activity.tabs.none{it.url.startsWith(ORIGIN)}){"Synthetic tab restored"};check(!activity.prefs.getString("tabs","").orEmpty().contains(ORIGIN)){"Synthetic saved tabs remained"};check(activity.store.history.none{it.url.startsWith(ORIGIN)}){"Synthetic history remained"};File(activity.filesDir,MARKER).delete()}
                result.putString("stream","PASS: force-stop recovery deleted stale private profile; only ordinary tabs restored; no private history/session URLs.\n")
            }else if(mode=="seed"){
                main{check(activity.enterPrivate());activity.open(ORIGIN+"secret-crash")}
                val web=activity.current!!.web!!;load(web,ORIGIN+"secret-crash");seed(web,"crash-secret")
                main{activity.privateSession!!.profile.cookieManager.flush();File(activity.filesDir,MARKER).writeText(activity.privateSession!!.name);check(!activity.prefs.getString("tabs","").orEmpty().contains("secret-crash"))}
                val ready=Bundle();ready.putString("stream","READY: synthetic private storage written; force-stop now, before lifecycle cleanup.\n");r.sendStatus(0,ready)
                Thread.sleep(30_000)
                error("Seed must be force-stopped by the host within 30 seconds")
            }else{
                main{activity.open(ORIGIN+"regular",true)}
                val normal=activity.current!!.web!!;load(normal,ORIGIN+"regular");seed(normal,"ordinary")
                var savedTabs="";var profileName="";var privateProfile:androidx.webkit.Profile?=null;var closedAvailable=false
                main{closedAvailable=activity.closedTabs.available();check(activity.enterPrivate());savedTabs=activity.prefs.getString("tabs","").orEmpty();profileName=activity.privateSession!!.name;privateProfile=activity.privateSession!!.profile;check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0);activity.open(ORIGIN+"secret")}
                val secret=activity.current!!.web!!;load(secret,ORIGIN+"secret");assertData(secret,null);seed(secret,"private")
                main{check(WebViewCompat.getProfile(secret).name==profileName);check(privateProfile!!.cookieManager.getCookie(ORIGIN).contains("ql_private=private"));check(android.webkit.CookieManager.getInstance().getCookie(ORIGIN).contains("ql_private=ordinary"));activity.openBackground(ORIGIN+"secret-child")}
                val child=activity.tabs.last().web!!;load(child,ORIGIN+"secret-child");assertData(child,"private")
                main{check(activity.tabs.all{it.incognito});check(activity.tabs.all{WebViewCompat.getProfile(it.web!!).name==profileName});activity.closeTab(activity.tabs.last().id);check(activity.closedTabs.available()==closedAvailable){"Private close added undo entry"};activity.persistSession();check(activity.prefs.getString("tabs","")==savedTabs){"Private navigation changed persisted normal tabs"};check(activity.store.history.none{it.url.contains("secret")&&it.url.startsWith(ORIGIN)});check(!File(activity.filesDir,"library.json").readText().contains("secret-child"))}
                val cleared=CountDownLatch(1);var ok=false
                main{activity.exitPrivate{ok=it;cleared.countDown()}}
                check(cleared.await(20,TimeUnit.SECONDS)&&ok){"Private data deletion did not complete"}
                main{check(!activity.isIncognito);check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0);probe=WebView(activity).apply{WebViewCompat.setProfile(this,profileName);settings.javaScriptEnabled=true;settings.domStorageEnabled=true}}
                load(probe!!,ORIGIN+"cleanup-probe");assertData(probe!!,null)
                val restored=activity.current!!.web!!;load(restored,ORIGIN+"regular");assertData(restored,"ordinary")
                // Remove only this test origin's synthetic values from the default profile.
                eval(restored,"document.cookie='ql_private=;path=/;Max-Age=0';localStorage.removeItem('ql_private');indexedDB.deleteDatabase('ql_private');caches.delete('ql_private');true")
                main{probe!!.destroy();probe=null;check(activity.enterPrivate());check(activity.privateSession!!.name!=profileName);activity.open(ORIGIN+"secret-fresh")}
                val fresh=activity.current!!.web!!;load(fresh,ORIGIN+"secret-fresh");assertData(fresh,null)
                main{activity.closeTab(activity.current!!.id);check(!activity.isIncognito);check(activity.currentUrl==ORIGIN+"regular");check(activity.tabs.map{it.id}.containsAll(originalIds))}
                result.putString("stream","PASS: actual WebView profile separation; Cookie/localStorage/IndexedDB/CacheStorage isolation and exit cleanup; private background tabs share profile; no saved private tabs/history/closed entries; normal storage and tabs preserved; FLAG_SECURE; fresh session; closing last private tab exits.\n")
            }
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}
        finally{if(mode!="seed")main{probe?.destroy();a?.let{activity->if(activity.isIncognito)activity.exitPrivate();activity.tabs.filter{it.id !in originalIds}.toList().forEach{activity.closeTab(it.id)};activity.closedTabs.clear();activity.store.history.removeAll{it.url.startsWith(ORIGIN)};activity.store.save();activity.tabs.indexOfFirst{it.id==originalSelected}.takeIf{it>=0}?.let(activity::switchTab);activity.finish()}}}
        if(mode!="seed"){r.waitForIdleSync();a?.prefs?.edit()?.commit()} // Instrumentation.finish kills the process; flush queued cleanup writes first.
        r.finish(if(result.getString("stream").orEmpty().startsWith("PASS:"))Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
