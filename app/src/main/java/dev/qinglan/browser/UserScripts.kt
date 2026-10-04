package dev.qinglan.browser

import android.content.Context
import android.util.AtomicFile
import android.webkit.WebView
import androidx.webkit.*
import org.json.JSONArray
import java.io.File
import java.util.WeakHashMap

class UserScripts(context:Context,fileName:String="user-scripts.json"){
    private val file=AtomicFile(File(context.filesDir,fileName))
    private val handles=WeakHashMap<WebView,List<ScriptHandler>>()
    val documentStart get()=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    fun read():List<UserScript>{if(!file.baseFile.exists())return emptyList();val arr=JSONArray(file.openRead().bufferedReader().use{it.readText()});return List(arr.length()){i->val o=arr.getJSONObject(i);UserScript(o.getString("id"),o.getString("source"),o.getBoolean("enabled"))}}
    fun save(items:List<UserScript>){require(items.size<=50){"最多安装 50 个脚本"};val raw=JSONArray().apply{items.forEach{put(it.json())}}.toString().toByteArray();require(raw.size<=2097152){"脚本总大小超过 2 MB"};val out=file.startWrite();try{out.write(raw);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
    fun attach(web:WebView){if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))return;handles.remove(web)?.forEach{it.remove()};handles[web]=read().filter{it.enabled}.map{WebViewCompat.addDocumentStartJavaScript(web,ScriptCodec.javascript(it),setOf("*"))}}
    fun finished(web:WebView){if(!documentStart)read().filter{it.enabled&&it.runAt!="document-start"}.forEach{web.evaluateJavascript(ScriptCodec.javascript(it),null)}}
}
