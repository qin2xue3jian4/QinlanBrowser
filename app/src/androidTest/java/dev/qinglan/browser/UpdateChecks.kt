package dev.qinglan.browser

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object UpdateChecks {
    fun run(r:Instrumentation,mode:String) {
        val result=Bundle();var passed=true
        try {
            if(mode=="remote"){
                val release=GitHubUpdates.latest()
                result.putString("stream","PASS: public GitHub API completed; installable stable release=${release?.version?.name?:"none"}; no credentials sent.\n")
            }else{
                core(r)
                result.putString("stream","PASS: automatic check opt-out, daily throttle, persisted release cache, manual recheck, update details and public about page; original preferences restored.\n")
            }
        }catch(e:Throwable){passed=false;result.putString("stream","FAIL: updates ${e.javaClass.simpleName}: ${e.message}\n")}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
    private fun core(r:Instrumentation) {
        val prefs=r.targetContext.getSharedPreferences("preferences",Context.MODE_PRIVATE)
        val old=prefs.all["autoCheckUpdates"]
        check(prefs.edit().putBoolean("autoCheckUpdates",false).commit())
        val stateName="qa-updates-${UUID.randomUUID()}"
        val state=r.targetContext.getSharedPreferences(stateName,Context.MODE_PRIVATE)
        var a:BrowserActivity?=null;var updater:UpdatePanels?=null;var restored:UpdatePanels?=null
        fun main(block:()->Unit){var error:Throwable?=null;r.runOnMainSync{try{block()}catch(e:Throwable){error=e}};error?.let{throw it}}
        fun waitFor(condition:()->Boolean){val until=System.currentTimeMillis()+10_000
            while(System.currentTimeMillis()<until){var ready=false;main{ready=condition()};if(ready)return;Thread.sleep(30)}
            error("Update check did not complete")}
        fun root():View {val f=PageHost::class.java.getDeclaredField("dialog").apply{isAccessible=true};return (f.get(a!!.panels.pages) as Dialog).window!!.decorView}
        fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
        fun busy(updates:UpdatePanels)=UpdatePanels::class.java.getDeclaredField("busy").apply{isAccessible=true}.getBoolean(updates)
        fun capture(name:String){r.waitForIdleSync();val image=r.uiAutomation.takeScreenshot()?:error("Screenshot unavailable")
            try{File(r.targetContext.cacheDir,name).outputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{image.recycle()}}
        try {
            val activity=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;a=activity
            val calls=AtomicInteger()
            val release=AppRelease(AppVersion.parse("9.9.9")!!,"Synthetic update","Synthetic release notes for UI verification.")
            val fetch={calls.incrementAndGet();release}
            main {
                check(!activity.isIncognito){"Exit private mode before update checks"}
                activity.panels.settings();updater=UpdatePanels(activity.panels,fetch,stateName)
                updater!!.resumed();check(calls.get()==0)
                prefs.edit().putBoolean("autoCheckUpdates",true).commit();updater!!.resumed()
            }
            waitFor{calls.get()==1&&!busy(updater!!)}
            check(AppRelease.parse(state.getString("release","")!!)==release)
            main {
                updater!!.resumed();updater!!.paused();check(calls.get()==1)
                restored=UpdatePanels(activity.panels,fetch,stateName);restored!!.resumed();restored!!.show()
                check(calls.get()==1)
                views(root()).filterIsInstance<Button>().single{it.text==tr("立即检查更新")}.performClick()
            }
            waitFor{calls.get()==2&&!busy(restored!!)}
            main{check(views(root()).filterIsInstance<TextView>().any{it.text.toString().contains("Synthetic release notes")})}
            capture("qa-update-preview.png")
            main{activity.panels.pages.close();activity.panels.about()
                val labels=views(root()).filterIsInstance<TextView>().map{it.text.toString()}
                check(tr("项目主页") in labels&&tr("数据与隐私") in labels&&tr("检查更新") in labels)}
            capture("qa-about-preview.png")
        }finally {
            main{updater?.close();restored?.close();a?.panels?.pages?.close()
                val e=prefs.edit();if(old is Boolean)e.putBoolean("autoCheckUpdates",old)else e.remove("autoCheckUpdates");check(e.commit())}
            state.edit().clear().commit();r.targetContext.deleteSharedPreferences(stateName)
        }
    }
}
