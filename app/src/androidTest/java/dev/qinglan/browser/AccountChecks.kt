package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises production tab switching with synthetic data; never inspects real login cookies. */
object AccountChecks {
    private const val ORIGIN="https://account-fixture.example.test/"
    private const val SSO="https://sso-fixture.example.test/"
    private const val MARKER="accounts-check.json"
    fun run(r:Instrumentation,mode:String){
        var activity:BrowserActivity?=null;val result=Bundle();val owned=mutableListOf<String>();var normal:WebView?=null
        var originalIds=setOf<Long>();var originalSelected=0L;var originalIndex=0
        fun main(action:()->Unit){var failure:Throwable?=null;r.runOnMainSync{try{action()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
        fun eval(web:WebView,script:String):String {var value="";val ready=CountDownLatch(1);main{web.evaluateJavascript(script){value=it;ready.countDown()}};check(ready.await(10,TimeUnit.SECONDS));return value}
        fun waitJS(web:WebView,script:String){repeat(100){if(eval(web,script)=="true")return;Thread.sleep(80)};error("Synthetic JS timeout: $script")}
        var sequence=0
        fun load(web:WebView,url:String){val title="Account fixture ${++sequence}";main{web.stopLoading();web.loadDataWithBaseURL(url,"<html><title>$title</title><body>Account fixture<input id='field'></body></html>","text/html","UTF-8",null)};waitJS(web,"document.title===${JSONObject.quote(title)}")}
        fun put(web:WebView,value:String){eval(web,"document.cookie='ql_account=$value;path=/;Max-Age=3600;SameSite=Lax';localStorage.setItem('ql_account','$value');true")}
        fun expect(web:WebView,value:String?){val data=JSONObject(eval(web,"({cookie:document.cookie,local:localStorage.getItem('ql_account')})"));if(value==null){check(!data.getString("cookie").contains("ql_account=")){"New space has synthetic cookie"};check(data.isNull("local")){"New space has synthetic localStorage"}}else{check(data.getString("cookie").contains("ql_account=$value")){"Cookie state mismatch"};check(data.getString("local")==value){"localStorage state mismatch"}}}
        fun removeAccount(a:BrowserActivity,id:String){val ready=CountDownLatch(1);var ok=false;main{a.deleteAccount(id){ok=it;ready.countDown()}};check(ready.await(20,TimeUnit.SECONDS)&&ok){"Account removal did not clear data"}}
        try{
            val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;activity=a
            main{originalIds=a.tabs.map{it.id}.toSet();originalSelected=a.current!!.id;originalIndex=a.selected}
            check(PrivateSession.supported())
            if(mode=="cleanup"){
                main{owned.addAll(a.accounts.all().filter{it.startUrl=="http://127.0.0.1:8881/"&&it.name in listOf("QA-Work","QA-Office","QA-Personal")}.map{it.id});a.tabs.filter{it.url.startsWith("http://127.0.0.1:8881/")}.toList().forEach{a.closeTab(it.id)};a.store.history.removeAll{it.url.startsWith("http://127.0.0.1:8881/")};a.store.save()}
                result.putString("stream","PASS: removed only the local account fixture tabs/history and explicitly named QA spaces.\n")
            }else if(mode=="recover"){
                val marker=JSONObject(File(a.filesDir,MARKER).readText());val id=marker.getString("id");owned.add(id);originalIndex=marker.getInt("selected")
                main{check(a.accounts.find(id)?.name=="QA 重启账号");check(a.current?.accountId==id){"Restored tab lost account identity"}}
                val web=a.current!!.web!!;load(web,ORIGIN);expect(web,"persisted")
                main{check(WebViewCompat.getProfile(web).name==id)}
                load(web,SSO);expect(web,"sso-persisted")
                main{a.prefs.edit().putBoolean("restore",marker.getBoolean("restore")).commit();File(a.filesDir,MARKER).delete()}
                result.putString("stream","PASS: named profile, persistent cookies, localStorage and associated SSO origin survive process restart; tab restores into same account.\n")
            }else if(mode=="seed"){
                lateinit var account:SiteAccount
                main{account=a.accounts.create("QA 重启账号",ORIGIN);owned.add(account.id);File(a.filesDir,MARKER).writeText(JSONObject().put("id",account.id).put("selected",originalIndex).put("restore",a.prefs.getBoolean("restore",true)).toString());a.prefs.edit().putBoolean("restore",true).commit();a.openAccount(account.id)}
                val web=a.current!!.web!!;load(web,ORIGIN);put(web,"persisted");load(web,SSO);put(web,"sso-persisted");load(web,ORIGIN)
                main{a.current!!.url=ORIGIN;a.current!!.error=null;a.persistSession();a.accounts.flush();a.moveTaskToBack(true)};a.prefs.edit().commit()
                Thread.sleep(6000) // Chromium batches localStorage disk writes; exercise a backgrounded session before killing the process.
                result.putString("stream","PASS: seeded named account and related-origin state for restart check.\n")
            }else{
                main{normal=WebView(a).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true}}
                load(normal!!,ORIGIN);put(normal!!,"default")
                lateinit var first:SiteAccount;lateinit var second:SiteAccount
                main{first=a.accounts.create("QA 工作",ORIGIN);second=a.accounts.create("QA 个人",ORIGIN);owned.addAll(listOf(first.id,second.id));a.accounts.rename(first.id,"QA 工作改名");check(AccountProfiles(a).find(first.id)?.name=="QA 工作改名");check(runCatching{a.accounts.rename(second.id,"QA 工作改名")}.isFailure);a.accounts.renameDefault("account-fixture.example.test","QA 原有登录");check(a.accounts.label("","account-fixture.example.test")=="QA 原有登录");a.openAccount(first.id)}
                var web=a.current!!.web!!;load(web,ORIGIN);expect(web,null);put(web,"work");load(web,SSO);expect(web,null);put(web,"sso-work")
                main{a.openBackground(ORIGIN)};val child=a.tabs.last();load(child.web!!,ORIGIN);expect(child.web!!,"work")
                main{check(child.accountId==first.id);check(WebViewCompat.getProfile(child.web!!).name==first.id);a.switchAccount(second.id,ORIGIN)}
                web=a.current!!.web!!;load(web,ORIGIN);expect(web,null);put(web,"personal");load(web,SSO);expect(web,null);put(web,"sso-personal")
                expect(child.web!!,"work");expect(normal!!,"default")
                main{a.switchAccount(first.id,ORIGIN)};web=a.current!!.web!!;load(web,ORIGIN);expect(web,"work");load(web,SSO);expect(web,"sso-work")
                main{val id=a.current!!.id;a.closeTab(id);a.undoCloseTab();check(a.current!!.accountId==first.id);check(WebViewCompat.getProfile(a.current!!.web!!).name==first.id)}
                main{check(a.enterPrivate());a.open(ORIGIN)};val secret=a.current!!.web!!;load(secret,ORIGIN);expect(secret,null);put(secret,"private")
                val ready=CountDownLatch(1);main{a.exitPrivate{check(it);ready.countDown()}};check(ready.await(20,TimeUnit.SECONDS));main{check(a.current!!.accountId==first.id)}
                load(a.current!!.web!!,ORIGIN);expect(a.current!!.web!!,"work")
                removeAccount(a,first.id);owned.remove(first.id)
                main{check(a.tabs.none{it.accountId==first.id});check(a.accounts.find(first.id)==null);check(!a.accounts.available(first.id));a.openAccount(second.id)}
                web=a.current!!.web!!;load(web,ORIGIN);expect(web,"personal");expect(normal!!,"default")
                main{a.switchAccount("",ORIGIN)};web=a.current!!.web!!;load(web,ORIGIN);expect(web,"default")
                main{check(a.tabs.map{it.id}.containsAll(originalIds))}
                result.putString("stream","PASS: named account CRUD and duplicate-name rejection; default account naming; actual Cookie/localStorage isolation; SSO origin separation; background tab inheritance; switching only current tab; undo preserves profile; incognito separation; deletion closes account tabs and preserves other accounts/default login.\n")
            }
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}; ${e.stackTrace.firstOrNull{it.className.contains("AccountChecks")}}\n")}
        finally{if(mode!="seed"){
            activity?.let{a->
                if(a.isIncognito){val ready=CountDownLatch(1);main{a.exitPrivate{ready.countDown()}};ready.await(20,TimeUnit.SECONDS)}
                owned.toList().forEach{id->if(a.accounts.find(id)!=null)removeAccount(a,id)}
                normal?.let{web->load(web,ORIGIN);eval(web,"document.cookie='ql_account=;path=/;Max-Age=0';localStorage.removeItem('ql_account');true")}
                main{normal?.destroy();a.accounts.renameDefault("account-fixture.example.test","默认账号");a.tabs.filter{it.id !in originalIds}.toList().forEach{a.closeTab(it.id)};a.closedTabs.clear();a.store.history.removeAll{it.url.startsWith(ORIGIN)||it.url.startsWith(SSO)};a.store.save();val index=a.tabs.indexOfFirst{it.id==originalSelected}.takeIf{it>=0}?:originalIndex.coerceIn(0,a.tabs.lastIndex);a.switchTab(index);a.finish()}
                r.waitForIdleSync();a.prefs.edit().commit()
            }
        }}
        r.finish(if(result.getString("stream").orEmpty().startsWith("PASS:"))Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
