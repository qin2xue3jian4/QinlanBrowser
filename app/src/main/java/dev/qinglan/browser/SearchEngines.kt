package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject

object SearchEngines {
    const val default="https://www.bing.com/search?q=%s"
    val presets get()=linkedMapOf(tr("必应") to default,tr("百度") to "https://www.baidu.com/s?wd=%s","Google" to "https://www.google.com/search?q=%s","DuckDuckGo" to "https://duckduckgo.com/?q=%s",tr("搜狗") to "https://www.sogou.com/web?query=%s",tr("360 搜索") to "https://www.so.com/s?q=%s")
    fun valid(s:String)=s.length<=2048&&s.contains("%s")&&runCatching{val uri=java.net.URI(s.replace("%s","test"));uri.scheme in listOf("http","https")&&!uri.host.isNullOrBlank()&&uri.userInfo==null}.getOrDefault(false)
    data class Custom(val id:String=java.util.UUID.randomUUID().toString(),val name:String,val url:String)
    fun parse(raw:String):List<Custom>{val a=JSONArray(raw);require(a.length()<=50){tr("最多保存 50 个自定义搜索引擎")};return List(a.length()){i->val o=a.getJSONObject(i);val id=o.getString("id");val name=o.getString("name");val url=o.getString("url");require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))&&name.isNotBlank()&&name.length<=40&&valid(url)){tr("搜索引擎格式无效")};Custom(id,name,url)}.also{require(it.map{e->e.id}.toSet().size==it.size){tr("搜索引擎 ID 重复")};require(it.map{e->e.name}.toSet().size==it.size){tr("搜索引擎名称重复")}}}
    fun json(items:List<Custom>)=JSONArray().apply{items.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("url",it.url))}}.toString()
    fun name(s:String,custom:List<Custom> = emptyList())=custom.find{it.url==s}?.name?:presets.entries.find{it.value==s}?.key?:tr("自定义")
}
