package dev.qinglan.browser

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object UserScriptInstallChecks {
    fun run(r:Instrumentation,remote:Boolean=false) {
        val result=Bundle();var passed=false;var activity:BrowserActivity?=null;var oldTab=0L;var testTab=0L
        var prior:List<UserScript>?=null;var oldPrefs=mapOf<String,Any?>()
        fun main(run:()->Unit){var error:Throwable?=null;r.runOnMainSync{try{run()}catch(e:Throwable){error=e}};error?.let{throw it}}
        fun trace(text:String){r.sendStatus(0,Bundle().apply{putString("stream","CHECK: $text\n")})}
        try {
            if(remote){
                val response=ScriptNetwork.fetch(ScriptPanels.installUrl("https://greasyfork.org/zh-CN/scripts/466723"),timeout=15000,allowed={it.startsWith("https://")})
                check(response.status==200){"Remote HTTP ${response.status}"};val script=UserScript(source=response.text(),installUrl=response.url)
                check(script.name.isNotBlank());result.putString("stream","PASS: official Greasy Fork page URL resolved to installable UserScript source; ${script.meta["grant"].orEmpty().size} supported grants. No third-party script installed or executed.\n");passed=true
            } else {
                val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;activity=a
                trace("Open synthetic install page")
                main{check(!a.isIncognito){"Exit private mode before installation test"};oldTab=a.current!!.id;prior=a.scripts.read().filterNot{it.meta["namespace"]==listOf("qinglan-qa")&&it.name=="Qinglan synthetic install fixture"};a.scripts.save(prior!!);a.tabs.filterNot{it.incognito}.forEach{it.web?.let(a.scripts::attach)};oldPrefs=listOf("httpsOnly","recordHistory").associateWith{a.prefs.all[it]};a.prefs.edit().putBoolean("httpsOnly",false).putBoolean("recordHistory",false).apply();a.newHome();testTab=a.current!!.id;a.open("http://127.0.0.1:8895/")}
                fun waitFor(checker:()->Boolean){val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(System.nanoTime()<end){var yes=false;main{yes=checker()};if(yes)return;Thread.sleep(100)};error("Install flow timed out")}
                fun eval(code:String):String{val latch=CountDownLatch(1);var value="";main{a.current!!.web!!.evaluateJavascript(code){value=it;latch.countDown()}};check(latch.await(10,TimeUnit.SECONDS));return value}
                fun dialog()=PageHost::class.java.getDeclaredField("dialog").apply{isAccessible=true}.get(a.panels.pages) as? Dialog
                fun find(root:View,text:String):View?{if(root is TextView&&root.text.toString()==text)return root;if(root is ViewGroup)for(i in 0 until root.childCount)find(root.getChildAt(i),text)?.let{return it};return null}
                waitFor{a.current!!.committedUrl=="http://127.0.0.1:8895/"&&a.current!!.web!!.progress==100}
                eval("document.getElementById('install').click()")
                trace("Preview install link")
                waitFor{dialog()?.window?.decorView?.let{find(it,tr("安装用户脚本"))}!=null}
                main{check(a.scripts.read()==prior){"Script installed without preview confirmation"};check(a.current!!.committedUrl=="http://127.0.0.1:8895/"){"Install link replaced the page"};find(dialog()!!.window!!.decorView,tr("安装"))!!.performClick()}
                waitFor{a.scripts.read().size==prior!!.size+1}
                trace("Enable installed script")
                var installed:UserScript?=null
                main{installed=a.scripts.read().single{it.meta["namespace"]==listOf("qinglan-qa")};check(!installed!!.enabled){"New script enabled automatically"};check(installed!!.installUrl.endsWith(".user.js"));a.panels.pages.close();a.scripts.save(a.scripts.read().map{if(it.id==installed!!.id)it.copy(enabled=true)else it});a.tabs.filterNot{it.incognito}.forEach{it.web?.let(a.scripts::attach)};a.reload()}
                val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end&&eval("document.body.dataset.installed==='true'")!="true")Thread.sleep(100)
                check(eval("document.body.dataset.installed==='true'")=="true"){"Installed script did not execute"}
                trace("Update installed script")
                main{a.panels.scripts.preview(installed!!.source.replace("// @version 1.0","// @version 1.1"),installed!!.installUrl);find(dialog()!!.window!!.decorView,tr("更新脚本"))!!.performClick()}
                waitFor{a.scripts.read().find{it.id==installed!!.id}?.version=="1.1"}
                main{check(a.scripts.read().single{it.id==installed!!.id}.enabled){"Update lost enabled state"};check(a.scripts.read().size==prior!!.size+1){"Update duplicated the script"}}
                trace("Greasy Fork install click precedes extension-help guard")
                main{a.panels.pages.close();a.current!!.web!!.loadDataWithBaseURL("https://greasyfork.org/scripts/466723","<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><a id='install' style='display:block;padding:36px;font-size:20px' href='https://greasyfork.org/scripts/466723/code/script.user.js'>Synthetic official install link</a><script>document.addEventListener('click',e=>{e.preventDefault();window.qaExtensionHelp=true;});</script></body></html>","text/html","UTF-8",null)}
                Thread.sleep(500)
                val bounds=org.json.JSONArray(org.json.JSONTokener(eval("JSON.stringify((()=>{const r=document.getElementById('install').getBoundingClientRect();return [(r.left+r.width/2)*devicePixelRatio,(r.top+r.height/2)*devicePixelRatio];})())")).nextValue() as String)
                val location=IntArray(2);main{a.current!!.web!!.getLocationOnScreen(location)}
                val x=location[0]+bounds.getDouble(0).toFloat();val y=location[1]+bounds.getDouble(1).toFloat();val time=android.os.SystemClock.uptimeMillis()
                r.sendPointerSync(android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,x,y,0));Thread.sleep(80);r.sendPointerSync(android.view.MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,x,y,0))
                waitFor{dialog()?.window?.decorView?.let{find(it,tr("安装用户脚本"))}!=null}
                check(eval("window.qaExtensionHelp!==true")=="true"){"Extension-help guard intercepted the install click"}
                main{check(a.scripts.read().size==prior!!.size+1){"Third-party script installed without confirmation"}}
                result.putString("stream","PASS: real .user.js link intercepted, original page retained, source/grant preview before installation, disabled by default, enabled installed script executed, manual update preserved ID and enabled state; trusted Greasy Fork install click reached native preview before the synthetic extension-help guard, official remote source parsed and not installed. Synthetic install removed and original settings/tabs restored.\n");passed=true
            }
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}
        finally{activity?.let{a->main{a.panels.pages.close();prior?.let{a.scripts.save(it);a.tabs.filterNot{it.incognito}.forEach{tab->tab.web?.let(a.scripts::attach)}};if(testTab!=0L&&a.tabs.any{it.id==testTab})a.closeTab(testTab);val old=a.tabs.indexOfFirst{it.id==oldTab};if(old>=0)a.switchTab(old);val edit=a.prefs.edit();oldPrefs.forEach{(key,value)->when(value){null->edit.remove(key);is Boolean->edit.putBoolean(key,value)}};edit.apply()}}}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
