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
    private fun render(title:String,text:String,url:String,truncated:Boolean){p.pages.show("阅读模式"){col->
        var size=p.prefs.getInt("readerSize",20).coerceIn(14,32)
        col.addView(u.title(title))
        col.addView(u.label(android.net.Uri.parse(url).host.orEmpty()+" · 纯文字阅读",12f,u.muted))
        val controls=u.row();val body=u.label(text,size.toFloat()).apply{setTextIsSelectable(true);setLineSpacing(u.dp(5).toFloat(),1.25f)}
        val sizeLabel=u.label("${size}sp",12f)
        fun change(delta:Int){size=(size+delta).coerceIn(14,32);p.prefs.edit().putInt("readerSize",size).apply();body.textSize=size.toFloat();sizeLabel.text="${size}sp"}
        controls.addView(u.button("A−"){change(-2)},LinearLayout.LayoutParams(0,-2,1f));controls.addView(sizeLabel)
        controls.addView(u.button("A＋"){change(2)},LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(u.button("朗读"){a.speech.readText(text);a.toast("可在菜单的朗读控制中暂停或停止")},LinearLayout.LayoutParams(0,-2,1f))
        col.addView(controls)
        col.addView(u.label("本地提取可能遗漏图片、表格或分页内容；返回即可继续原网页。"+(if(truncated)" 本文超过 20 万字符，当前仅展示前 20 万字符。"else""),12f,u.muted))
        col.addView(body)
        col.addView(u.button("导出正文 TXT"){a.writeDocument(title.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(80)+".txt","text/plain","$title\n$url\n\n$text")})
    }}
    fun printPage(){
        val web=a.current?.web
        if(web==null||!a.isHttp(a.currentUrl)){a.toast("请先打开网页");return}
        runCatching{(a.getSystemService(Context.PRINT_SERVICE) as PrintManager).print((a.current?.title?:"网页").take(80),web.createPrintDocumentAdapter("清岚网页"),null)}
            .onFailure{a.toast("系统打印暂不可用")}
    }
}
