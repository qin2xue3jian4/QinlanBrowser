package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Copies public source for a static compatibility review; does not execute or install it. */
object UserScriptReviewChecks {
    fun run(r:Instrumentation) {
        val out=JSONArray();val bundle=Bundle();var passed=false
        try {
            val report=JSONObject(File(r.targetContext.filesDir,"qa-popular-report.json").readText());val scripts=report.getJSONArray("scripts")
            val folder=File(r.targetContext.cacheDir,"qa-popular-source").apply{mkdirs()}
            for(i in 0 until scripts.length()) {
                val id=scripts.getJSONObject(i).getString("id");val item=JSONObject().put("id",id);out.put(item)
                try{val response=ScriptNetwork.fetch("https://greasyfork.org/scripts/$id/code/script.user.js",timeout=15000,allowed={it.startsWith("https://")});check(response.status==200){"HTTP ${response.status}"};val source=response.text();File(folder,"$id.user.js").writeText(source)
                    item.put("bytes",response.bytes.size).put("sourceUrl",response.url).put("apiReferences",JSONArray(Regex("\\bGM(?:_|\\.)[A-Za-z][A-Za-z0-9_]*").findAll(source).map{it.value}.distinct().toList())).put("saved",true)
                    r.sendStatus(0,Bundle().apply{putString("stream","REVIEW: ${i+1}/${scripts.length()} public source $id saved\n")})
                }catch(e:Throwable){item.put("saved",false).put("reason",e.message);r.sendStatus(0,Bundle().apply{putString("stream","REVIEW: $id source unavailable: ${e.message}\n")})}
            }
            passed=true;bundle.putString("stream","PASS: public sources cached for static review. No third-party execution.\n")
        }catch(e:Throwable){bundle.putString("stream","FAIL: ${e.message}\n")}
        finally{File(r.targetContext.filesDir,"qa-popular-review.json").writeText(out.toString(2))}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,bundle)
    }
}
