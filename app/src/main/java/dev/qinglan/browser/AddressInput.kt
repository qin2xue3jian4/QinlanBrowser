package dev.qinglan.browser

import java.net.IDN
import java.net.URI
import java.net.URLEncoder

/** Explicit schemes never fall through to a search provider (may contain credentials). */
object AddressInput {
    sealed class Result { data class Navigate(val url:String):Result();data class Search(val text:String):Result();data class Invalid(val message:String):Result() }
    fun resolve(input:String):Result {
        val s=input.trim()
        if(s.isEmpty())return Result.Invalid(tr("请输入网址或关键词"))
        if(s.length>8192)return Result.Invalid(tr("输入过长"))
        val explicit=Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(s)
        val hostPort=Regex("^(localhost|[\\w.-]+\\.[\\w.-]+):[0-9]+([/?#].*)?$").matches(s)
        if(explicit&&!hostPort&&!s.startsWith("http://",true)&&!s.startsWith("https://",true))return Result.Invalid(tr("地址栏只支持 HTTP / HTTPS 网址"))
        if(s.any{it.isWhitespace()})return if(explicit&&!hostPort)Result.Invalid(tr("网址不能包含空白字符"))else Result.Search(s)
        val authority=s.substringBefore('/').substringBefore('?').substringBefore('#')
        val looks=explicit||authority.contains('.')||authority.startsWith('[')||authority=="localhost"||hostPort
        if(!looks)return Result.Search(s)
        return runCatching{
            val raw=if(explicit&&!hostPort)s else (if(authority.substringBefore(':')=="localhost"||Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$").matches(authority)||authority.startsWith('['))"http://"else"https://")+s
            val uri=URI(raw);require(uri.scheme.lowercase() in listOf("http","https")&&uri.rawUserInfo==null&&uri.rawAuthority!=null)
            require(!uri.rawAuthority.contains('@')&&!uri.rawAuthority.contains('\\'))
            val host=uri.host?:IDN.toASCII(uri.rawAuthority.substringBefore(':')).lowercase()
            require(host.isNotBlank()&&(host.startsWith('[')||host.split('.').all{it.isNotEmpty()&&it.length<=63&&!it.startsWith('-')&&!it.endsWith('-')&&it.all{c->c.isLetterOrDigit()||c=='-'}}))
            val port=if(uri.host!=null)uri.port else uri.rawAuthority.substringAfter(':',"").let{if(it.isBlank())-1 else it.toInt()}
            require(port==-1||port in 1..65535)
            val normalized=uri.scheme.lowercase()+"://"+host+(if(port!=-1)":$port"else"")+(uri.rawPath?:"")+(uri.rawQuery?.let{"?$it"}?:"")+(uri.rawFragment?.let{"#$it"}?:"")
            Result.Navigate(URI(normalized).toASCIIString())
        }.getOrElse{if(!explicit&&!s.contains('/')&&!s.contains('@')&&!s.contains(':'))Result.Search(s)else Result.Invalid(tr("网址格式无效，请检查地址"))}
    }
    fun searchUrl(template:String,text:String)=template.replace("%s",URLEncoder.encode(text,"UTF-8"))
    data class Suggestion(val title:String,val url:String,val source:String)
    fun suggestions(query:String,bookmarks:List<Visit>,history:List<Visit>):List<Suggestion>{
        val q=query.trim();if(q.length<2)return emptyList()
        return (bookmarks.filterNot{it.folder}.map{Suggestion(it.title,it.url,tr("书签"))}+history.map{Suggestion(it.title,it.url,tr("历史"))})
            .filter{it.title.contains(q,true)||it.url.contains(q,true)}.distinctBy{it.url}.take(6)
    }
}
