package dev.qinglan.browser

import java.net.URI

object WebPermissionPolicy {
    fun origin(url:String):String?=runCatching{val u=URI(url);require(u.scheme.equals("https",true)&&u.userInfo==null&&!u.host.isNullOrBlank());"https://${u.host.lowercase()}:${if(u.port==-1)443 else u.port}"}.getOrNull()
    fun sameOrigin(page:String,request:String)=origin(page)?.let{it==origin(request)}==true
}
