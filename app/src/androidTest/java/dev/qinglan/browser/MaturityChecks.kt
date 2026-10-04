package dev.qinglan.browser

import android.app.Activity
import android.app.DownloadManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object MaturityChecks {
    fun run(r:Instrumentation){val result=Bundle();var activity:BrowserActivity?=null
        try{
            val archive=ReadingList(r.targetContext,"qa-maturity-reading")
            val first=archive.save("Synthetic title","https://reader.example.test/a","First synthetic paragraph.\n\nLast paragraph.")
            check(archive.text(first).endsWith("Last paragraph."));check(archive.list().size==1)
            val second=archive.save("Updated","https://reader.example.test/a","Updated offline body")
            check(archive.list().size==1&&archive.text(second)=="Updated offline body")
            check(runCatching{archive.save("Too large","https://reader.example.test/long","a".repeat(200001))}.isFailure)
            archive.delete(second);check(archive.list().isEmpty())
            val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;activity=a
            r.runOnMainSync{
                val original=a.current!!.id;val before=a.tabs.size
                a.newHome();val tab=a.current!!;tab.url="https://qa.example.test/";tab.web=android.webkit.WebView(a)
                class Fake(private val requested:String,private val types:Array<String>):PermissionRequest(){var denied=false;var granted=false;override fun getOrigin()=Uri.parse(requested);override fun getResources()=types;override fun deny(){denied=true};override fun grant(resources:Array<String>){granted=true}}
                val unknown=Fake("https://qa.example.test",arrayOf("future.unknown.permission"));a.webPermissions.media(tab,unknown);check(unknown.denied&&!unknown.granted)
                val cross=Fake("https://cross.example.test",arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE));a.webPermissions.media(tab,cross);check(cross.denied&&!cross.granted)
                val known=Fake("https://qa.example.test/",arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE));a.webPermissions.media(tab,known)
                check(a.webPermissions.asking&&!known.granted&&!known.denied){"Foreground prompt missing"};a.webPermissions.cancel();check(known.denied&&!known.granted)
                a.panels.settingsUi.show();a.panels.settingsUi.open("recordHistory");check(a.panels.pages.visible);a.panels.pages.back();a.panels.pages.close()
                a.closeTab(tab.id);a.closedTabs.clear();a.switchTab(a.tabs.indexOfFirst{it.id==original});check(a.tabs.size==before)
            }
            result.putString("stream","PASS: private offline save/load/replace/delete and size bound; unknown and mismatched permission requests denied; settings navigation lifecycle.\n")
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n");r.finish(Activity.RESULT_CANCELED,result);return}
        finally{r.runOnMainSync{activity?.finish()}}
        r.finish(Activity.RESULT_OK,result)
    }
    /** Requires the loopback fixture; creates one bounded synthetic failed download for UI retry QA. */
    fun download(r:Instrumentation){val result=Bundle();val manager=r.targetContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var id:Long?=null
        try{
            id=manager.enqueue(DownloadManager.Request(Uri.parse("http://127.0.0.1:8877/fail")).setTitle("qinglan-maturity-failure.txt").setMimeType("text/plain").setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,"qinglan-maturity-failure.txt").addRequestHeader("User-Agent","Mozilla/5.0 Qinglan-QA"))
            val prefs=r.targetContext.getSharedPreferences("preferences",0);prefs.edit().putStringSet("downloads",prefs.getStringSet("downloads",emptySet()).orEmpty()+id.toString()).commit()
            var status=0;var reason=0;val deadline=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<deadline){manager.query(DownloadManager.Query().setFilterById(id)).use{c->if(c.moveToFirst()){status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));reason=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}};if(status==DownloadManager.STATUS_FAILED)break;Thread.sleep(300)}
            check(status==DownloadManager.STATUS_FAILED&&reason==404){"Expected 404, got $status / $reason"}
            result.putString("stream","PASS: synthetic system download failed with HTTP 404; retained only its record for UI retry QA.\n");r.finish(Activity.RESULT_OK,result)
        }catch(e:Throwable){id?.let{manager.remove(it);val prefs=r.targetContext.getSharedPreferences("preferences",0);prefs.edit().putStringSet("downloads",prefs.getStringSet("downloads",emptySet()).orEmpty()-it.toString()).commit()};result.putString("stream","FAIL: ${e.message}\n");r.finish(Activity.RESULT_CANCELED,result)}
    }
}
