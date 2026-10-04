package dev.qinglan.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.*
import android.view.Gravity
import java.io.File
import java.security.MessageDigest

/** Only favicons supplied by visited WebViews; no third-party icon lookup service. */
class SiteIcons(context:Context){
    private val dir=File(context.filesDir,"site-icons").apply{mkdirs()}
    private val cache=LruCache<String,Bitmap>(100)
    private fun key(url:String)=runCatching{java.net.URI(url).host?.lowercase()}.getOrNull().orEmpty()
    private fun file(host:String)=File(dir,MessageDigest.getInstance("SHA-256").digest(host.toByteArray()).joinToString(""){"%02x".format(it)}+".png")
    fun save(url:String,bitmap:Bitmap){val host=key(url);if(host.isBlank())return
        runCatching{val icon=Bitmap.createScaledBitmap(bitmap,48,48,true);cache.put(host,icon);file(host).outputStream().use{icon.compress(Bitmap.CompressFormat.PNG,100,it)}}
        if((dir.listFiles()?.size?:0)>400)dir.listFiles()?.sortedBy{it.lastModified()}?.take(50)?.forEach{it.delete()}
    }
    fun view(ui:Ui,title:String,url:String,size:Int=32):android.view.View{
        val host=key(url);val icon=cache.get(host)?:runCatching{BitmapFactory.decodeFile(file(host).path)?.also{cache.put(host,it)}}.getOrNull()
        return if(icon!=null)ImageView(ui.context).apply{setImageBitmap(icon);scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));contentDescription="网站图标";layoutParams=LinearLayout.LayoutParams(ui.dp(size),ui.dp(size))}
        else ui.label(title.take(1).uppercase().ifBlank{"·"},(size*.48f),ui.accent).apply{setPadding(0,0,0,0);gravity=Gravity.CENTER;background=ui.round(ui.soft,8);layoutParams=LinearLayout.LayoutParams(ui.dp(size),ui.dp(size))}
    }
}
