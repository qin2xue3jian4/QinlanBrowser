package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object UserScriptCleanupChecks {
    fun run(r:Instrumentation,history:String) {
        val prefs=r.targetContext.getSharedPreferences("preferences",android.content.Context.MODE_PRIVATE)
        val old=JSONArray(prefs.getString("tabs","[]"));val retained=JSONArray();val indexes=mutableListOf<Int>()
        for(i in 0 until old.length()){val item=old.getJSONObject(i);val uri=android.net.Uri.parse(item.optString("url"));if(uri.host=="127.0.0.1"&&uri.port==8895)continue;retained.put(item);indexes.add(i)}
        val edit=prefs.edit();val removed=old.length()-retained.length()
        if(removed>0){if(retained.length()==0)retained.put(JSONObject().put("url","about:home").put("title",tr("主页")).put("account",""));val selected=prefs.getInt("selected",0);val index=indexes.indexOf(selected).takeIf{it>=0}?:indexes.indexOfLast{it<selected}.coerceAtLeast(0);edit.putString("tabs",retained.toString()).putInt("selected",index.coerceAtMost(retained.length()-1))}
        if(history=="true"||history=="false")edit.putBoolean("recordHistory",history=="true")
        edit.commit()
        listOf("qa-userscript.json","qa-userscript.json.values","qa-popular-scripts.json","qa-popular-scripts.json.values","qa4-scripts.json","qa4-scripts.json.values").forEach{r.targetContext.deleteFile(it)}
        val folder=File(r.targetContext.cacheDir,"qa-popular-source");folder.listFiles()?.filter{it.isFile&&Regex("\\d+\\.user\\.js").matches(it.name)}?.forEach{it.delete()};folder.delete()
        r.finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: removed $removed synthetic userscript session tabs and isolated test/source files; real scripts and other tabs preserved. History setting: ${prefs.getBoolean("recordHistory",true)}.\n")})
    }
}
