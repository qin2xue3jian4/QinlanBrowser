package dev.qinglan.browser

import android.widget.Switch

class ScriptPanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    private fun change(items:List<UserScript>){runCatching{a.scripts.save(items);a.tabs.forEach{it.web?.let(a.scripts::attach)}}.onSuccess{a.toast("已保存，下次加载网页生效");p.pages.refresh()}.onFailure{p.info("无法保存脚本",it.message.orEmpty())}}
    fun show(){p.pages.show("用户脚本"){col->
        col.addView(u.label("轻量兼容：仅在主网页运行，支持网址匹配、执行时机和 GM_addStyle。脚本可以读取及修改匹配网页的内容，请只安装可信来源。",13f,u.muted))
        if(!a.scripts.documentStart)col.addView(u.label("当前 WebView 不支持文档开始注入：document-start 脚本无法启用，其余脚本在页面加载完成后执行。",13f))
        col.addView(u.button("从文件导入 .user.js",true){a.readDocument{preview(it)}})
        col.addView(u.button("粘贴脚本"){p.pages.show("粘贴用户脚本"){body->val code=u.edit("// ==UserScript==",multiline=true);body.addView(code);body.addView(u.button("检查脚本"){preview(code.text.toString())})}})
        val items=runCatching{a.scripts.read()}.getOrElse{col.addView(u.label("读取脚本失败：${it.message}"));return@show}
        if(items.isEmpty())col.addView(u.label("尚未安装脚本",14f,u.muted))
        items.forEach{script->col.addView(Switch(a).apply{text=script.name;setTextColor(u.text);minimumHeight=u.dp(52);isChecked=script.enabled;isEnabled=a.scripts.documentStart||script.runAt!="document-start";setOnCheckedChangeListener{_,enabled->change(items.map{if(it.id==script.id)it.copy(enabled=enabled)else it})}});col.addView(u.item("查看 · ${script.name}",script.version.ifBlank{script.runAt}){detail(script.id)})}
        col.addView(u.button("刷新当前网页应用脚本"){p.pages.close();a.reload()})
        col.addView(u.item("支持范围","不兼容完整油猴扩展 API"){p.info("脚本支持范围","支持 @match、@include 通配符、@exclude、@exclude-match、@run-at（start/end/idle），以及 GM_addStyle / GM.addStyle、GM_log、GM_info、unsafeWindow。\n\n只在主网页运行，不在 iframe 中注入。脚本与网页共享 JavaScript 环境，没有独立的扩展沙箱。\n\n暂不支持 @require、@resource、GM_xmlhttpRequest、GM_setValue 等高级接口；声明这些需求的脚本会拒绝安装。没有自动更新和自动下载依赖。\n\n脚本单独导出 .user.js，不包含在浏览器设置备份中。")})
    }}
    private fun preview(raw:String){val script=runCatching{UserScript(source=raw)}.getOrElse{p.info("脚本不兼容",it.message.orEmpty());return};p.pages.show("安装用户脚本"){col->col.addView(u.title(script.name));col.addView(u.label("版本：${script.version.ifBlank{"未注明"}}\n执行：${script.runAt}\n匹配：\n"+(script.meta["match"].orEmpty()+script.meta["include"].orEmpty()).joinToString("\n")));col.addView(u.label("安装后默认关闭，可在脚本列表手动启用。",13f,u.muted));col.addView(u.button("安装",true){val old=runCatching{a.scripts.read()}.getOrElse{p.info("读取失败",it.message.orEmpty());return@button};if(old.any{it.name==script.name&&it.meta["namespace"]==script.meta["namespace"]}){a.toast("已有同名脚本，请先删除旧版再安装");return@button};runCatching{a.scripts.save(old+script)}.onSuccess{p.pages.back();p.pages.refresh();a.toast("已安装，默认关闭")}.onFailure{p.info("无法安装",it.message.orEmpty())}})}}
    private fun detail(id:String){p.pages.show("脚本详情"){col->val script=a.scripts.read().find{it.id==id}?:return@show;col.addView(u.label(script.name+"\n"+(script.meta["match"].orEmpty()+script.meta["include"].orEmpty()).joinToString("\n")));col.addView(u.button("查看源代码"){p.pages.show("脚本源代码"){it.addView(u.label(script.source,12f).apply{setTextIsSelectable(true)})}});col.addView(u.button("导出 .user.js"){a.writeDocument("qinglan-script.user.js","text/javascript",script.source)});col.addView(u.button("删除脚本"){p.confirm("删除脚本",script.name){change(a.scripts.read().filterNot{it.id==id});p.pages.back()}})}}
}
