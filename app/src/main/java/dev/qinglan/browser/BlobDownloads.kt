package dev.qinglan.browser

import android.app.AlertDialog
import android.util.Base64
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ScriptHandler
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Bounded, acknowledged chunks read inside the originating page/profile. */
class BlobDownloads(private val a:BrowserActivity){
    private data class State(val tab:BrowserTab,val token:String=UUID.randomUUID().toString(),var url:String="",var file:File?=null,var size:Long=0,var received:Long=0,var mime:String="",var name:String="",var writing:Boolean=false,var dialog:AlertDialog?=null)
    private val states=WeakHashMap<WebView,State>()
    private val objectHooks=WeakHashMap<WebView,ScriptHandler>()
    private val worker=Executors.newSingleThreadExecutor()
    private var dead=false
    private val limit=64L*1024*1024
    @android.annotation.SuppressLint("RequiresFeature")
    fun attach(web:WebView,tab:BrowserTab){
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return
        states[web]=State(tab)
        WebViewCompat.addWebMessageListener(web,"qinglanBlob",setOf("*")){view,message,origin,main,reply->
            val state=states[view]?:return@addWebMessageListener
            if(!main||dead||state.tab !in a.tabs||!a.prefs.getBoolean("blobDownloads",false))return@addWebMessageListener
            if(NavigationPolicy.webOrigin(origin.toString())!=NavigationPolicy.webOrigin(state.tab.committedUrl))return@addWebMessageListener
            val obj=runCatching{JSONObject(message.data.orEmpty())}.getOrNull()?:return@addWebMessageListener
            if(obj.optString("token")!=state.token)return@addWebMessageListener
            when(obj.optString("type")){
                "request"->{if(state.tab===a.current&&state.file==null&&state.dialog==null)request(state.tab,view,obj.optString("url"),"","",obj.optString("name"))}
                "begin"->{if(state.url.isEmpty()||state.file!=null||state.dialog!=null)return@addWebMessageListener
                    val size=obj.optLong("size",-1);if(size !in 0..limit){fail(view,state,reply,tr("Blob 文件超过 64 MB 或大小无效"));return@addWebMessageListener}
                    if(state.tab!==a.current||!a.hasWindowFocus()){fail(view,state,reply,tr("请回到原网页重新下载"));return@addWebMessageListener}
                    state.size=size;state.mime=obj.optString("mime").take(200).ifBlank{"application/octet-stream"}
                    val name=a.ui.edit(tr("文件名"),state.name.ifBlank{URLUtil.guessFileName("https://blob.invalid/file","",state.mime)})
                    val col=a.ui.column(16);col.addView(a.ui.label(tr("Blob 文件由当前网页生成，保存到你选择的位置。大小：%1\$s",android.text.format.Formatter.formatFileSize(a,size)),13f));col.addView(name)
                    state.dialog=AlertDialog.Builder(a).setTitle(tr("下载文件")).setView(col).setNegativeButton(tr("取消")){_,_->fail(view,state,reply,"")}
                        .setOnCancelListener{fail(view,state,reply,"")}.setPositiveButton(tr("保存")){_,_->state.dialog=null
                            if(states[view]!==state){reply.postMessage("abort");return@setPositiveButton}
                            state.name=name.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").take(180).ifBlank{"download"}
                            state.file=File(a.cacheDir,"blob-${UUID.randomUUID()}.part");state.received=0;reply.postMessage("start")
                        }.show()
                    if(state.tab.incognito)state.dialog?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                "chunk"->{val file=state.file?:return@addWebMessageListener
                    if(state.writing||obj.optLong("offset",-1)!=state.received){fail(view,state,reply,tr("Blob 下载失败"));return@addWebMessageListener}
                    val encoded=obj.optString("data");if(encoded.length>65536){fail(view,state,reply,tr("Blob 下载失败"));return@addWebMessageListener}
                    val bytes=runCatching{Base64.decode(encoded,Base64.NO_WRAP)}.getOrNull()
                    if(bytes==null||bytes.isEmpty()||state.received+bytes.size>state.size){fail(view,state,reply,tr("Blob 下载失败"));return@addWebMessageListener}
                    state.writing=true
                    worker.execute{val written=runCatching{java.io.FileOutputStream(file,true).use{it.write(bytes)}}.isSuccess
                        a.runOnUiThread{state.writing=false;if(states[view]!==state||state.file!==file||dead){file.delete();return@runOnUiThread}
                            if(written){state.received+=bytes.size;reply.postMessage("next")}else fail(view,state,reply,tr("Blob 下载失败"))}
                    }
                }
                "end"->{val file=state.file?:return@addWebMessageListener
                    if(state.writing||state.received!=state.size){fail(view,state,reply,tr("Blob 下载失败"));return@addWebMessageListener}
                    if(state.size==0L)file.createNewFile()
                    state.file=null;state.url="";reply.postMessage("done");a.exports.saveFile(file,state.name,state.mime)
                }
                "error"->fail(view,state,reply,tr("Blob 下载失败，请在原网页重试"))
            }
        }
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))objectHooks[web]=WebViewCompat.addDocumentStartJavaScript(web,objectScript(),setOf("*"))
    }
    @android.annotation.SuppressLint("RequiresFeature") // Reply proxies exist only after the feature-guarded attach call.
    private fun fail(web:WebView,state:State,reply:JavaScriptReplyProxy?,message:String){
        state.dialog?.dismiss();state.dialog=null;state.url="";val file=state.file;state.file=null
        worker.execute{file?.delete()};reply?.postMessage("abort");if(message.isNotEmpty()&&!dead)a.toast(message)
    }
    fun navigated(web:WebView){val old=states[web]?:return;fail(web,old,null,"");states[web]=State(old.tab)}
    fun finished(web:WebView){val state=states[web]?:return
        if(a.prefs.getBoolean("blobDownloads",false))web.evaluateJavascript(script(state.token),null)
        else {navigated(web);web.evaluateJavascript("if(window.__qinglanBlobClick)document.removeEventListener('click',window.__qinglanBlobClick,true);delete window.__qinglanBlobToken;window.__qinglanPendingBlob=null;",null)}
    }
    fun request(tab:BrowserTab,web:WebView,url:String,disposition:String,mime:String,name:String=""){
        if(!a.prefs.getBoolean("blobDownloads",false)){a.toast(tr("请在设置中开启 Blob 下载"));return}
        val state=states[web]?:run{a.toast(tr("请更新系统 WebView 后使用 Blob 下载"));return}
        if(tab!==a.current||!url.startsWith("blob:")||NavigationPolicy.webOrigin(url.removePrefix("blob:"))!=NavigationPolicy.webOrigin(tab.committedUrl)){a.toast(tr("请回到生成 Blob 的原网页下载"));return}
        if(state.url.isNotEmpty()||state.file!=null){a.toast(tr("请先完成当前保存"));return}
        state.url=url;state.name=name.ifBlank{if(disposition.isNotEmpty())URLUtil.guessFileName(url,disposition,mime)else ""}
        web.evaluateJavascript(script(state.token)+";window.__qinglanReadBlob(${JSONObject.quote(url)});",null)
    }
    internal fun objectScript()="""
        (function(){
          if(window.top!==window||window.__qinglanBlobObjects)return;
          const objects=new Map();window.__qinglanBlobObjects=objects;
          const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);
          URL.createObjectURL=function(value){const url=create(value);if(value instanceof Blob&&value.size<=67108864)objects.set(url,value);return url;};
          URL.revokeObjectURL=function(url){objects.delete(String(url));return revoke(url);};
        })();
    """.trimIndent()
    internal fun script(token:String)=objectScript()+"\n"+"""
        (function(){
          if(!window.qinglanBlob)return;
          const token=${JSONObject.quote(token)};
          if(window.__qinglanBlobToken===token)return;window.__qinglanBlobToken=token;
          if(window.__qinglanBlobClick)document.removeEventListener('click',window.__qinglanBlobClick,true);
          let waiting=null,busy=false;
          qinglanBlob.onmessage=function(e){if(waiting){const w=waiting;waiting=null;w(e.data);}};
          function post(type,rest){return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{waiting=null;reject(Error('timeout'));},60000);waiting=value=>{clearTimeout(timer);if(value==='abort')reject(Error('cancel'));else resolve(value);};qinglanBlob.postMessage(JSON.stringify(Object.assign({token,type},rest)));});}
          window.__qinglanReadBlob=async function(url){if(busy)return;busy=true;try{
            // FileReader/Blob.arrayBuffer do not make requests and are unaffected by connect-src.
            const pending=window.__qinglanPendingBlob;
            let blob=pending&&pending.url===url?pending.blob:window.__qinglanBlobObjects.get(url);
            if(pending&&pending.url===url)window.__qinglanPendingBlob=null;
            if(!blob)blob=await (await fetch(url)).blob();if(blob.size>67108864)throw Error('size');
            if(await post('begin',{size:blob.size,mime:blob.type})!=='start')throw Error('cancel');
            for(let offset=0;offset<blob.size;offset+=49152){const bytes=new Uint8Array(await blob.slice(offset,offset+49152).arrayBuffer());let binary='';for(let i=0;i<bytes.length;i++)binary+=String.fromCharCode(bytes[i]);await post('chunk',{offset,data:btoa(binary)});}
            await post('end',{});
          }catch(e){qinglanBlob.postMessage(JSON.stringify({token,type:'error'}));}finally{busy=false;}};
          window.__qinglanBlobClick=function(e){const link=e.target.closest&&e.target.closest('a');if(link&&link.href.startsWith('blob:')&&link.hasAttribute('download')){e.preventDefault();const blob=window.__qinglanBlobObjects.get(link.href);window.__qinglanPendingBlob=blob?{url:link.href,blob}:null;qinglanBlob.postMessage(JSON.stringify({token,type:'request',url:link.href,name:link.download}));}};
          document.addEventListener('click',window.__qinglanBlobClick,true);
        })();
    """.trimIndent()
    fun detach(web:WebView){objectHooks.remove(web)?.remove();val state=states.remove(web)?:return;state.dialog?.dismiss();val file=state.file;worker.execute{file?.delete()}}
    fun close(){dead=true;objectHooks.values.toList().forEach{it.remove()};objectHooks.clear();states.values.toList().forEach{it.dialog?.dismiss();val file=it.file;worker.execute{file?.delete()}};states.clear();worker.shutdown()}
}
