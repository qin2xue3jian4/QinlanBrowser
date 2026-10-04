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
    private var generation=0
    private var currentText:String?=null
    private var language=Locale.getDefault()
    fun matches(text:String)=currentText==text.take(80000)
    var speaking=false;private set
    var paused=false;private set
    var onChange:(()->Unit)?=null
    fun observe(view:android.view.View,update:()->Unit){
        view.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(v:android.view.View){onChange=update;update()}
            override fun onViewDetachedFromWindow(v:android.view.View){if(onChange===update)onChange=null}
        })
    }
    fun readPage(){val web=a.current?.web?:return;val url=a.currentUrl
        web.evaluateJavascript("""(()=>{const root=document.querySelector('article')||document.querySelector('main')||document.body;if(!root)return '';const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let out='',n;while((n=walker.nextNode())&&out.length<80000){const p=n.parentElement;if(!p||p.closest('script,style,noscript,input,textarea,select,nav,header,footer,[aria-hidden="true"]')||!p.getClientRects().length)continue;const s=n.textContent.trim();if(s)out+=s+'\n';}return out.slice(0,80000);})()"""){raw->if(a.isFinishing||a.isDestroyed||a.current?.web!==web||a.currentUrl!=url)return@evaluateJavascript;readText(runCatching{JSONTokener(raw).nextValue()as? String}.getOrNull().orEmpty())}
    }
    fun readText(source:String){val text=source.take(80000);if(text.isBlank()){a.toast("本页没有可朗读的文字");return};stop();currentText=text;chunks=text.chunked(1200);index=0;paused=false
            language=if(text.any{it in '\u4e00'..'\u9fff'})Locale.SIMPLIFIED_CHINESE else Locale.getDefault()
            if(tts==null){tts=TextToSpeech(a){status->ready=status==TextToSpeech.SUCCESS;if(ready){tts?.language=language;listen();if(!paused)speak()}else{tts?.shutdown();tts=null;a.toast("系统未提供可用的朗读引擎")}}}else if(ready){tts?.language=language;speak()}else a.toast("语音引擎正在初始化，请稍后重试")
    }
    private fun listen(){tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){};override fun onDone(id:String?){a.runOnUiThread{if(speaking&&id=="$generation:$index"){index++;speak()}}};@Deprecated("Deprecated")override fun onError(id:String?){a.runOnUiThread{if(id=="$generation:$index"){stop();a.toast("朗读失败，请检查系统语音引擎和语言数据")}}}})}
    private fun speak(){if(index>=chunks.size){stop();return};speaking=true;paused=false;tts?.setSpeechRate(a.prefs.getInt("speechRate",100).coerceIn(75,200)/100f);val result=tts?.speak(chunks[index],TextToSpeech.QUEUE_FLUSH,null,"$generation:$index");if(result==TextToSpeech.ERROR){stop();a.toast("该语言暂不可朗读，请检查系统语音设置")};onChange?.invoke()}
    fun pause(){if(chunks.isEmpty())return;generation++;speaking=false;paused=true;tts?.stop();onChange?.invoke()}
    fun resume(){if(ready&&paused)speak()}
    fun stop(){generation++;speaking=false;paused=false;tts?.stop();chunks=emptyList();currentText=null;index=0;onChange?.invoke()}
    fun close(){stop();tts?.shutdown();tts=null}
}
