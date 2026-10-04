package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject

/** Explicit allowlist: never serializes sessions, history, downloads, or site storage. */
object BackupCodec {
    data class Snapshot(val home:List<HomeItem>?,val bookmarks:List<Visit>?,val settings:Map<String,Any>?)
    private val boolKeys=setOf("bottomAddress","restore","thirdParty","noImages","autoHideAddress")
    fun allowed(key:String)=key in boolKeys||key in setOf("theme","search","textZoom")||Regex("site\\.[a-zA-Z0-9.:-]{1,253}\\.(desktop|js|thirdParty|dark|noImages)").matches(key)
    private fun validUrl(s:String)=s.length<=8192&&runCatching{val u=java.net.URI(s);u.scheme in listOf("http","https")&&!u.host.isNullOrBlank()&&u.userInfo==null}.getOrDefault(false)
    private fun settings(raw:JSONObject):Map<String,Any> {
        require(raw.length()<=3000){"设置项过多"}
        val out=linkedMapOf<String,Any>()
        raw.keys().forEach{key->if(allowed(key)){
            val v=raw.get(key)
            val valid=when(key){"theme"->v in listOf("light","dark","system");"search"->v is String&&SearchEngines.valid(v);"textZoom"->v is Number&&v.toDouble()==v.toInt().toDouble()&&v.toInt() in 50..300;else->v is Boolean}
            require(valid){"设置项格式无效：$key"};out[key]=if(key=="textZoom")(v as Number).toInt()else v
        }};return out
    }
    fun export(home:List<HomeItem>?,bookmarks:List<Visit>?,prefs:Map<String,*>?):String {
        val o=JSONObject().put("format","qinglan-backup").put("version",1)
        home?.let{o.put("home",JSONArray().apply{it.forEach{item->put(item.json())}})}
        bookmarks?.let{o.put("bookmarks",JSONArray().apply{it.forEach{item->put(JSONObject().put("title",item.title).put("url",item.url))}})}
        prefs?.let{val s=JSONObject();it.forEach{(k,v)->if(allowed(k))s.put(k,v)};o.put("settings",s)}
        val result=o.toString(2);require(result.toByteArray().size<=1_048_576){"备份超过 1 MB，请分项导出"};return result
    }
    fun parse(text:String):Snapshot {
        require(text.toByteArray().size<=1_048_576){"备份超过 1 MB"}
        val o=JSONObject(text.trim().removePrefix("\uFEFF"));require(o.optString("format")=="qinglan-backup"&&o.optInt("version")==1){"不是支持的清岚备份文件"}
        val home=if(o.has("home")){val arr=o.getJSONArray("home");require(arr.length()<=2000){"主页项目过多"}
            List(arr.length()){i->val item=arr.getJSONObject(i);val id=item.getString("id");val title=item.getString("title");val folder=item.getBoolean("folder");val url=item.optString("url");val parent=item.optString("parent")
                require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))&&title.isNotBlank()&&title.length<=1000){"主页项目格式错误"}
                require(if(folder)url.isEmpty()&&parent.isEmpty()else validUrl(url)){"主页网址或文件夹格式错误"};HomeItem(id,title,url,parent,folder)
            }.also{items->require(items.map{it.id}.toSet().size==items.size){"主页 ID 重复"};val folders=items.filter{it.folder}.map{it.id}.toSet();require(items.all{it.parent.isEmpty()||it.parent in folders}){"主页包含无效文件夹引用"}}
        }else null
        val bookmarks=if(o.has("bookmarks")){val arr=o.getJSONArray("bookmarks");require(arr.length()<=10000){"书签数量过多"};List(arr.length()){i->val item=arr.getJSONObject(i);val title=item.getString("title");val url=item.getString("url");require(title.length<=1000&&validUrl(url)){"书签格式或网址错误"};Visit(title,url)}.distinctBy{it.url}}else null
        val settings=if(o.has("settings"))settings(o.getJSONObject("settings"))else null
        require(home!=null||bookmarks!=null||settings!=null){"文件中没有可恢复的项目"};return Snapshot(home,bookmarks,settings)
    }
}
