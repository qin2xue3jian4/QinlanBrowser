package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject

data class UserScript(val id:String=java.util.UUID.randomUUID().toString(),val source:String,val enabled:Boolean=false){
    val meta=ScriptCodec.metadata(source)
    val name=meta["name"]!!.first()
    val version=meta["version"]?.firstOrNull().orEmpty()
    val runAt=meta["run-at"]?.firstOrNull()?:"document-idle"
    val includes=meta["match"].orEmpty().map{ScriptCodec.matchRegex(it)}+meta["include"].orEmpty().map{ScriptCodec.glob(it)}
    val excludes=meta["exclude-match"].orEmpty().map{ScriptCodec.matchRegex(it)}+meta["exclude"].orEmpty().map{ScriptCodec.glob(it)}
    fun accepts(url:String):Boolean {val target=url.substringBefore('#');return (target.startsWith("http://")||target.startsWith("https://"))&&includes.any{Regex(it).matches(target)}&&excludes.none{Regex(it).matches(target)}}
    fun json()=JSONObject().put("id",id).put("source",source).put("enabled",enabled)
}

object ScriptCodec {
    private val grants=setOf("none","GM_addStyle","GM.addStyle","GM_log","GM.log","GM_info","GM.info","unsafeWindow")
    fun metadata(raw:String):Map<String,List<String>>{
        require(raw.toByteArray().size<=262144){tr("脚本超过 256 KB")}
        val text=raw.trimStart('\uFEFF',' ','\n','\r','\t');require(text.startsWith("// ==UserScript==")){tr("缺少 UserScript 脚本头")}
        val end=text.indexOf("// ==/UserScript==");require(end>=0){tr("脚本头未结束")}
        val map=linkedMapOf<String,MutableList<String>>()
        text.substring(0,end).lineSequence().forEach{line->Regex("^\\s*//\\s*@([\\w:-]+)\\s*(.*?)\\s*$").find(line)?.let{map.getOrPut(it.groupValues[1]){mutableListOf()}.add(it.groupValues[2])}}
        require(map["name"]?.firstOrNull()?.let{it.isNotBlank()&&it.length<=200}==true){tr("脚本需要 @name")}
        require(!map["match"].isNullOrEmpty()||!map["include"].isNullOrEmpty()){tr("脚本必须声明 @match 或 @include")}
        listOf("require","resource","connect","sandbox","unwrap").firstOrNull{it in map}?.let{throw IllegalArgumentException(tr("暂不支持 @%1\$s，未安装此脚本", it))}
        map["grant"].orEmpty().firstOrNull{it !in grants}?.let{throw IllegalArgumentException(tr("暂不支持授权 %1\$s，未安装此脚本", it))}
        require(map["run-at"].orEmpty().all{it in listOf("document-start","document-end","document-idle")}){tr("暂不支持此 @run-at")}
        (map["match"].orEmpty()+map["exclude-match"].orEmpty()).forEach(::matchRegex)
        (map["include"].orEmpty()+map["exclude"].orEmpty()).forEach{require(it.length<=2048&&(it=="*"||it.contains("://"))&&!it.startsWith('/')){tr("@include / @exclude 仅支持网址通配符")}}
        return map
    }
    private fun escape(s:String)=s.map{if(it in "\\.^$+?()[]{}|")"\\$it"else it.toString()}.joinToString("")
    fun glob(s:String)="^"+s.split('*').joinToString(".*"){escape(it)}+"$"
    fun matchRegex(s:String):String{
        if(s=="<all_urls>")return "^https?://[^/]+/.*$"
        require(s.length<=2048){tr("匹配网址过长")};val m=Regex("^(https?|\\*)://([^/]+)(/.*)$").matchEntire(s)?:throw IllegalArgumentException(tr("无效 @match：%1\$s", s))
        val host=m.groupValues[2].lowercase();require(host=="*"||Regex("(?:\\*\\.)?[a-z0-9.-]+(?::[0-9]+)?").matches(host)){tr("无效匹配域名")}
        val domain=when{host=="*"->"[^/]+";host.startsWith("*.")->"(?:[^/]+\\.)?"+escape(host.removePrefix("*."));else->escape(host)}
        return "^"+(if(m.groupValues[1]=="*")"https?"else m.groupValues[1])+"://"+domain+glob(m.groupValues[3]).removePrefix("^")
    }
    fun javascript(script:UserScript):String{
        val inc=JSONArray(script.includes).toString();val exc=JSONArray(script.excludes).toString();val id=JSONObject.quote(script.id)
        val info=JSONObject().put("script",JSONObject().put("name",script.name).put("version",script.version)).put("scriptHandler","Qinglan").toString()
        val run="""function run(){try{(function(){const unsafeWindow=window;const GM_info=$info;const GM_log=(...x)=>console.log(...x);const GM_addStyle=css=>{const s=document.createElement('style');s.textContent=String(css);const add=()=>{const root=document.head||document.documentElement;if(!root)return false;root.appendChild(s);return true;};if(!add()){const observer=new MutationObserver(()=>{if(add())observer.disconnect();});observer.observe(document,{childList:true,subtree:true});}return s;};const GM={info:GM_info,addStyle:GM_addStyle,log:GM_log};
${script.source}
}).call(window);}catch(e){console.warn('Qinglan user script failed');}}"""
        val schedule=when(script.runAt){"document-start"->"run();";"document-end"->"if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run,{once:true});else run();";else->"const idle=()=>setTimeout(run,0);if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',idle,{once:true});else idle();"}
        return """(()=>{if(window.top!==window||!/^https?:$/.test(location.protocol))return;const url=location.href.split('#')[0];if(!$inc.some(p=>new RegExp(p).test(url))||$exc.some(p=>new RegExp(p).test(url)))return;const key=Symbol.for('qinglan.script.executed');const done=window[key]||(window[key]=new Set());if(done.has($id))return;done.add($id);$run $schedule})();"""
    }
}
