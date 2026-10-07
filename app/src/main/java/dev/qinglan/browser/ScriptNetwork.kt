package dev.qinglan.browser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

/** No browser cookies, SSL exceptions, or global URL handlers are used by script requests. */
object ScriptNetwork {
    const val MAX_RESPONSE=4*1024*1024
    fun dependencyUrl(raw:String):String {
        if(raw.startsWith("data:",true)){require(raw.length<=MAX_RESPONSE&&',' in raw){tr("无效脚本资源")};integrity(raw);return raw.substringBefore('#')}
        val uri=runCatching{URI(raw)}.getOrNull()
        require(uri!=null&&uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.userInfo==null){tr("脚本依赖必须使用 HTTPS")}
        integrity(raw)
        return raw.substringBefore('#')
    }
    private val hashAlgorithms=mapOf("md5" to "MD5","sha1" to "SHA-1","sha256" to "SHA-256","sha384" to "SHA-384","sha512" to "SHA-512")
    private fun integrity(raw:String):Pair<String,String>? {
        val fragment=requireNotNull(runCatching{URI(raw)}.getOrNull()){"Invalid dependency URL"}.fragment?:return null
        val items=fragment.split(',', ';').map{part->val m=Regex("^(md5|sha1|sha256|sha384|sha512)[=-]([A-Za-z0-9+/=_-]+)$",RegexOption.IGNORE_CASE).matchEntire(part.trim());require(m!=null){tr("暂不支持此依赖校验格式")};m.groupValues[1].lowercase() to m.groupValues[2]}
        return items.lastOrNull()
    }
    fun verifyIntegrity(raw:String,bytes:ByteArray) {
        val (algorithm,expected)=integrity(raw)?:return
        val hash=MessageDigest.getInstance(hashAlgorithms.getValue(algorithm)).digest(bytes)
        val hex=hash.joinToString(""){"%02x".format(it)};val base64=Base64.getEncoder().encodeToString(hash);val url64=Base64.getUrlEncoder().encodeToString(hash)
        require(expected.equals(hex,true)||expected.trimEnd('=')==base64.trimEnd('=')||expected.trimEnd('=')==url64.trimEnd('=')){"Dependency integrity mismatch ($algorithm)"}
    }
    fun isInstallUrl(raw:String)=runCatching{val uri=URI(raw);uri.scheme in listOf("http","https")&&uri.userInfo==null&&uri.path.endsWith(".user.js",true)}.getOrDefault(false)
    fun allowed(script:UserScript,target:String,page:String):Boolean {
        val uri=runCatching{URI(target)}.getOrNull()?:return false
        if(uri.scheme !in listOf("https","http")||uri.host.isNullOrBlank()||uri.userInfo!=null)return false
        val host=uri.host.lowercase();val self=runCatching{URI(page).host.lowercase()}.getOrDefault("")
        return host==self || script.meta["connect"].orEmpty().any{rule-> val r=rule.lowercase();r=="*" || r=="self"&&host==self || host==r || host.endsWith("."+r.removePrefix("*."))}
    }
    data class Response(val url:String,val status:Int,val statusText:String,val headers:String,val mime:String,val bytes:ByteArray) {
        fun text():String {val charset=Regex("charset=([^;\\s]+)",RegexOption.IGNORE_CASE).find(mime)?.groupValues?.get(1)?.trim('"','\'');return bytes.toString(runCatching{charset?.let{java.nio.charset.Charset.forName(it)}}.getOrNull()?:Charsets.UTF_8)}
        fun json()=JSONObject().put("finalUrl",url).put("status",status).put("statusText",statusText).put("responseHeaders",headers).put("responseText",text()).put("base64",Base64.getEncoder().encodeToString(bytes))
    }
    fun fetch(raw:String,method:String="GET",headers:Map<String,String> = emptyMap(),body:String?=null,timeout:Int=30000,allowed:(String)->Boolean={true}):Response {
        var url=raw.substringBefore('#');var verb=method.uppercase();var payload=body
        require(verb in setOf("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS")){"Unsupported HTTP method"}
        repeat(6){
            val uri=URI(url);require(uri.scheme in listOf("http","https")&&uri.host!=null&&uri.userInfo==null&&allowed(url)){"Request outside @connect"}
            val conn=URL(url).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects=false;conn.connectTimeout=timeout.coerceIn(1000,60000);conn.readTimeout=timeout.coerceIn(1000,60000);conn.requestMethod=verb
                conn.setRequestProperty("Accept-Encoding","identity")
                headers.forEach{(key,value)->require(Regex("[A-Za-z0-9-]+").matches(key)&&!value.contains('\r')&&!value.contains('\n')){"Invalid header"};require(key.lowercase() !in setOf("cookie","host","origin","referer","content-length","connection","proxy-authorization","accept-encoding")){"Restricted header"};conn.setRequestProperty(key,value)}
                if(payload!=null&&verb !in listOf("GET","HEAD")){val bytes=payload!!.toByteArray();require(bytes.size<=MAX_RESPONSE){"Request too large"};conn.doOutput=true;conn.setFixedLengthStreamingMode(bytes.size);conn.outputStream.use{it.write(bytes)}}
                val status=conn.responseCode
                if(status in setOf(301,302,303,307,308)) {
                    val next=URI(url).resolve(conn.getHeaderField("Location")?:error("Missing redirect location")).toString()
                    require(!(url.startsWith("https:")&&next.startsWith("http:"))){"HTTPS redirect downgrade refused"}
                    // Never forward caller credentials to another origin.
                    require(headers.keys.none{it.equals("Authorization",true)}||NavigationPolicy.webOrigin(url)==NavigationPolicy.webOrigin(next)){"Credential redirect refused"}
                    url=next;if(status==303||status in listOf(301,302)&&verb=="POST"){verb="GET";payload=null}
                } else {
                    require(conn.contentLengthLong<=MAX_RESPONSE){"Response too large"}
                    val input=if(status>=400)conn.errorStream else conn.inputStream
                    val bytes=input?.use{stream->val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=stream.read(buf);if(n<0)break;require(out.size()+n<=MAX_RESPONSE){"Response too large"};out.write(buf,0,n)};out.toByteArray()}?:ByteArray(0)
                    val lines=conn.headerFields.filterKeys{it!=null}.entries.joinToString("\r\n"){(key,values)->"$key: ${values.joinToString(", ")}"}
                    return Response(url,status,conn.responseMessage.orEmpty(),lines,conn.contentType?:"application/octet-stream",bytes)
                }
            } finally {conn.disconnect()}
        }
        error("Too many redirects")
    }
    fun dependency(raw:String,userAgent:String=""):Response {
        val url=dependencyUrl(raw)
        val response=if(url.startsWith("data:",true))inline(url)else fetch(url,headers=if(userAgent.isBlank())emptyMap()else mapOf("User-Agent" to userAgent),allowed={it.startsWith("https://")})
        require(response.status in 200..299){"Dependency HTTP ${response.status}: $url"}
        verifyIntegrity(raw,response.bytes)
        return response
    }
    private fun inline(raw:String):Response {
        val header=raw.substringAfter(':').substringBefore(',');val content=raw.substringAfter(',');val decoded=java.io.ByteArrayOutputStream();var i=0
        while(i<content.length){if(content[i]=='%'){require(i+2<content.length){"Invalid data URL"};decoded.write(content.substring(i+1,i+3).toInt(16));i+=3}else{val cp=content.codePointAt(i);decoded.write(String(Character.toChars(cp)).toByteArray());i+=Character.charCount(cp)}}
        val bytes=if(header.endsWith(";base64",true))Base64.getDecoder().decode(decoded.toByteArray())else decoded.toByteArray();require(bytes.size<=MAX_RESPONSE){"Inline resource too large"}
        val mime=header.removeSuffix(";base64").ifBlank{"text/plain;charset=US-ASCII"}
        return Response(raw,200,"OK","Content-Type: $mime",mime,bytes)
    }
    fun prepare(script:UserScript,userAgent:String=""):UserScript {
        val dependencies=linkedMapOf<String,String>();val resources=linkedMapOf<String,String>();val urls=linkedMapOf<String,String>();var total=script.source.toByteArray().size
        script.meta["require"].orEmpty().forEach{url->val data=dependency(url,userAgent);total+=data.bytes.size;require(total<=8*1024*1024){"Dependencies too large"};dependencies[url]=data.text()}
        script.meta["resource"].orEmpty().forEach{spec->val parts=spec.split(Regex("\\s+"),limit=2);val data=dependency(parts[1],userAgent);total+=data.bytes.size;require(total<=8*1024*1024){"Dependencies too large"};resources[parts[0]]=data.text();urls[parts[0]]="data:${data.mime.substringBefore(';')};base64,"+Base64.getEncoder().encodeToString(data.bytes)}
        return script.copy(dependencies=dependencies,resources=resources,resourceUrls=urls)
    }
}
