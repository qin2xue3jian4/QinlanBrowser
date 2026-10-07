package dev.qinglan.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.webkit.WebView
import androidx.webkit.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors

class UserScripts(context:Context,fileName:String="user-scripts.json",private val httpsOnly:()->Boolean={false},private val openTab:((WebView,String,Boolean)->Long?)?=null,private val closeTab:((Long)->Unit)?=null,private val clipboardWriter:(String,Boolean)->Unit={text,html->
    val clipboard=context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(if(html)android.content.ClipData.newHtmlText("User script",text,text)else android.content.ClipData.newPlainText("User script",text))
}) {
    private val file=AtomicFile(File(context.filesDir,fileName))
    private val valueFile=AtomicFile(File(context.filesDir,"$fileName.values"))
    private val values=if(valueFile.baseFile.exists())runCatching{JSONObject(valueFile.openRead().bufferedReader().use{it.readText()})}.getOrElse{JSONObject()}else JSONObject()
    private data class Client(val doc:String,val url:String,val parent:String,val main:Boolean,val reply:JavaScriptReplyProxy)
    private data class Binding(var script:UserScript,val name:String="qlScript"+UUID.randomUUID().toString().replace("-",""),@Volatile var token:String=UUID.randomUUID().toString(),var handle:ScriptHandler?=null,val clients:MutableMap<String,Client> = linkedMapOf(),var inFlight:Int=0,var mainDoc:String="",var tabData:JSONObject=JSONObject(),val openedTabs:MutableSet<Long> = mutableSetOf())
    data class Command(val label:String,val run:()->Unit)
    private data class Menu(val web:WebView,val binding:Binding,val client:Client,val key:String,val label:String)
    private val bindings=WeakHashMap<WebView,List<Binding>>()
    private val bridges=WeakHashMap<WebView,MutableMap<String,Binding>>()
    private val menus=mutableListOf<Menu>()
    private val worker=Executors.newFixedThreadPool(2)
    private val ui=Handler(Looper.getMainLooper())
    @Volatile private var dead=false
    private var requests=0
    val documentStart get()=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    val bridgeSupported get()=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    @Synchronized fun read():List<UserScript> {if(!file.baseFile.exists())return emptyList();val arr=JSONArray(file.openRead().bufferedReader().use{it.readText()});return List(arr.length()){ScriptCodec.decode(arr.getJSONObject(it))}}
    @Synchronized fun save(items:List<UserScript>) {
        require(items.size<=50){tr("最多安装 50 个脚本")}
        val raw=JSONArray().apply{items.forEach{put(it.json())}}.toString().toByteArray();require(raw.size<=32*1024*1024){tr("脚本总大小超过限制")}
        write(file,raw)
        val ids=items.map{it.id}.toSet();values.keys().asSequence().toList().filter{it !in ids}.forEach{values.remove(it)};write(valueFile,values.toString().toByteArray())
    }
    private fun write(target:AtomicFile,raw:ByteArray){val out=target.startWrite();try{out.write(raw);target.finishWrite(out)}catch(e:Exception){target.failWrite(out);throw e}}
    private fun snapshot(id:String)=JSONObject(values.optJSONObject(id)?.toString()?:"{}")
    @android.annotation.SuppressLint("RequiresFeature")
    fun attach(web:WebView) {
        // Keep the native bridge for the lifetime of its WebView. Replacing listeners
        // while Chromium is dispatching replies can invalidate queued native callbacks.
        val scripts=runCatching{read().filter{it.enabled}}.getOrDefault(emptyList());val ids=scripts.map{it.id}.toSet()
        bindings[web].orEmpty().filter{it.script.id !in ids}.forEach{it.handle?.remove();it.handle=null;it.clients.clear();it.token=UUID.randomUUID().toString();menus.removeAll{menu->menu.binding===it}}
        val pool=bridges.getOrPut(web){linkedMapOf()};val fresh=mutableSetOf<String>()
        val list=scripts.map{script->pool[script.id]?.apply{if(this.script!=script){handle?.remove();handle=null;this.script=script;token=UUID.randomUUID().toString();clients.clear();menus.removeAll{it.binding===this}}}?:Binding(script).also{pool[script.id]=it;fresh.add(script.id)}};bindings[web]=list
        list.forEach{binding->
            if(bridgeSupported&&binding.script.id in fresh)WebViewCompat.addWebMessageListener(web,binding.name,setOf("*")){view,message,origin,main,reply->
                if(dead||binding !in bindings[view].orEmpty())return@addWebMessageListener
                if(message.type!=WebMessageCompat.TYPE_STRING)return@addWebMessageListener
                val raw=message.data.orEmpty();if(raw.length>ScriptNetwork.MAX_RESPONSE)return@addWebMessageListener
                val obj=runCatching{JSONObject(raw)}.getOrNull()?:return@addWebMessageListener
                val url=obj.optString("url");val script=binding.script
                // sourceOrigin comes from WebView, whereas view.url can still describe the
                // previous document during document-start. Authenticate against the former.
                if(obj.optString("token")!=binding.token||!script.accepts(url)||NavigationPolicy.webOrigin(url)!=NavigationPolicy.webOrigin(origin.toString())||("noframes" in script.meta&&!main))return@addWebMessageListener
                val doc=obj.optString("doc");if(doc.length !in 1..100)return@addWebMessageListener
                val client=Client(doc,url,if(main)url else view.url.orEmpty(),main,reply);val seq=obj.optInt("seq");val data=obj.optJSONObject("data")?:JSONObject()
                fun respond(value:Any?=null,error:String?=null){reply.postMessage(JSONObject().put("doc",doc).put("seq",seq).put("value",value?:JSONObject.NULL).apply{if(error!=null)put("error",error)}.toString())}
                try {when(obj.optString("op")) {
                    "hello"->{if(main&&binding.mainDoc!=doc){binding.mainDoc=doc;binding.clients.clear();menus.removeAll{it.binding===binding}};if(binding.clients.size>=64)binding.clients.clear();binding.clients[doc]=client;respond()}
                    "clipboard"->{require(script.grants("GM_setClipboard")){"Missing clipboard grant"};val text=data.getString("text");require(text.length<=131072){"Clipboard data exceeds limit"};clipboardWriter(text,data.optBoolean("html"));respond()}
                    "openTab"->{require(script.grants("GM_openInTab")){"Missing tab-open grant"};val target=data.getString("url");require(target.length<=8192&&NavigationPolicy.webOrigin(target)!=null){"Invalid tab URL"};val id=openTab?.invoke(view,NavigationPolicy.secure(target,httpsOnly()),data.optBoolean("active",true))?:error("Cannot open script tab");binding.openedTabs.add(id);if(binding in bindings[view].orEmpty())respond(id.toString())}
                    "closeTab"->{require(script.grants("GM_openInTab")){"Missing tab-open grant"};val id=data.getString("id").toLong();require(binding.openedTabs.remove(id)){"Tab was not opened by this script"};closeTab?.invoke(id);respond()}
                    "getTab"->{require(script.grants("GM_getTab")){"Missing tab grant"};respond(JSONObject(binding.tabData.toString()))}
                    "saveTab"->{require(script.grants("GM_saveTab")){"Missing tab grant"};require(data.toString().toByteArray().size<=65536){"Tab data exceeds 64 KB"};binding.tabData=JSONObject(data.toString());respond()}
                    "getTabs"->{require(script.grants("GM_getTabs")){"Missing tab grant"};val tabs=JSONObject();bridges.values.forEach{pool->pool[script.id]?.let{tabs.put(it.name,JSONObject(it.tabData.toString()))}};respond(tabs)}
                    "set","delete"->{val delete=obj.optString("op")=="delete";require(script.grants(if(delete)"GM_deleteValue"else "GM_setValue")||script.grants(if(delete)"GM_deleteValues"else "GM_setValues")){"Missing storage grant"}
                        val key=data.getString("key");require(key.length<=1024){"Value name too long"};val map=snapshot(script.id)
                        if(delete)map.remove(key)else map.put(key,data.get("value"));require(map.toString().toByteArray().size<=1024*1024){"Script storage exceeds 1 MB"}
                        val updated=JSONObject(values.toString()).put(script.id,map);require(updated.toString().toByteArray().size<=8*1024*1024){"Script storage exceeds total limit"};write(valueFile,updated.toString().toByteArray());values.put(script.id,map)
                        refreshInjections(script.id);broadcast(script.id,doc,key,if(delete)null else data.get("value"),delete);respond()
                    }
                    "menu"->{require(script.grants("GM_registerMenuCommand")){"Missing menu grant"};val key=data.getString("key");require(key.length<=100){"Menu ID too long"};menus.removeAll{it.binding===binding&&it.client.doc==doc&&it.key==key};require(menus.count{it.binding===binding}<50){"Too many script commands"};menus.add(Menu(view,binding,client,key,data.getString("label").take(200)));respond()}
                    "unmenu"->{require(script.grants("GM_unregisterMenuCommand")||script.grants("GM_registerMenuCommand")){"Missing menu grant"};menus.removeAll{it.binding===binding&&it.client.doc==doc&&it.key==data.optString("key")};respond()}
                    "xhr"->{require(script.grants("GM_xmlhttpRequest")||script.grants("GM.xmlHttpRequest")){"Missing request grant"};require(binding.inFlight<8&&requests<32){"Too many concurrent requests"};val target=data.getString("url");require(!NavigationPolicy.blocked(target,httpsOnly())&&ScriptNetwork.allowed(script,target,url)){"Request outside @connect or HTTPS-only policy"};val headers=ScriptCodec.strings(data.optJSONObject("headers")).toMutableMap();if(headers.keys.none{it.equals("User-Agent",true)})headers["User-Agent"]=view.settings.userAgentString;binding.inFlight++;requests++;val generation=binding.token
                        worker.execute{val result=runCatching{check(!dead&&binding.token==generation){"Script request canceled"};ScriptNetwork.fetch(target,data.optString("method","GET"),headers,if(data.isNull("body"))null else data.optString("body"),data.optInt("timeout",30000),allowed={!NavigationPolicy.blocked(it,httpsOnly())&&ScriptNetwork.allowed(script,it,url)}).json()}
                            ui.post{binding.inFlight--;requests--;if(binding.token==generation&&active(view,binding,client)){respond(result.getOrNull(),result.exceptionOrNull()?.message)}}}
                    }
                    else->respond(error="Unknown script operation")
                }}catch(e:Exception){respond(error=e.message?:"Script request failed")}
            }
            if(binding.handle==null)inject(web,binding)
        }
    }
    private fun active(web:WebView,binding:Binding,client:Client):Boolean {
        if(dead||binding !in bindings[web].orEmpty()||binding.clients[client.doc]==null)return false
        if(client.main&&binding.mainDoc!=client.doc)return false
        // The reply proxy belongs to its original document. URL equality is not a
        // document identity check: history.pushState may change it during a request.
        return true
    }
    @android.annotation.SuppressLint("RequiresFeature")
    private fun inject(web:WebView,binding:Binding){binding.handle?.remove();binding.handle=null;if(documentStart)binding.handle=WebViewCompat.addDocumentStartJavaScript(web,ScriptCodec.javascript(binding.script,if(bridgeSupported)binding.name else "",binding.token,snapshot(binding.script.id)),setOf("*"))}
    private fun refreshInjections(id:String){bindings.entries.toList().forEach{(web,list)->list.filter{it.script.id==id}.forEach{inject(web,it)}}}
    @android.annotation.SuppressLint("RequiresFeature")
    private fun broadcast(id:String,sourceDoc:String,key:String,value:Any?,deleted:Boolean){bindings.entries.toList().forEach{(web,list)->list.filter{it.script.id==id}.forEach{binding->binding.clients.values.filter{it.doc!=sourceDoc&&active(web,binding,it)}.forEach{client->client.reply.postMessage(JSONObject().put("doc",client.doc).put("event","change").put("key",key).put("deleted",deleted).put("value",value?:JSONObject.NULL).toString())}}}}
    @android.annotation.SuppressLint("RequiresFeature")
    fun commands(web:WebView?):List<Command> = menus.filter{it.web===web&&active(it.web,it.binding,it.client)}.map{menu->Command(menu.binding.script.name+" · "+menu.label){if(active(menu.web,menu.binding,menu.client))menu.client.reply.postMessage(JSONObject().put("doc",menu.client.doc).put("event","menu").put("key",menu.key).toString())}}
    // Document-start messages may arrive before onPageStarted. Keep clients belonging to
    // the new URL; a main-frame hello also replaces clients when reloading the same URL.
    fun navigated(web:WebView){val origin=NavigationPolicy.webOrigin(web.url.orEmpty())?:return;bindings[web]?.forEach{binding->binding.clients.entries.removeAll{NavigationPolicy.webOrigin(it.value.parent)!=origin}};menus.removeAll{it.web===web&&NavigationPolicy.webOrigin(it.client.parent)!=origin}}
    fun finished(web:WebView){if(!documentStart)bindings[web].orEmpty().filter{it.script.runAt!="document-start"}.forEach{web.evaluateJavascript(ScriptCodec.javascript(it.script,if(bridgeSupported)it.name else "",it.token,snapshot(it.script.id)),null)}}
    @android.annotation.SuppressLint("RequiresFeature")
    fun detach(web:WebView){bindings.remove(web)?.forEach{it.handle?.remove();it.clients.clear();it.token=UUID.randomUUID().toString()};bridges.remove(web);menus.removeAll{it.web===web}}
    fun close(){dead=true;bindings.keys.toList().forEach(::detach);worker.shutdownNow()}
}
