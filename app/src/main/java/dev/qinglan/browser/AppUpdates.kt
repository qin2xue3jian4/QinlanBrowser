package dev.qinglan.browser

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ProjectLinks {
    const val repository="https://github.com/qin2xue3jian4/QinlanBrowser"
    const val releases="$repository/releases"
    const val issues="$repository/issues"
    const val latestApi="https://api.github.com/repos/qin2xue3jian4/QinlanBrowser/releases/latest"
}

/** Semantic versions, rather than lexical comparison (0.10.0 follows 0.9.0). */
@ConsistentCopyVisibility
data class AppVersion private constructor(val name:String,private val numbers:List<Long>,private val pre:List<String>):Comparable<AppVersion> {
    val stable get()=pre.isEmpty()
    override fun compareTo(other:AppVersion):Int {
        numbers.zip(other.numbers).forEach{(a,b)->if(a!=b)return a.compareTo(b)}
        if(stable||other.stable)return when{stable&&other.stable->0;stable->1;else->-1}
        pre.zip(other.pre).forEach{(a,b)->
            if(a!=b){val an=a.all(Char::isDigit);val bn=b.all(Char::isDigit)
                return when{an&&bn->if(a.length!=b.length)a.length.compareTo(b.length)else a.compareTo(b);an->-1;bn->1;else->a.compareTo(b)}}
        }
        return pre.size.compareTo(other.pre.size)
    }
    companion object {
        fun parse(raw:String):AppVersion? {
            if(raw.length>100)return null
            val name=raw.removePrefix("v")
            val m=Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$").matchEntire(name)?:return null
            val numbers=(1..3).map{m.groupValues[it].toLongOrNull()?:return null}
            val pre=m.groupValues[4].takeIf{it.isNotEmpty()}?.split('.')?:emptyList()
            if(pre.any{it.length>1&&it.all(Char::isDigit)&&it.startsWith('0')})return null
            return AppVersion(name,numbers,pre)
        }
    }
}

data class AppRelease(val version:AppVersion,val title:String,val notes:String,val tag:String="v${version.name}") {
    init { require(tag==version.name||tag=="v${version.name}") }
    val page get()="${ProjectLinks.releases}/tag/$tag"
    fun newerThan(installed:String)=AppVersion.parse(installed)?.let{version>it}?:false
    fun cache()=JSONObject().put("tag_name",tag).put("html_url",page).put("name",title).put("body",notes)
        .put("draft",false).put("prerelease",false).put("assets",org.json.JSONArray().put(JSONObject()
            .put("name","qinglan-${version.name}.apk").put("state","uploaded")
            .put("browser_download_url","${ProjectLinks.releases}/download/$tag/qinglan-${version.name}.apk"))).toString()
    companion object {
        fun parse(raw:String):AppRelease? {
            val o=JSONObject(raw)
            if(o.getBoolean("draft")||o.getBoolean("prerelease"))return null
            val tag=o.getString("tag_name")
            val version=AppVersion.parse(tag)?.takeIf{it.stable&&tag in listOf(it.name,"v${it.name}")}?:return null
            val expectedPage="${ProjectLinks.releases}/tag/$tag"
            require(o.getString("html_url")==expectedPage){"Invalid release source"}
            val assets=o.getJSONArray("assets")
            val name="qinglan-${version.name}.apk"
            val apk=(0 until assets.length()).map{assets.getJSONObject(it)}.any{
                it.optString("name")==name&&it.optString("state")=="uploaded"&&
                    it.optString("browser_download_url")=="${ProjectLinks.releases}/download/$tag/$name"
            }
            if(!apk)return null
            val title=if(o.isNull("name"))version.name else o.optString("name").take(200).ifBlank{version.name}
            val notes=if(o.isNull("body"))""else o.optString("body").take(16_384)
            return AppRelease(version,title,notes,tag)
        }
    }
}

object UpdatePolicy {
    const val INTERVAL=24*60*60*1000L
    fun due(now:Long,lastAttempt:Long)=lastAttempt<=0||now<lastAttempt||now-lastAttempt>=INTERVAL
}

class UpdateFailure(message:String):IOException(message)

/** Public GitHub API only: no token, cookies, user settings, or browser TLS exceptions. */
object GitHubUpdates {
    const val MAX_BYTES=524_288
    fun latest(connect:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}):AppRelease? {
        var connection:HttpURLConnection?=null
        try {
            val c=connect(URL(ProjectLinks.latestApi));connection=c
            c.requestMethod="GET";c.instanceFollowRedirects=false;c.useCaches=false;c.connectTimeout=15_000;c.readTimeout=20_000
            c.setRequestProperty("Accept","application/vnd.github+json")
            c.setRequestProperty("User-Agent","Qinglan-Update-Checker")
            c.setRequestProperty("X-GitHub-Api-Version","2026-03-10")
            c.setRequestProperty("Accept-Encoding","identity")
            when(val code=c.responseCode){
                404->return null
                403,429->throw UpdateFailure(tr("GitHub 暂时限制了请求，请稍后重试"))
                200->Unit
                else->throw UpdateFailure(tr("检查更新失败（HTTP %1\$s）",code))
            }
            if(c.contentLengthLong>MAX_BYTES)throw UpdateFailure(tr("更新信息过大，请前往 GitHub 发布页查看"))
            val raw=c.inputStream.use{input->val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break
                    if(out.size()+n>MAX_BYTES)throw UpdateFailure(tr("更新信息过大，请前往 GitHub 发布页查看"))
                    out.write(buffer,0,n)};out.toString("UTF-8")}
            return try{AppRelease.parse(raw)}catch(e:Exception){throw UpdateFailure(tr("无法读取更新信息，请前往 GitHub 发布页查看"))}
        }catch(e:UpdateFailure){throw e}
        catch(e:IOException){throw UpdateFailure(tr("无法连接 GitHub，请检查网络后重试"))}
        finally {connection?.disconnect()}
    }
}
