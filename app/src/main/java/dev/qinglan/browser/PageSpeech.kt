package dev.qinglan.browser

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONTokener
import java.util.Locale

class PageSpeech(private val a:BrowserActivity){
    private var tts:TextToSpeech?=null
    private var ready=false
    private var chunks=listOf<String>()
    private var index=0
    var speaking=false;private set
    var paused=false;private set
    var onChange:(()->Unit)?=null
    fun readPage(){val web=a.current?.web?:return
        web.evaluateJavascript("""(()=>{const root=document.querySelector('article')||document.querySelector('main')||document.body;if(!root)return '';const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let out='',n;while((n=walker.nextNode())&&out.length<80000){const p=n.parentElement;if(!p||p.closest('script,style,noscript,input,textarea,select,nav,header,footer,[aria-hidden="true"]')||!p.getClientRects().length)continue;const s=n.textContent.trim();if(s)out+=s+'\n';}return out.slice(0,80000);})()"""){raw->val text=runCatching{JSONTokener(raw).nextValue()as? String}.getOrNull().orEmpty();if(text.isBlank()){a.toast("本页没有可朗读的文字");return@evaluateJavascript};stop();chunks=text.chunked(1200);index=0;paused=false
            if(tts==null){tts=TextToSpeech(a){status->ready=status==TextToSpeech.SUCCESS;if(ready){tts?.language=if(text.any{it in '\u4e00'..'\u9fff'})Locale.SIMPLIFIED_CHINESE else Locale.getDefault();listen();speak()}else a.toast("系统未提供可用的朗读引擎")}}else if(ready)speak()
        }
    }
    private fun listen(){tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){};override fun onDone(id:String?){a.runOnUiThread{if(speaking&&id==index.toString()){index++;speak()}}};@Deprecated("Deprecated")override fun onError(id:String?){a.runOnUiThread{stop();a.toast("朗读失败，请检查系统语音引擎和语言数据")}}})}
    private fun speak(){if(index>=chunks.size){stop();return};speaking=true;paused=false;val result=tts?.speak(chunks[index],TextToSpeech.QUEUE_FLUSH,null,index.toString());if(result==TextToSpeech.ERROR){stop();a.toast("该语言暂不可朗读，请检查系统语音设置")};onChange?.invoke()}
    fun pause(){if(!speaking)return;speaking=false;paused=true;tts?.stop();onChange?.invoke()}
    fun resume(){if(ready&&paused)speak()}
    fun stop(){speaking=false;paused=false;tts?.stop();chunks=emptyList();index=0;onChange?.invoke()}
    fun close(){stop();tts?.shutdown();tts=null}
}
