package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID

/** Opt-in checks: synthetic credentials locally; remote mode consumes a private test fixture. */
object WebDavChecks {
    fun run(r:Instrumentation,mode:String) {
        val result=Bundle();var passed=true;var phase="core"
        try {
            if(mode=="remote")remote(r){phase=it}else core(r)
            result.putString("stream",if(mode=="remote")"PASS: HTTPS WebDAV create, download, ETag replacement, stale-version rejection, settings round trip; isolated remote file removed.\n"
                else "PASS: Keystore configuration round trip, no plaintext credentials, tamper rejection/preservation, settings replacement/defaults, unrelated preferences retained, native settings entry.\n")
        }catch(e:Throwable){passed=false;val detail=if(e is WebDavFailure)e.message else e.javaClass.simpleName
            result.putString("stream","FAIL: WebDAV $phase: $detail; no credentials or server response logged.\n")}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
    private fun core(r:Instrumentation) {
        val id=UUID.randomUUID().toString();val alias="qinglan.qa.webdav.$id";val filename="qa-webdav-$id.enc"
        val config=WebDavConfig("https://dav.example.test/dav/","synthetic-user","synthetic-password-$id")
        val store=WebDavConfigStore(r.targetContext,filename,alias);val file=File(r.targetContext.filesDir,filename)
        try {
            store.write(config);check(store.read()==config)
            val bytes=file.readBytes();check(!bytes.toString(Charsets.ISO_8859_1).contains(config.password))
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
            check(runCatching{store.read()}.isFailure);check(file.readBytes().contentEquals(bytes))
        }finally{store.clear();KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry(alias)}
        val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity
        var original:WebDavSettings.Snapshot?=null
        val marker="qa.webdav.keep.$id"
        fun main(block:()->Unit){var error:Throwable?=null;r.runOnMainSync{try{block()}catch(e:Throwable){error=e}};error?.let{throw it}}
        try {
            main {
                original=WebDavSettings.parse(WebDavSettings.export(a.prefs.all,AppLanguage.choice(a)))
                a.prefs.edit().putString(marker,"synthetic").putInt("homeColumns",5).commit()
                a.panels.settingsUi.restoreSynced(original!!.copy(settings=original!!.settings-"homeColumns"))
                check(!a.prefs.contains("homeColumns"));check(a.prefs.getInt("homeColumns",4)==4)
                check(a.prefs.getString(marker,"")=="synthetic")
                a.panels.settingsUi.open("webdav");check(a.panels.pages.visible)
            }
            r.waitForIdleSync()
        }finally{main{original?.let(a.panels.settingsUi::restoreSynced);a.prefs.edit().remove(marker).commit();a.panels.pages.close()}}
    }
    private fun remote(r:Instrumentation,phase:(String)->Unit) {
        val fixture=File(r.targetContext.filesDir,"qa-webdav.json")
        var client:WebDavClient?=null;var config:WebDavConfig?=null;var created=false
        try {
            phase("load fixture")
            val json=JSONObject(fixture.readText());config=WebDavConfig(json.getString("directory"),json.getString("username"),json.getString("password")).validated()
            client=WebDavClient(config,"qinglan-webdav-qa-${UUID.randomUUID()}.json")
            val raw=WebDavSettings.export(mapOf("theme" to "dark","homeColumns" to 3,"site.example.test.js" to false),"zh-Hans")
            phase("initial GET");check(client.download()==null)
            // Delete only the random resource for which this test attempted a conditional create.
            phase("create");created=true;client.upload(raw,null)
            phase("read created file")
            val first=client.download()?:error("Missing test resource")
            phase("round trip");check(first.snapshot==WebDavSettings.parse(raw))
            phase("ETag");check(WebDavClient.usableTag(first.etag))
            val updated=WebDavSettings.export(first.snapshot.settings+("theme" to "light"),"en")
            phase("create collision rejection")
            check(runCatching{client.upload(updated,null)}.exceptionOrNull()?.message==tr("远端设置已改变，请重新下载或重新发起上传"))
            check(client.download()?.snapshot==first.snapshot)
            phase("replace");client.upload(updated,first)
            phase("read replacement")
            check(client.download()?.snapshot==WebDavSettings.parse(updated))
            phase("stale ETag rejection");check(runCatching{client.upload(raw,first)}.exceptionOrNull()?.message==tr("远端设置已改变，请重新下载或重新发起上传"))
            check(client.download()?.snapshot==WebDavSettings.parse(updated))
        }finally {
            fixture.delete()
            if(created&&client!=null&&config!=null) {
                val connection=URL(client.address).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects=false;connection.connectTimeout=15_000;connection.readTimeout=20_000
                    connection.requestMethod="DELETE";connection.setRequestProperty("Authorization",config.authorization())
                    check(connection.responseCode in listOf(200,204,404)){"Test resource cleanup failed"}
                }finally{connection.disconnect()}
            }
        }
    }
}
