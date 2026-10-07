package dev.qinglan.browser

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.*
import android.webkit.WebView
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.min

class PageExports(private val a:BrowserActivity){
    private val u get()=a.ui
    private val worker=Executors.newSingleThreadExecutor()
    private var pending:File?=null
    private var image:Bitmap?=null
    private var dead=false
    fun saveFile(file:File,name:String,mime:String){
        if(dead){file.delete();return}
        if(pending!=null){file.delete();a.toast(tr("请先完成当前保存"));return}
        pending=file
        runCatching{a.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),107)}
            .onFailure{pending=null;file.delete();a.toast(tr("无法打开文件选择器"))}
    }
    fun result(code:Int,uri:Uri?){val file=pending?:return;pending=null
        if(code!=Activity.RESULT_OK||uri==null){file.delete();return}
        worker.execute{val ok=runCatching{a.contentResolver.openOutputStream(uri,"wt")?.use{out->file.inputStream().use{it.copyTo(out)}}?:error("No output")}.isSuccess
            file.delete();a.runOnUiThread{if(!dead)a.toast(if(ok)tr("已保存")else tr("导出失败"))}}
    }
    fun source(){val tab=a.current;val web=tab?.web?.takeIf{a.isHttp(a.currentUrl)}?:run{a.toast(tr("请先打开网页"));return}
        web.evaluateJavascript("document.documentElement.outerHTML"){raw->if(a.current!==tab)return@evaluateJavascript
            val html=runCatching{JSONArray("[$raw]").getString(0)}.getOrNull()?:return@evaluateJavascript
            a.panels.pages.show(tr("网页源码"),PageHost.HeaderAction("download",tr("导出 HTML")){a.writeDocument("page.html","text/html",html)}){col->
                col.addView(u.label(tab.url,12f,u.muted));col.addView(u.label(html,12f).apply{typeface=Typeface.MONOSPACE;setTextIsSelectable(true)})
            }
        }
    }
    fun ua(){if(!a.isHttp(a.currentUrl)){a.toast(tr("请先打开网页"));return};val url=a.currentUrl;val host=a.store.siteKey(url)
        a.panels.pages.show(tr("浏览器标识（UA）")){col->
            col.addView(u.label(host,14f,u.muted));col.addView(u.label(a.current?.web?.settings?.userAgentString.orEmpty(),12f).apply{setTextIsSelectable(true)})
            fun apply(desktop:Boolean,custom:String=""){val edit=a.prefs.edit().putBoolean("site.$host.desktop",desktop);if(custom.isBlank())edit.remove("site.$host.ua")else edit.putString("site.$host.ua",custom);edit.apply();a.panels.pages.close();a.reload()}
            col.addView(u.button(tr("手机标识")){apply(false)})
            col.addView(u.button(tr("电脑标识")){apply(true)})
            val input=u.edit(tr("自定义 UA"),a.prefs.getString("site.$host.ua","").orEmpty(),true);col.addView(input)
            col.addView(u.button(tr("应用自定义 UA"),true){val value=input.text.toString().trim();if(value.isEmpty()||value.length>2048||value.any(Char::isISOControl)){a.toast(tr("UA 需为 1–2048 个字符且不能包含换行"));return@button};apply(false,value)})
            col.addView(u.button(tr("恢复继承默认")){a.prefs.edit().remove("site.$host.ua").remove("site.$host.desktop").apply();a.panels.pages.close();a.reload()})
        }
    }
    fun qr(url:String):Bitmap {
        val matrix=MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,768,768,mapOf(EncodeHintType.CHARACTER_SET to "UTF-8",EncodeHintType.MARGIN to 2))
        val pixels=IntArray(matrix.width*matrix.height){i->if(matrix[i%matrix.width,i/matrix.width])Color.BLACK else Color.WHITE}
        return Bitmap.createBitmap(pixels,matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
    }
    fun share(){val tab=a.current?.takeIf{a.isHttp(it.url)}?:run{a.toast(tr("请先打开网页"));return};val title=tab.title;val url=tab.url
        val format=a.prefs.getString("shareFormat","ask")
        if(format!="ask"){send(format.orEmpty(),title,url);return}
        val bitmap=runCatching{qr(url)}.getOrNull()
        a.panels.pages.show(tr("分享链接")){col->
            col.addView(u.title(title));col.addView(u.label(url,14f,u.muted).apply{setTextIsSelectable(true)})
            if(bitmap!=null)col.addView(ImageView(a).apply{setImageBitmap(bitmap);contentDescription=tr("网址二维码")},LinearLayout.LayoutParams(-1,u.dp(220)))
            listOf("url" to tr("仅网址"),"title" to tr("标题 + 网址"),"qr" to tr("二维码")).forEach{(id,label)->col.addView(u.button(label){a.panels.pages.close();send(id,title,url)})}
        }
    }
    private fun send(format:String,title:String,url:String){
        if(format=="qr")worker.execute{val result=runCatching{
            val dir=File(a.cacheDir,"shared").apply{mkdirs()};dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>86400000}?.forEach{it.delete()}
            val file=File(dir,"qr-${UUID.randomUUID()}.png");val bitmap=qr(url);try{file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
            Uri.Builder().scheme("content").authority("${a.packageName}.images").appendPath(file.name).build()
        };a.runOnUiThread{if(!dead)result.fold({uri->chooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).apply{clipData=ClipData.newUri(a.contentResolver,tr("二维码"),uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})},{a.toast(tr("二维码生成失败"))})}}
        else chooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,if(format=="title")"$title\n$url"else url).putExtra(Intent.EXTRA_SUBJECT,title))
    }
    private fun chooser(intent:Intent){runCatching{a.startActivity(Intent.createChooser(intent,tr("分享链接")))}.onFailure{a.toast(tr("未找到分享应用"))}}
    fun screenshot(){if(a.current?.web==null||!a.isHttp(a.currentUrl)){a.toast(tr("请先打开网页"));return}
        a.panels.choose(tr("网页截图"),listOf(tr("整个网页"),tr("当前区域"))){index->capture(index==0)}
    }
    @Suppress("DEPRECATION")
    internal fun snapshot(web:WebView,whole:Boolean):Bitmap {
        require(web.width>0&&web.height>0)
        val height=if(whole)maxOf(web.height,(web.contentHeight.toDouble()*web.scale).toInt())else web.height
        // Bound both bitmap memory and dimensions, retaining the entire page at reduced resolution.
        val factor=min(1.0,min(32760.0/height,kotlin.math.sqrt(24_000_000.0/(web.width.toDouble()*height))))
        val bitmap=Bitmap.createBitmap(maxOf(1,(web.width*factor).toInt()),maxOf(1,(height*factor).toInt()),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(factor.toFloat(),factor.toFloat())
        if(whole)canvas.translate(-web.scrollX.toFloat(),-web.scrollY.toFloat())
        web.draw(canvas);return bitmap
    }
    private fun capture(whole:Boolean){val web=a.current?.web?:return
        val bitmap=runCatching{snapshot(web,whole)}.getOrElse{a.toast(tr("截图失败，页面可能过大或含受保护内容"));return}
        image?.recycle();image=bitmap
        val crop=CropPreview(a,bitmap);var format="png"
        a.panels.pages.showFixed(tr("截图范围与格式"),onClose={if(image===bitmap){image=null;bitmap.recycle()}}){col->
            col.addView(u.label(tr("拖动框选范围，默认全部。长页预览可双指缩放与移动。"),12f,u.muted))
            col.addView(crop,LinearLayout.LayoutParams(-1,0,1f))
            col.addView(u.button(tr("选择全部")){crop.reset()})
            val formats=u.row();listOf("png" to "PNG","jpeg" to "JPEG","pdf" to "PDF").forEach{(id,label)->formats.addView(RadioButton(a).apply{text=label;setTextColor(u.text);isChecked=id==format;setOnClickListener{format=id;for(i in 0 until formats.childCount)(formats.getChildAt(i) as RadioButton).isChecked=formats.getChildAt(i)===this}},LinearLayout.LayoutParams(0,u.dp(48),1f))};col.addView(formats)
            val buttons=u.row();buttons.addView(u.button(tr("取消")){a.panels.pages.back()},LinearLayout.LayoutParams(0,u.dp(48),1f))
            buttons.addView(u.button(tr("保存"),true){
                val selection=crop.selection();val chosen=format
                // Make a private crop before closing the route recycles the preview.
                val output=Bitmap.createBitmap(bitmap,selection.left,selection.top,selection.width(),selection.height()).let{if(it===bitmap)bitmap.copy(Bitmap.Config.ARGB_8888,false)else it}
                a.panels.pages.back()
                worker.execute{val file=File(a.cacheDir,"export-${UUID.randomUUID()}.$chosen");val ok=runCatching{file.outputStream().use{stream->
                    if(chosen=="pdf"){val doc=PdfDocument();try{val page=doc.startPage(PdfDocument.PageInfo.Builder(output.width,output.height,1).create());page.canvas.drawBitmap(output,0f,0f,null);doc.finishPage(page);doc.writeTo(stream)}finally{doc.close()}}
                    else check(output.compress(if(chosen=="jpeg")Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,95,stream))
                }}.isSuccess;output.recycle();a.runOnUiThread{if(ok)saveFile(file,"qinglan-${System.currentTimeMillis()}.$chosen",if(chosen=="pdf")"application/pdf"else if(chosen=="jpeg")"image/jpeg"else "image/png")else{file.delete();a.toast(tr("导出失败"))}}}
            },LinearLayout.LayoutParams(0,u.dp(48),1f));col.addView(buttons)
        }
    }
    fun close(){dead=true;pending?.delete();pending=null;image?.recycle();image=null;worker.shutdown()}
}

/** Image-space crop, with zoom/pan for choosing a readable part of a long page. */
@android.annotation.SuppressLint("ViewConstructor") // Created only for a captured bitmap, never inflated from XML.
internal class CropPreview(a:BrowserActivity,private val bitmap:Bitmap):View(a){
    private val crop=RectF(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat())
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds=RectF()
    private val chosen=RectF()
    private var zoom=1f;private var offsetX=0f;private var offsetY=0f
    private var startX=0f;private var startY=0f;private var lastX=0f;private var lastY=0f;private var dragging=false;private var pinching=false
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(detector:ScaleGestureDetector):Boolean{zoom=(zoom*detector.scaleFactor).coerceIn(1f,30f);invalidate();return true}})
    init{contentDescription=tr("截图框选预览");isFocusable=true;setLayerType(LAYER_TYPE_SOFTWARE,null)}
    fun reset(){crop.set(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat());zoom=1f;offsetX=0f;offsetY=0f;invalidate()}
    fun selection():Rect {val left=crop.left.toInt().coerceIn(0,bitmap.width-1);val top=crop.top.toInt().coerceIn(0,bitmap.height-1);return Rect(left,top,maxOf(left+1,crop.right.toInt().coerceAtMost(bitmap.width)),maxOf(top+1,crop.bottom.toInt().coerceAtMost(bitmap.height)))}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);if(bitmap.isRecycled)return
        val fit=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)*zoom
        bounds.set((width-bitmap.width*fit)/2+offsetX,(height-bitmap.height*fit)/2+offsetY,(width+bitmap.width*fit)/2+offsetX,(height+bitmap.height*fit)/2+offsetY)
        paint.color=Color.DKGRAY;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint);paint.color=Color.WHITE;canvas.drawBitmap(bitmap,null,bounds,paint)
        chosen.set(bounds.left+crop.left*fit,bounds.top+crop.top*fit,bounds.left+crop.right*fit,bounds.top+crop.bottom*fit)
        canvas.save();canvas.clipOutRect(chosen);canvas.drawColor(0x88000000.toInt());canvas.restore()
        paint.style=Paint.Style.STROKE;paint.color=Color.CYAN;paint.strokeWidth=3*resources.displayMetrics.density;canvas.drawRect(chosen,paint);paint.style=Paint.Style.FILL
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event:MotionEvent):Boolean {scale.onTouchEvent(event);parent?.requestDisallowInterceptTouchEvent(true)
        if(event.pointerCount>1){pinching=true;if(event.actionMasked==MotionEvent.ACTION_MOVE){offsetX+=event.x-lastX;offsetY+=event.y-lastY};lastX=event.x;lastY=event.y;invalidate();return true}
        val factor=bounds.width()/bitmap.width;if(factor<=0)return true
        val x=((event.x-bounds.left)/factor).coerceIn(0f,bitmap.width.toFloat());val y=((event.y-bounds.top)/factor).coerceIn(0f,bitmap.height.toFloat())
        when(event.actionMasked){MotionEvent.ACTION_DOWN->{pinching=false;dragging=false;startX=x;startY=y;lastX=event.x;lastY=event.y}
            MotionEvent.ACTION_MOVE->if(!pinching&&kotlin.math.abs(event.x-lastX)+kotlin.math.abs(event.y-lastY)>8){dragging=true;crop.set(minOf(startX,x),minOf(startY,y),maxOf(startX,x),maxOf(startY,y));invalidate()}
            MotionEvent.ACTION_UP->{if(!dragging)performClick();dragging=false;pinching=false}
        };return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
