package dev.qinglan.browser

import java.net.URI
import java.util.Locale

enum class ResourceKind(val label: String) {
    VIDEO("视频"), AUDIO("音频"), IMAGE("图片"), HLS("HLS 播放列表"), DASH("DASH 播放列表"), SEGMENT("媒体分片"), BLOB("Blob 媒体")
}
data class WebResource(val url: String, val kind: ResourceKind, val mime: String = "", val referer: String = "", val userAgent: String = "", val source: String = "网页请求") {
    val name: String get() = runCatching { URI(url).path?.substringAfterLast('/')?.take(120) }.getOrNull().orEmpty().ifBlank { kind.label }
    val playlist get() = kind == ResourceKind.HLS || kind == ResourceKind.DASH
}

/** A bounded in-memory list per tab; never persists URLs, headers or credentials. */
class ResourceSession {
    private val entries = linkedMapOf<String, WebResource>()
    @Volatile var page = ""; private set
    @Volatile var epoch = 0L; private set
    @Volatile var userAgent = ""; private set
    @Synchronized fun start(url: String, ua: String) { epoch++;page=url;userAgent=ua;entries.clear() }
    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun list() = entries.values.toList()
    @Synchronized fun add(generation: Long, resource: WebResource) {
        if (generation != epoch || resource.url.length > 8192) return
        val url=ResourceClassifier.normalize(resource.url) ?: return
        val old=entries[url]
        if(old!=null) {
            entries[url]=old.copy(kind=if(resource.mime.isNotBlank())resource.kind else old.kind,
                mime=resource.mime.ifBlank{old.mime},referer=resource.referer.ifBlank{old.referer},userAgent=resource.userAgent.ifBlank{old.userAgent},source=if(resource.source=="页面元素")resource.source else old.source)
            return
        }
        if(entries.size>=300) {
            if(resource.kind in setOf(ResourceKind.IMAGE,ResourceKind.SEGMENT))return
            val replace=entries.entries.firstOrNull { it.value.kind in setOf(ResourceKind.IMAGE,ResourceKind.SEGMENT) }?.key ?: return
            entries.remove(replace)
        }
        entries[url]=resource.copy(url=url)
    }
}

object ResourceClassifier {
    fun normalize(raw: String): String? {
        if(raw.length>8192 || raw.any { it=='\r'||it=='\n'||it=='\u0000' })return null
        return runCatching {
            val u=URI(raw)
            if(u.scheme in listOf("http","https")&&!u.host.isNullOrBlank()&&u.rawUserInfo==null)raw.substringBefore('#')
            else if(u.scheme=="blob"&&FilterEngine.host(raw.removePrefix("blob:")).isNotEmpty())raw else null
        }.getOrNull()
    }
    fun classify(url: String, mime: String="", hint: String=""): ResourceKind? {
        val valid=normalize(url) ?: return null
        if(valid.startsWith("blob:"))return if(hint in listOf("video","audio","source"))ResourceKind.BLOB else null
        val type=mime.substringBefore(';').trim().lowercase(Locale.ROOT)
        val ext=runCatching { URI(valid).path.substringAfterLast('/').substringAfterLast('.',"").lowercase(Locale.ROOT) }.getOrDefault("")
        if(ext=="m3u8" || type in setOf("application/vnd.apple.mpegurl","application/x-mpegurl","audio/mpegurl","audio/x-mpegurl"))return ResourceKind.HLS
        if(ext=="mpd" || type=="application/dash+xml")return ResourceKind.DASH
        if(ext in setOf("ts","m4s") || type=="video/mp2t")return ResourceKind.SEGMENT
        if(type.startsWith("video/"))return ResourceKind.VIDEO
        if(type.startsWith("audio/"))return ResourceKind.AUDIO
        if(type.startsWith("image/"))return ResourceKind.IMAGE
        // An explicit HTML/JSON response takes precedence over a misleading file extension.
        if(type.isNotEmpty()&&type !in setOf("application/octet-stream","binary/octet-stream"))return null
        return when {
            ext in setOf("mp4","webm","m4v","mov","mkv","flv","3gp","ogv") -> ResourceKind.VIDEO
            ext in setOf("mp3","m4a","aac","wav","flac","ogg","opus") -> ResourceKind.AUDIO
            ext in setOf("jpg","jpeg","png","gif","webp","avif","svg","bmp","ico") -> ResourceKind.IMAGE
            hint in listOf("video","audio","image","img") -> when(hint){"video"->ResourceKind.VIDEO;"audio"->ResourceKind.AUDIO;else->ResourceKind.IMAGE}
            else -> null
        }
    }
    fun mime(kind: ResourceKind): String = when(kind) {
        ResourceKind.VIDEO->"video/*";ResourceKind.AUDIO->"audio/*";ResourceKind.IMAGE->"image/*"
        ResourceKind.HLS->"application/vnd.apple.mpegurl";ResourceKind.DASH->"application/dash+xml"
        ResourceKind.SEGMENT->"video/mp2t";ResourceKind.BLOB->"application/octet-stream"
    }
    fun sameDocument(left: String,right: String) = normalize(left)==normalize(right)
    fun sameOrigin(left: String,right: String): Boolean = runCatching {
        val a=URI(left);val b=URI(right)
        fun port(u: URI)=if(u.port>=0)u.port else if(u.scheme=="https")443 else 80
        a.scheme==b.scheme && a.host!=null && a.host.equals(b.host,true) && port(a)==port(b)
    }.getOrDefault(false)
}
