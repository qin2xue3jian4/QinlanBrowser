package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Installs current popular scripts disabled into a separate test store. Never executes them. */
object UserScriptPopularChecks {
    fun run(r:Instrumentation) {
        val results=JSONArray();val report=JSONObject().put("listUrl","https://greasyfork.org/zh-CN/scripts?filter_locale=0&sort=total_installs").put("checkedAt",java.time.Instant.now().toString()).put("executionTested",false)
        val bundle=Bundle();var passed=false;var manager:UserScripts?=null
        try {
            var ua="";r.runOnMainSync{ua=android.webkit.WebSettings.getDefaultUserAgent(r.targetContext)}
            val page=ScriptNetwork.fetch(report.getString("listUrl"),timeout=20000,allowed={it.startsWith("https://")});check(page.status==200){"List HTTP ${page.status}"}
            val ids=Regex("data-script-id=[\"'](\\d+)[\"']").findAll(page.text()).map{it.groupValues[1]}.distinct().take(30).toList();check(ids.size>=10){"No usable popularity list"}
            r.runOnMainSync{manager=UserScripts(r.targetContext,"qa-popular-scripts.json");manager!!.save(emptyList())}
            val installed=mutableListOf<UserScript>()
            ids.forEachIndexed{index,id->
                val item=JSONObject().put("rank",index+1).put("id",id).put("pageUrl","https://greasyfork.org/scripts/$id");results.put(item)
                r.sendStatus(0,Bundle().apply{putString("stream","CHECK: popular ${index+1}/${ids.size}, Greasy Fork ID $id\n")})
                try {
                    item.put("stage","source")
                    val response=ScriptNetwork.fetch("https://greasyfork.org/scripts/$id/code/script.user.js",timeout=15000,allowed={it.startsWith("https://")});check(response.status==200){"Source HTTP ${response.status}"}
                    val source=response.text();item.put("name",Regex("(?m)^//\\s*@name\\s+(.+)$").find(source)?.groupValues?.get(1)?.trim()?:"ID $id").put("bytes",response.bytes.size)
                    item.put("grants",JSONArray(Regex("(?m)^//\\s*@grant\\s+(.+)$").findAll(source.substringBefore("// ==/UserScript==")).map{it.groupValues[1].trim()}.toList()))
                    item.put("requiresUrls",JSONArray(Regex("(?m)^//\\s*@require\\s+(.+)$").findAll(source.substringBefore("// ==/UserScript==")).map{it.groupValues[1].trim()}.toList()))
                    item.put("matches",JSONArray(Regex("(?m)^//\\s*@match\\s+(.+)$").findAll(source.substringBefore("// ==/UserScript==")).map{it.groupValues[1].trim()}.toList()))
                    item.put("stage","metadata");val script=UserScript(source=source,installUrl=response.url);item.put("stage","dependencies");val ready=ScriptNetwork.prepare(script,ua)
                    item.put("stage","save")
                    manager!!.save(installed+ready);installed.add(ready)
                    check(manager!!.read().single{it.id==ready.id}.let{!it.enabled&&it.source==source&&it.dependencies==ready.dependencies&&it.resources==ready.resources}){"Installed script round trip failed"}
                    item.remove("stage");item.put("result","installed_disabled").put("requires",ready.dependencies.size).put("resources",ready.resources.size).put("version",ready.version)
                    r.sendStatus(0,Bundle().apply{putString("stream","PASS: $id ${script.name.take(70)} (disabled, cached dependencies)\n")})
                }catch(e:Throwable){item.put("result","failed").put("reason",e.message?:e.javaClass.simpleName);r.sendStatus(0,Bundle().apply{putString("stream","FAIL: $id ${e.message?.take(160)}\n")})}
            }
            report.put("tested",ids.size).put("installed",installed.size).put("failed",ids.size-installed.size).put("scripts",results)
            check(installed.isNotEmpty()){"No popular scripts installed"};passed=true
            bundle.putString("stream","PASS: ${installed.size}/${ids.size} popular scripts installed disabled into the isolated test store; all installation sources and cached assets survived reload. No third-party scripts executed. Report: files/qa-popular-report.json\n")
        }catch(e:Throwable){report.put("error",e.message);bundle.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}
        finally{r.runOnMainSync{manager?.close()};r.targetContext.deleteFile("qa-popular-scripts.json");r.targetContext.deleteFile("qa-popular-scripts.json.values");File(r.targetContext.filesDir,"qa-popular-report.json").writeText(report.toString(2))}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,bundle)
    }
}
