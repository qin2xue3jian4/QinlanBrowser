package dev.qinglan.browser

import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.widget.LinearLayout
import org.json.JSONObject
import org.json.JSONTokener

/** Extract on a clone; render only inert native text. No reader network requests or HTML execution. */
class ReaderPanels(private val p:BrowserPanels) {
    private val a get()=p.a
    private val u get()=p.u
    private val library by lazy { a.assets.open("reader/Readability.js").bufferedReader().use{it.readText()} }
    private val archive by lazy { ReadingList(a) }
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    fun close(){worker.shutdown()}
    private fun <T> work(task:()->T,done:(T)->Unit){worker.execute{val result=runCatching(task);a.runOnUiThread{if(!a.isFinishing&&!a.isDestroyed)result.onSuccess(done).onFailure{a.toast(it.message?:"文章操作失败")}}}}
    fun saved(){var query="";p.pages.show("离线文章"){col->
        col.addView(u.label("在阅读模式中保存正文，断网也可阅读。最多 100 篇 / 20 MB；不含图片，不包含在设置备份中。",13f,u.muted))
        val input=u.edit("搜索已保存文章",query);col.addView(input);val rows=u.column();col.addView(rows)
        fun render(){work({archive.list()}){items->if(!rows.isAttachedToWindow)return@work;rows.removeAllViews()
            val found=items.filter{it.title.contains(query,true)||it.url.contains(query,true)}
            if(found.isEmpty())rows.addView(u.label(if(items.isEmpty())"暂无离线文章，打开文章后选择阅读模式保存。"else"没有匹配的文章"))
            found.forEach{item->val row=u.row();row.addView(u.item(item.title,"${android.net.Uri.parse(item.url).host.orEmpty()} · ${android.text.format.Formatter.formatFileSize(a,item.bytes.toLong())}"){work({archive.text(item)}){text->if(row.isAttachedToWindow)this@ReaderPanels.render(item.title,text,item.url,false,true)}},LinearLayout.LayoutParams(0,-2,1f));row.addView(u.icon("close","移除 ${item.title.take(40)}"){p.confirm("移除离线文章？",item.title){work({archive.delete(item)}){if(rows.isAttachedToWindow)p.pages.refresh()}}});rows.addView(row)}
        }}
        input.onChange{query=it;render()};rows.post{render()}
    }}
    internal fun extractionScript()="""(()=>{try{
        if(document.getElementsByTagName('*').length>20000)return null;
        $library
        const article=new Readability(document.cloneNode(true),{maxElemsToParse:20000,charThreshold:140,serializer:el=>el}).parse();
        if(!article)return null;
        const root=article.content;
        root.querySelectorAll('script,style,noscript,form,input,textarea,select,button').forEach(e=>e.remove());
        root.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,tr,br').forEach(e=>e.appendChild(document.createTextNode('\n\n')));
        const text=root.textContent.replace(/[ \t]+/g,' ').replace(/\n[ \t]+/g,'\n').replace(/\n{3,}/g,'\n\n').trim();
        return {title:article.title||document.title,text:text.slice(0,200000),truncated:text.length>200000};
    }catch(e){return null}})()"""
    fun show(){
        val tab=a.current;val web=tab?.web
        if(web==null||!a.isHttp(a.currentUrl)){a.toast("请先打开文章网页");return}
        val url=a.currentUrl
        a.toast("正在提取正文")
        web.evaluateJavascript(extractionScript()){raw->
            if(a.isFinishing||a.isDestroyed||a.current!==tab||a.currentUrl!=url)return@evaluateJavascript
            val article=runCatching{JSONTokener(raw).nextValue() as? JSONObject}.getOrNull()
            val text=article?.optString("text").orEmpty()
            if(text.length<80){a.toast("未识别到足够正文，请在原网页阅读");return@evaluateJavascript}
            render(article!!.optString("title").take(300),text,url,article.optBoolean("truncated"))
        }
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Single taps reveal tools; the header offers an accessible button. Selection remains native.
    private fun render(title:String,text:String,url:String,truncated:Boolean,offline:Boolean=false){
        var showTools:(()->Unit)?=null
        p.pages.show(if(offline)"离线阅读"else"阅读模式",PageHost.HeaderAction("tools","阅读工具"){showTools?.invoke()}){col->
            col.keepScreenOn=p.prefs.getBoolean("readerKeepAwake",false)
            var size=p.prefs.getInt("readerSize",20).coerceIn(14,32)
            col.addView(u.title(title))
            col.addView(u.label(android.net.Uri.parse(url).host.orEmpty()+" · 轻点正文打开工具",12f,u.muted))
            val body=u.label(text,size.toFloat()).apply{setTextIsSelectable(true);setLineSpacing(0f,p.prefs.getInt("readerSpacing",135).coerceIn(110,160)/100f)}
            var sheet:android.app.Dialog?=null
            fun tools(){
                if(!col.isAttachedToWindow||sheet?.isShowing==true)return
                val panel=u.column(16);panel.addView(u.title("阅读工具"))
                if(a.isIncognito)panel.addView(u.label("保存离线和导出的文件会保留，退出无痕不会删除。",13f,u.muted))
                val controls=u.row();val sizeLabel=u.label("${size}号",12f)
                fun change(delta:Int){size=(size+delta).coerceIn(14,32);p.prefs.edit().putInt("readerSize",size).apply();body.textSize=size.toFloat();sizeLabel.text="${size}号"}
                controls.addView(u.button("A−"){change(-2)},LinearLayout.LayoutParams(0,-2,1f));controls.addView(sizeLabel)
                controls.addView(u.button("A＋"){change(2)},LinearLayout.LayoutParams(0,-2,1f));panel.addView(controls)
                val speechControls=u.row();lateinit var play:android.widget.Button
                play=u.button("朗读"){when{!a.speech.matches(text)->a.speech.readText(text);a.speech.speaking->a.speech.pause();a.speech.paused->a.speech.resume();else->a.speech.readText(text)}}
                speechControls.addView(play,LinearLayout.LayoutParams(0,-2,1f));speechControls.addView(u.button("停止"){a.speech.stop()},LinearLayout.LayoutParams(0,-2,1f))
                speechControls.addView(u.button("语速"){
                    val spec=SettingCatalog.find("speechRate")!!
                    android.app.AlertDialog.Builder(a).setTitle("朗读语速").setSingleChoiceItems(spec.labels.toTypedArray(),spec.values.indexOf(p.prefs.getInt("speechRate",100))){dialog,index->p.prefs.edit().putInt("speechRate",spec.values[index] as Int).apply();dialog.dismiss();a.toast("下次开始或继续朗读时生效")}.setNegativeButton("取消",null).show()
                },LinearLayout.LayoutParams(0,-2,1f));panel.addView(speechControls)
                a.speech.observe(play){play.text=if(!a.speech.matches(text))"朗读"else if(a.speech.speaking)"暂停"else if(a.speech.paused)"继续"else"朗读"}
                panel.addView(u.item(if(offline)"打开原网页"else"保存离线"){
                    sheet?.dismiss()
                    if(offline){p.pages.close();a.open(url,true)}else work({archive.save(title,url,text)}){a.toast("已保存，可在离线文章中阅读")}
                })
                panel.addView(u.item("导出 TXT"){sheet?.dismiss();a.writeDocument(title.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(80)+".txt","text/plain","$title\n$url\n\n$text")})
                sheet=p.dialog(panel)
            }
            showTools=::tools
            val taps=android.view.GestureDetector(a,object:android.view.GestureDetector.SimpleOnGestureListener(){
                override fun onDown(e:android.view.MotionEvent)=true
                override fun onSingleTapConfirmed(e:android.view.MotionEvent):Boolean{if(body.selectionStart==body.selectionEnd)tools();return false}
            })
            body.setOnTouchListener{_,event->taps.onTouchEvent(event);false}
            col.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener{
                override fun onViewAttachedToWindow(v:android.view.View){}
                override fun onViewDetachedFromWindow(v:android.view.View){sheet?.dismiss();sheet=null}
            })
            col.addView(body)
            col.addView(u.label("本地提取可能遗漏图片、表格或分页内容。"+(if(truncated)" 本文超过 20 万字符，当前仅展示前 20 万字符。"else""),12f,u.muted))
        }
    }
    fun printPage(){
        val web=a.current?.web
        if(web==null||!a.isHttp(a.currentUrl)){a.toast("请先打开网页");return}
        runCatching{(a.getSystemService(Context.PRINT_SERVICE) as PrintManager).print((a.current?.title?:"网页").take(80),web.createPrintDocumentAdapter("清岚网页"),null)}
            .onFailure{a.toast("系统打印暂不可用")}
    }
}
