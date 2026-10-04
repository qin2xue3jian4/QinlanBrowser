package dev.qinglan.browser

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedArticle(val id:String,val title:String,val url:String,val time:Long,val bytes:Int)
/** Bounded private text snapshots. New files commit before the atomic index replaces the old one. */
class ReadingList(context:Context,folder:String="reading") {
    private val dir=File(context.filesDir,folder).apply{mkdirs()}
    private val index=AtomicFile(File(dir,"index.json"))
    @Synchronized fun list():List<SavedArticle>{
        if(!index.baseFile.exists()&&!File(dir,"index.json.bak").exists())return emptyList()
        val arr=JSONArray(index.openRead().bufferedReader().use{it.readText()});require(arr.length()<=100){"离线文章索引异常"}
        return List(arr.length()){i->val o=arr.getJSONObject(i);val id=o.getString("id");require(id.matches(Regex("[a-f0-9-]{36}"))){"文章编号无效"};SavedArticle(id,o.getString("title"),o.getString("url"),o.getLong("time"),o.getInt("bytes"))}
    }
    @Synchronized fun text(item:SavedArticle):String {
        require(item.id.matches(Regex("[a-f0-9-]{36}")));val file=File(dir,"${item.id}.txt");require(file.length()<=800000){"文章文件过大"};return file.readText()
    }
    @Synchronized fun save(title:String,url:String,text:String):SavedArticle {
        require(text.isNotBlank()&&text.length<=200000){"正文为空或超过 20 万字符"}
        val old=list();val previous=old.firstOrNull{it.url==url};val kept=old.filterNot{it.url==url};val bytes=text.toByteArray()
        require(kept.size<100&&kept.sumOf{it.bytes.toLong()}+bytes.size<=20*1024*1024){"离线文章上限为 100 篇 / 20 MB，请先移除一些文章"}
        val item=SavedArticle(UUID.randomUUID().toString(),title.take(300),url.take(8192),System.currentTimeMillis(),bytes.size)
        val file=File(dir,"${item.id}.txt");file.writeBytes(bytes)
        try{writeIndex(listOf(item)+kept)}catch(e:Exception){file.delete();throw e}
        previous?.let{File(dir,"${it.id}.txt").delete()};return item
    }
    @Synchronized fun delete(item:SavedArticle){writeIndex(list().filterNot{it.id==item.id});File(dir,"${item.id}.txt").delete()}
    private fun writeIndex(items:List<SavedArticle>){val arr=JSONArray();items.forEach{arr.put(JSONObject().put("id",it.id).put("title",it.title).put("url",it.url).put("time",it.time).put("bytes",it.bytes))};val stream=index.startWrite();try{stream.write(arr.toString().toByteArray());index.finishWrite(stream)}catch(e:Exception){index.failWrite(stream);throw e}}
}
