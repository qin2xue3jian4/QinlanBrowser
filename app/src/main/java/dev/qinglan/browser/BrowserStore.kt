package dev.qinglan.browser

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HomeItem(val id: String = UUID.randomUUID().toString(), var title: String, var url: String = "", var parent: String = "", val folder: Boolean = false) {
    fun json() = JSONObject().put("id", id).put("title", title).put("url", url).put("parent", parent).put("folder", folder)
}
data class Visit(val title: String, val url: String, val time: Long = System.currentTimeMillis(),val id:String=UUID.randomUUID().toString(),val parent:String="",val folder:Boolean=false) {
    fun json()=JSONObject().put("title",title).put("url",url).put("time",time).put("id",id).put("parent",parent).put("folder",folder)
}

class BrowserStore(context: Context) {
    val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val dataFile = AtomicFile(File(context.filesDir, "library.json"))
    val home = mutableListOf<HomeItem>()
    val bookmarks = mutableListOf<Visit>()
    val history = mutableListOf<Visit>()
    init {
        val data = runCatching { JSONObject(dataFile.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
        if (data != null) {
            data.optJSONArray("home")?.let { a -> for (i in 0 until a.length()) { val o=a.getJSONObject(i);home.add(HomeItem(o.getString("id"),o.getString("title"),o.optString("url"),o.optString("parent"),o.optBoolean("folder"))) } }
            fun visits(key: String, list: MutableList<Visit>) { data.optJSONArray(key)?.let { a -> for(i in 0 until a.length()) { val o=a.getJSONObject(i);list.add(Visit(o.optString("title"),o.getString("url"),o.optLong("time"),o.optString("id").ifBlank{UUID.randomUUID().toString()},o.optString("parent"),o.optBoolean("folder"))) } } }
            visits("bookmarks",bookmarks);visits("history",history)
        } else {
            home.add(HomeItem(title="ChatGPT",url="https://chatgpt.com/"))
        }
    }
    @Synchronized fun save() {
        fun jsonVisits(list: List<Visit>) = JSONArray().apply { list.forEach { put(it.json()) } }
        val data=JSONObject().put("home",JSONArray().apply { home.forEach { put(it.json()) } }).put("bookmarks",jsonVisits(bookmarks)).put("history",jsonVisits(history.take(1500)))
        val out=dataFile.startWrite()
        try { out.write(data.toString().toByteArray());dataFile.finishWrite(out) } catch(e:Exception) { dataFile.failWrite(out);throw e }
    }
    fun visit(title:String,url:String) {
        if (!url.startsWith("http")) return
        history.removeAll { it.url==url };history.add(0,Visit(title,url));while(history.size>1500)history.removeAt(history.lastIndex);save()
    }
    fun siteKey(url:String) = runCatching { java.net.URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
    fun siteBool(url:String,key:String,default:Boolean) = prefs.getBoolean("site.${siteKey(url)}.$key",default)
    fun setSiteBool(url:String,key:String,value:Boolean) { prefs.edit().putBoolean("site.${siteKey(url)}.$key",value).apply() }
    fun siteOverridden(url:String,key:String)=prefs.contains("site.${siteKey(url)}.$key")
    fun resetSite(url:String){val prefix="site.${siteKey(url)}.";val edit=prefs.edit();prefs.all.keys.filter{it.startsWith(prefix)}.forEach(edit::remove);edit.apply()}
    fun siteZoom(url:String)=prefs.getInt("site.${siteKey(url)}.textZoom",prefs.getInt("textZoom",100)).coerceIn(50,200)
    fun bookmarkHtml():String {
        fun esc(s:String)=s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;")
        fun entries(parent:String):String=bookmarks.filter{it.parent==parent}.joinToString("\n"){if(it.folder)"<DT><H3>${esc(it.title)}</H3><DL><p>\n"+bookmarks.filter{v->v.parent==it.id&&!v.folder}.joinToString("\n"){v->"<DT><A HREF=\"${esc(v.url)}\">${esc(v.title)}</A>"}+"\n</DL><p>"else "<DT><A HREF=\"${esc(it.url)}\">${esc(it.title)}</A>"}
        return "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<DL><p>\n"+entries("")+"\n</DL><p>"
    }
}
