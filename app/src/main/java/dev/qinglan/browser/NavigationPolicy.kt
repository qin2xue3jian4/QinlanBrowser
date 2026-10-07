package dev.qinglan.browser

import java.net.URI

/** Shared by typed addresses, redirects, restored tabs and downloads. */
object NavigationPolicy {
    fun secure(url:String,httpsOnly:Boolean):String = if(httpsOnly&&url.startsWith("http://",true)) "https://"+url.substring(7) else url
    fun blocked(url:String,httpsOnly:Boolean)=httpsOnly&&url.startsWith("http://",true)
    fun origin(url:String):String?=webOrigin(url)?.takeIf{it.startsWith("https://")}
    fun webOrigin(url:String):String?=runCatching {
        val u=URI(url);require(u.scheme in listOf("http","https")&&u.host!=null&&u.rawUserInfo==null)
        "${u.scheme}://${u.host.lowercase()}:${if(u.port<0)if(u.scheme=="https")443 else 80 else u.port}"
    }.getOrNull()
}
