package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Cookie-Editor interchange. Never fabricate missing scope/expiry metadata. */
object CookieCodec {
    data class Entry(val name:String,val value:String,val domain:String,val path:String="/",val secure:Boolean=false,val httpOnly:Boolean=false,val hostOnly:Boolean=true,val sameSite:String="unspecified",val expiry:Double?=null) {
        val identity get()="$name\u0000$domain\u0000$path"
        fun url()="${if(secure)"https" else "http"}://${domain.trimStart('.')}${path}"
        fun header(delete:Boolean=false):String = buildString {
            append("$name=$value; Path=$path")
            if(!hostOnly)append("; Domain=$domain")
            if(secure)append("; Secure")
            if(httpOnly)append("; HttpOnly")
            when(sameSite){"strict"->append("; SameSite=Strict");"lax"->append("; SameSite=Lax");"no_restriction"->append("; SameSite=None")}
            if(delete)append("; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
            else expiry?.let { append("; Expires=${dateFormat().format(Date((it*1000).toLong()))}") }
        }
        fun json()=JSONObject().put("name",name).put("value",value).put("domain",domain).put("path",path).put("secure",secure).put("httpOnly",httpOnly).put("hostOnly",hostOnly).put("sameSite",sameSite).put("session",expiry==null).apply { expiry?.let { put("expirationDate",it) } }
    }
    data class ImportResult(val entries:List<Entry>,val warnings:List<String>)
    fun dateFormat()=SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US).apply{timeZone=TimeZone.getTimeZone("GMT");isLenient=false}
    fun host(url:String)=runCatching{URI(url).host?.lowercase()?.trimEnd('.')}.getOrNull()
    private fun clean(s:String)=s.none{it<' '||it=='\u007f'||it==';'}
    fun parseImport(text:String,currentUrl:String,now:Double=System.currentTimeMillis()/1000.0):ImportResult {
        require(text.toByteArray().size<=1_048_576){"文件超过 1 MB"}
        val current=host(currentUrl)?:throw IllegalArgumentException("请先打开目标网站")
        val array=JSONArray(text.trimStart('\uFEFF'))
        require(array.length()<=1000){"单次最多导入 1000 项"}
        val entries=linkedMapOf<String,Entry>();val warnings=mutableListOf<String>()
        for(i in 0 until array.length()) {
            try {
                val o=array.getJSONObject(i)
                require(!o.has("partitionKey")||o.isNull("partitionKey")){"暂不支持分区 Cookie"}
                require(!o.optBoolean("partitioned")){"暂不支持分区 Cookie"}
                val name=o.getString("name");val value=o.getString("value")
                require(name.isNotBlank()&&name.all { it.code in 33..126 && it !in "()<>@,;:\\\"/[]?={} " }){"名称不合法"}
                require(clean(value)&&value.none{it=='\r'||it=='\n'}){"值包含不允许的分隔符"}
                val rawDomain=o.optString("domain",current).lowercase();val domain=rawDomain.trimStart('.')
                require(domain.isNotEmpty()&&clean(domain)&&domain.all{it.isLetterOrDigit()||it=='.'||it=='-'}){"域名不合法"}
                require(domain==current||current.endsWith(".$domain")||domain.endsWith(".$current")){"域名不属于当前网站"}
                val path=o.optString("path","/");require(path.startsWith('/')&&clean(path)){"路径不合法"}
                val secure=o.optBoolean("secure");val httpOnly=o.optBoolean("httpOnly")
                val hostOnly=if(o.has("hostOnly"))o.getBoolean("hostOnly") else !rawDomain.startsWith('.')
                val sameSite=when(o.optString("sameSite","unspecified").lowercase()){ "none","no_restriction"->"no_restriction";"lax"->"lax";"strict"->"strict";"unspecified",""->"unspecified";else->throw IllegalArgumentException("SameSite 不受支持") }
                require(sameSite!="no_restriction"||secure){"SameSite=None 需要 Secure"}
                val expiry=if(o.optBoolean("session",false)||!o.has("expirationDate")||o.isNull("expirationDate"))null else o.getDouble("expirationDate")
                require(expiry==null||(expiry.isFinite()&&expiry>now&&expiry<253402300800.0)){"Cookie 已过期或到期时间无效"}
                require(!name.startsWith("__Secure-")||secure){"__Secure- 需要 Secure"}
                require(!name.startsWith("__Host-")||(secure&&hostOnly&&path=="/")){"__Host- 需要 Secure、根路径及 hostOnly"}
                val e=Entry(name,value,if(hostOnly)domain else ".$domain",path,secure,httpOnly,hostOnly,sameSite,expiry)
                require(e.header().toByteArray().size<=4096){"Cookie 大于 4096 字节"}
                if(entries.containsKey(e.identity))warnings.add("第 ${i+1} 项：重复条目，保留最后一项")
                entries[e.identity]=e
            } catch(e:Exception) { warnings.add("第 ${i+1} 项：${e.message?:"格式不合法"}") }
        }
        return ImportResult(entries.values.toList(),warnings)
    }
    // Input is CookieManagerCompat's canonical cookie-info output, not arbitrary
    // server Set-Cookie headers: Chromium includes Domain even for host cookies,
    // distinguishing domain cookies with a leading dot.
    fun parseSetCookie(raw:String,url:String):Entry {
        val parts=raw.split(';');val first=parts.first();val split=first.indexOf('=');require(split>0){"无效的 Cookie"}
        val attributes=linkedMapOf<String,String>()
        parts.drop(1).forEach { val p=it.trim();attributes[p.substringBefore('=').lowercase()]=p.substringAfter('=',"") }
        require(!attributes.containsKey("partitioned")){"分区 Cookie 暂不导出"}
        val domain=attributes["domain"]?.takeIf{it.isNotBlank()}?:host(url)?:throw IllegalArgumentException("无域名")
        val path=attributes["path"]?:run { val p=URI(url).path.orEmpty();if(p.count{it=='/'}<=1)"/" else p.substringBeforeLast('/') }
        val expiry=attributes["expires"]?.let { rawDate ->
            val formats=listOf("EEE, dd MMM yyyy HH:mm:ss zzz","EEE, dd-MMM-yyyy HH:mm:ss zzz","EEE, dd MMM yy HH:mm:ss zzz")
            formats.firstNotNullOfOrNull { f->runCatching{SimpleDateFormat(f,Locale.US).apply{timeZone=TimeZone.getTimeZone("GMT");isLenient=false}.parse(rawDate)?.time?.div(1000.0)}.getOrNull() }?:throw IllegalArgumentException("无法保留到期时间")
        }
        val maxAge=attributes["max-age"]?.toLongOrNull()?.let{System.currentTimeMillis()/1000.0+it}
        val sameSite=when(attributes["samesite"]?.lowercase()){ "none"->"no_restriction";"lax"->"lax";"strict"->"strict";else->"unspecified" }
        return Entry(first.substring(0,split),first.substring(split+1),domain,path,attributes.containsKey("secure"),attributes.containsKey("httponly"),!domain.startsWith('.'),sameSite,maxAge?:expiry)
    }
    fun export(entries:List<Entry>)=JSONArray().apply{entries.forEach{put(it.json())}}.toString(2)
}
