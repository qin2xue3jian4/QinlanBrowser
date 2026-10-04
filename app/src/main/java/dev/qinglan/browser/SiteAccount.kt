package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class SiteAccount(val id:String,val name:String,val site:String,val startUrl:String)

/** Metadata only. Cookies and website storage remain inside the WebView profile. */
object AccountCodec {
    const val PREFIX="qinglan_account_"
    const val LIMIT=20
    fun home(url:String):String {
        val uri=URI(url)
        require(uri.scheme in listOf("https","http")&&!uri.host.isNullOrBlank()&&uri.rawUserInfo==null){"请先打开 HTTP / HTTPS 网站"}
        return URI(uri.scheme.lowercase(),null,uri.host.lowercase(),uri.port,"/",null,null).toASCIIString()
    }
    fun name(value:String)=value.trim().also{require(it.isNotEmpty()&&it.length<=24&&it.none(Char::isISOControl)){"名称需为 1–24 个字符"}}
    fun create(label:String,url:String):SiteAccount {val start=home(url);return SiteAccount(PREFIX+UUID.randomUUID(),name(label),URI(start).host,start)}
    fun encode(items:List<SiteAccount>)=JSONArray().apply{items.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("site",it.site).put("url",it.startUrl))}}.toString()
    fun decode(raw:String):List<SiteAccount> {
        val array=JSONArray(raw);require(array.length()<=LIMIT)
        val items=(0 until array.length()).map{val o=array.getJSONObject(it);val id=o.getString("id");require(id.startsWith(PREFIX)&&UUID.fromString(id.removePrefix(PREFIX)).toString()==id.removePrefix(PREFIX));val url=home(o.getString("url"));val site=URI(url).host;require(site==o.getString("site"));SiteAccount(id,name(o.getString("name")),site,url)}
        require(items.map{it.id}.distinct().size==items.size)
        require(items.map{it.site to it.name.lowercase()}.distinct().size==items.size)
        return items
    }
}
