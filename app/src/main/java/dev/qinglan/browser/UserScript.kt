package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject

data class UserScript(val id:String=java.util.UUID.randomUUID().toString(),val source:String,val enabled:Boolean=false,
    val dependencies:Map<String,String> = emptyMap(),val resources:Map<String,String> = emptyMap(),val resourceUrls:Map<String,String> = emptyMap(),val installUrl:String="") {
    val meta=ScriptCodec.metadata(source)
    val name=meta["name"]!!.first()
    val version=meta["version"]?.firstOrNull().orEmpty()
    val runAt=meta["run-at"]?.firstOrNull()?:"document-idle"
    val includes=(meta["match"].orEmpty().map{ScriptCodec.matchRegex(it)}+meta["include"].orEmpty().map{ScriptCodec.pattern(it)}).ifEmpty{listOf("^https?://.*$")}
    val excludes=meta["exclude-match"].orEmpty().map{ScriptCodec.matchRegex(it)}+meta["exclude"].orEmpty().map{ScriptCodec.pattern(it)}
    fun accepts(url:String):Boolean {val target=url.substringBefore('#');return (target.startsWith("http://")||target.startsWith("https://"))&&includes.any{Regex(it).containsMatchIn(target)}&&excludes.none{Regex(it).containsMatchIn(target)}}
    fun grants(api:String)=api in meta["grant"].orEmpty() || (if('.' in api) api.replace("GM.","GM_") else api.replace("GM_","GM.")) in meta["grant"].orEmpty()
    fun json()=JSONObject().put("id",id).put("source",source).put("enabled",enabled).put("dependencies",JSONObject(dependencies)).put("resources",JSONObject(resources)).put("resourceUrls",JSONObject(resourceUrls)).put("installUrl",installUrl)
}

object ScriptCodec {
    const val MAX_SOURCE=2*1024*1024
    private val apis=setOf("addStyle","addElement","log","info","getValue","setValue","deleteValue","listValues","getValues","setValues","deleteValues","getTab","saveTab","getTabs","addValueChangeListener","removeValueChangeListener","getResourceText","getResourceURL","getResourceUrl","xmlhttpRequest","xmlHttpRequest","registerMenuCommand","unregisterMenuCommand","openInTab","setClipboard")
    private val grants=setOf("none","unsafeWindow","window.onurlchange","window.close","window.focus")+apis.flatMap{listOf("GM_$it","GM.$it")}
    fun metadata(raw:String):Map<String,List<String>> {
        require(raw.toByteArray().size<=MAX_SOURCE){tr("脚本超过大小限制")}
        val text=raw.trimStart('\uFEFF',' ','\n','\r','\t');val header=Regex("(?m)^\\s*//\\s*==UserScript==\\s*$").find(text)?.range?.first
        require(header!=null&&header<=65536&&Regex("(?:\\s|//[^\\n]*|/\\*[\\s\\S]*?\\*/)*").matches(text.substring(0,header))){tr("缺少 UserScript 脚本头")}
        val end=Regex("(?m)^\\s*//\\s*==/UserScript==\\s*$").find(text,header)?.range?.first?:-1;require(end>=0){tr("脚本头未结束")}
        val map=linkedMapOf<String,MutableList<String>>()
        text.substring(header,end).lineSequence().forEach{line->Regex("^\\s*//\\s*@([\\w:-]+)\\s*(.*?)\\s*$").find(line)?.let{map.getOrPut(it.groupValues[1]){mutableListOf()}.add(it.groupValues[2])}}
        require(map["name"]?.firstOrNull()?.let{it.isNotBlank()&&it.length<=200}==true){tr("脚本需要 @name")}
        map["grant"].orEmpty().firstOrNull{it !in grants}?.let{throw IllegalArgumentException(tr("暂不支持授权 %1\$s，未安装此脚本",it))}
        require("unwrap" !in map){tr("暂不支持 @%1\$s，未安装此脚本","unwrap")}
        require(map["run-at"].orEmpty().all{it in listOf("document-start","document-body","document-end","document-idle")}){tr("暂不支持此 @run-at")}
        (map["match"].orEmpty()+map["exclude-match"].orEmpty()).forEach(::matchRegex)
        (map["include"].orEmpty()+map["exclude"].orEmpty()).forEach{pattern(it)}
        require(map["require"].orEmpty().size<=64&&map["resource"].orEmpty().size<=128){tr("脚本依赖过多")}
        map["require"].orEmpty().forEach{ScriptNetwork.dependencyUrl(it)}
        map["resource"].orEmpty().forEach{val parts=it.split(Regex("\\s+"),limit=2);require(parts.size==2&&parts[0].isNotBlank()){tr("无效脚本资源")};ScriptNetwork.dependencyUrl(parts[1])}
        return map
    }
    private fun escape(s:String)=s.map{if(it in "\\.^$+?()[]{}|")"\\$it"else it.toString()}.joinToString("")
    fun glob(s:String)="^"+s.split('*').joinToString(".*"){escape(it)}+"$"
    fun pattern(s:String):String {require(s.length<=2048){tr("匹配网址过长")};return if(s.startsWith('/')&&s.endsWith('/')&&s.length>2){s.substring(1,s.length-1).also{Regex(it)}}else glob(s)}
    fun matchRegex(s:String):String {
        if(s=="<all_urls>")return "^https?://[^/]+(?:/.*)?$"
        require(s.length<=2048){tr("匹配网址过长")};val m=Regex("^(https?|\\*)://([^/]+)(/.*)$").matchEntire(s)?:throw IllegalArgumentException(tr("无效 @match：%1\$s",s))
        val rawHost=m.groupValues[2].lowercase();val parts=Regex("^([^:]+)(?::([0-9]+|\\*))?$").matchEntire(rawHost)?:throw IllegalArgumentException(tr("无效匹配域名"))
        val core=parts.groupValues[1];require(core=="*"||!core.startsWith('*')||core.startsWith("*.")){tr("无效匹配域名")}
        val host=core.split('.').joinToString("."){label->if('*' in label){require(Regex("[a-z0-9*_-]+").matches(label)){tr("无效匹配域名")};label}else java.net.IDN.toASCII(label,java.net.IDN.USE_STD3_ASCII_RULES).lowercase()}
        fun hostGlob(value:String)=value.split('*').joinToString("[^/:]*"){escape(it)}
        val base=when{host=="*"->"[^/]+";host.startsWith("*.")->"(?:[^/:]+\\.)?"+hostGlob(host.removePrefix("*."));else->hostGlob(host)}
        val port=when(parts.groupValues[2]){""->if(host=="*")""else"(?::[0-9]+)?";"*"->":[0-9]+";else->":"+parts.groupValues[2]}
        val domain=base+port
        return "^"+(if(m.groupValues[1]=="*")"https?"else m.groupValues[1])+"://"+domain+glob(m.groupValues[3]).removePrefix("^")
    }
    fun javascript(script:UserScript,bridge:String="",token:String="",values:JSONObject=JSONObject()):String = ScriptRuntime.javascript(script,bridge,token,values)
    fun strings(obj:JSONObject?):Map<String,String> = obj?.keys()?.asSequence()?.associateWith{obj.getString(it)}?:emptyMap()
    fun decode(o:JSONObject)=UserScript(o.getString("id"),o.getString("source"),o.optBoolean("enabled"),strings(o.optJSONObject("dependencies")),strings(o.optJSONObject("resources")),strings(o.optJSONObject("resourceUrls")),o.optString("installUrl"))
}
