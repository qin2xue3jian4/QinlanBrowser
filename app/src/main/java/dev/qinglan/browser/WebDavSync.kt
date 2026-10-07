package dev.qinglan.browser

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

data class WebDavConfig(val directory:String,val username:String,val password:String) {
    override fun toString()="WebDavConfig(redacted)"
    fun validated():WebDavConfig {
        val uri=runCatching{URI(directory.trim())}.getOrNull()
        require(directory.length<=2048&&uri!=null&&uri.scheme=="https"&&!uri.host.isNullOrBlank()&&
            uri.userInfo==null&&uri.rawQuery==null&&uri.rawFragment==null&&uri.port in -1..65535&&uri.port!=0) {
            tr("请输入不含账号、查询参数或片段的 HTTPS WebDAV 目录地址")
        }
        require(username.isNotBlank()&&username.length<=1024&&':' !in username&&username.none{it.isISOControl()}&&
            password.isNotEmpty()&&password.length<=4096&&password.none{it.isISOControl()}) {
            tr("请输入有效的 WebDAV 账号和应用密码")
        }
        return copy(directory=uri!!.toASCIIString().trimEnd('/')+"/")
    }
    internal fun authorization()="Basic "+Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
}

/** Settings only. The backup allowlist also excludes WebDAV credentials and certificate trust. */
object WebDavSettings {
    const val MAX_BYTES=1_048_576
    data class Snapshot(val settings:Map<String,Any>,val language:String?)
    fun export(prefs:Map<String,*>,language:String):String {
        require(language in LanguageChoice.supported)
        val settings=JSONObject(BackupCodec.export(null,null,prefs)).getJSONObject("settings")
        return JSONObject().put("format","qinglan-settings").put("version",1).put("settings",settings)
            .put("language",language).toString(2).also{parse(it)}
    }
    fun parse(raw:String):Snapshot {
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){tr("同步设置文件超过 1 MB")}
        try {
            val json=JSONObject(raw.trim().removePrefix("\uFEFF"))
            require(json.getString("format")=="qinglan-settings"&&json.get("version")==1&&
                listOf("home","bookmarks","passwords").none(json::has))
            val language=if(json.has("language"))json.getString("language").also{require(it in LanguageChoice.supported)}else null
            val backup=JSONObject().put("format","qinglan-backup").put("version",2).put("settings",json.getJSONObject("settings"))
            return Snapshot(BackupCodec.parse(backup.toString()).settings!!,language)
        }catch(e:Exception){throw IllegalArgumentException(tr("远端文件不是有效的清岚设置，未更改本机设置"))}
    }
}

class WebDavFailure(message:String):IOException(message)

/** HTTPS with system certificate validation; redirects are never followed with credentials. */
class WebDavClient(config:WebDavConfig,filename:String=FILENAME,
    private val connect:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}) {
    private val config=config.validated()
    val address=this.config.directory+filename
    init { require(filename.matches(Regex("[A-Za-z0-9_-]+\\.json"))) }
    data class Remote(val snapshot:WebDavSettings.Snapshot,val etag:String?)
    fun download():Remote?=request("GET") { connection,code->
        if(code==404)return@request null
        if(code!=200)throw status(code)
        if(connection.contentLengthLong>WebDavSettings.MAX_BYTES)throw WebDavFailure(tr("同步设置文件超过 1 MB"))
        val bytes=connection.inputStream.use{input->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break
                if(output.size()+n>WebDavSettings.MAX_BYTES)throw WebDavFailure(tr("同步设置文件超过 1 MB"))
                output.write(buffer,0,n)
            };output.toByteArray()
        }
        Remote(WebDavSettings.parse(bytes.toString(Charsets.UTF_8)),connection.getHeaderField("ETag"))
    }
    fun upload(raw:String,previous:Remote?) {
        WebDavSettings.parse(raw)
        val tag=previous?.etag
        if(previous!=null&&!usableTag(tag))throw WebDavFailure(tr("服务器未提供有效的 ETag，无法安全覆盖远端设置"))
        // Recheck after the user's confirmation. Nutstore ignores If-None-Match: * on PUT.
        // If-Match still guards updates atomically on that service; initial creation has a race
        // on servers which ignore the creation precondition, documented in the user guide.
        val current=download()
        if(current!=previous)throw status(412)
        request("PUT",raw.toByteArray(Charsets.UTF_8),if(previous==null)"If-None-Match" to "*" else "If-Match" to tag!!) { _,code->
            if(code !in listOf(200,201,204))throw status(code)
        }
    }
    private fun <T> request(method:String,body:ByteArray?=null,condition:Pair<String,String>?=null,read:(HttpURLConnection,Int)->T):T {
        var connection:HttpURLConnection?=null
        try {
            val c=connect(URL(address));connection=c
            c.instanceFollowRedirects=false;c.useCaches=false;c.connectTimeout=15_000;c.readTimeout=20_000;c.requestMethod=method
            c.setRequestProperty("Authorization",config.authorization());c.setRequestProperty("Accept","application/json")
            c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Cache-Control","no-cache")
            condition?.let{c.setRequestProperty(it.first,it.second)}
            if(body!=null){c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.doOutput=true
                c.setFixedLengthStreamingMode(body.size);c.outputStream.use{it.write(body)}}
            return read(c,c.responseCode)
        }catch(e:WebDavFailure){throw e}
        catch(e:javax.net.ssl.SSLException){throw WebDavFailure(tr("WebDAV 证书验证失败，请检查服务器证书"))}
        catch(e:IOException){throw WebDavFailure(tr("WebDAV 连接失败或超时，请检查网络和目录地址"))}
        finally {connection?.disconnect()}
    }
    private fun status(code:Int)=WebDavFailure(when(code){
        401,403->tr("WebDAV 认证失败或没有权限，请检查账号和应用密码")
        404,409->tr("WebDAV 目录不存在，请先在服务器创建目录")
        412->tr("远端设置已改变，请重新下载或重新发起上传")
        in 300..399->tr("服务器要求重定向，请填写最终 HTTPS 目录地址")
        413->tr("同步设置文件超过服务器限制")
        429->tr("WebDAV 请求过于频繁，请稍后重试")
        else->tr("WebDAV 操作失败（HTTP %1\$s）",code)
    })
    companion object {
        const val FILENAME="qinglan-settings.json"
        // Nutstore returns an unquoted opaque token and compares If-Match against that exact token.
        // Accept its restricted token syntax as well as RFC strong entity tags, never weak tags/lists.
        internal fun usableTag(tag:String?)=tag!=null&&(Regex("\"[\\x21\\x23-\\x7E\\x80-\\xFF]*\"").matches(tag)||
            Regex("[A-Za-z0-9_-]{1,256}").matches(tag))
    }
}
