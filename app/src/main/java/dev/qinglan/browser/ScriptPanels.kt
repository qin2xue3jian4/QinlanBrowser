package dev.qinglan.browser

import android.widget.Switch
import java.net.URI

class ScriptPanels(private val p:BrowserPanels) {
    private val a get()=p.a
    private val u get()=p.u
    private var loading=false
    private fun userAgent()=a.current?.web?.settings?.userAgentString?:android.webkit.WebSettings.getDefaultUserAgent(a)
    private fun attach(){a.tabs.filterNot{it.incognito}.forEach{it.web?.let(a.scripts::attach)}}
    private fun change(items:List<UserScript>){runCatching{a.scripts.save(items);attach()}.onSuccess{a.toast(tr("已保存，下次加载网页生效"));p.pages.refresh()}.onFailure{p.info(tr("无法保存脚本"),it.message.orEmpty())}}
    fun show(){p.pages.show(tr("用户脚本")){col->
        col.addView(u.label(tr("支持常用 GM 接口、脚本配置存储、网络请求与依赖。脚本可读取和修改匹配网页，请只安装可信来源。"),13f,u.muted))
        if(!a.scripts.documentStart)col.addView(u.label(tr("当前 WebView 不支持文档开始注入：document-start 脚本无法启用，其余脚本在页面加载完成后执行。"),13f))
        if(!a.scripts.bridgeSupported)col.addView(u.label(tr("请更新系统 WebView 以使用脚本存储、网络请求和菜单命令。"),13f))
        if(a.isHttp(a.currentUrl))a.scripts.commands(a.current?.web).forEach{command->col.addView(u.button(command.label){p.pages.close();command.run()})}
        col.addView(u.button(tr("从文件导入 .user.js"),true){a.readDocument{preview(it)}})
        col.addView(u.button(tr("从网址安装")){p.pages.show(tr("从网址安装")){body->val url=u.edit(tr("脚本网址"));body.addView(u.label(tr("可输入 Greasy Fork 脚本页面或 .user.js 安装链接。"),13f));body.addView(url);body.addView(u.button(tr("检查脚本")){fromUrl(url.text.toString().trim())})}})
        col.addView(u.button(tr("浏览 Greasy Fork")){p.pages.close();a.open("https://greasyfork.org/")})
        col.addView(u.button(tr("粘贴脚本")){p.pages.show(tr("粘贴用户脚本")){body->val code=u.edit("// ==UserScript==",multiline=true);body.addView(code);body.addView(u.button(tr("检查脚本")){preview(code.text.toString())})}})
        val items=runCatching{a.scripts.read()}.getOrElse{col.addView(u.label(tr("读取脚本失败：%1\$s",it.message)));return@show}
        if(items.isEmpty())col.addView(u.label(tr("尚未安装脚本"),14f,u.muted))
        items.forEach{script->col.addView(Switch(a).apply{text=script.name;setTextColor(u.text);minimumHeight=u.dp(52);isChecked=script.enabled;isEnabled=a.scripts.documentStart||script.runAt!="document-start";setOnCheckedChangeListener{_,enabled->change(items.map{if(it.id==script.id)it.copy(enabled=enabled)else it})}});col.addView(u.item(tr("查看 · %1\$s",script.name),script.version.ifBlank{script.runAt}){detail(script.id)})}
        col.addView(u.button(tr("刷新当前网页应用脚本")){p.pages.close();a.reload()})
        col.addView(u.item(tr("支持范围"),tr("不兼容完整油猴扩展 API")){p.info(tr("脚本支持范围"),tr("支持网址通配符和正则、start/body/end/idle、iframe（@noframes 可关闭）、@require / @resource 缓存、GM 配置存储及变更监听、菜单命令、样式与元素、资源读取和 GM_xmlhttpRequest / GM.xmlHttpRequest。\n\n网络请求按 @connect 限制，不自动携带浏览器登录 Cookie；支持文本、JSON、ArrayBuffer、Blob 和 HTML 文档响应。剪贴板写入需要 GM_setClipboard 授权。\n\n脚本与网页共享 JavaScript 环境，无独立扩展沙箱。无自动更新、GM_download、通知、Cookie 管理或完整油猴扩展接口。联网依赖仅允许 HTTPS，支持 data 内联依赖。单脚本源代码上限 2 MB，网络响应上限 4 MB。\n\n导出 .user.js 只包含源代码，重新安装时重新下载依赖；配置存储不包含在设置备份中。"))})
    }}
    fun fromUrl(raw:String){
        val url=runCatching{NavigationPolicy.secure(installUrl(raw),a.prefs.getBoolean("httpsOnly",false))}.getOrElse{p.info(tr("无法安装"),it.message.orEmpty());return}
        background({val response=ScriptNetwork.fetch(url,allowed={ScriptNetwork.isInstallUrl(it)||it.startsWith("https://")});require(response.status in 200..299){"HTTP ${response.status}"};UserScript(source=response.text(),installUrl=response.url)}){preview(it.source,it.installUrl)}
    }
    private fun <T> background(work:()->T,done:(T)->Unit){if(loading){a.toast(tr("正在读取脚本及依赖"));return};loading=true;a.toast(tr("正在读取脚本及依赖"));Thread{
        val result=runCatching(work);a.runOnUiThread{loading=false;if(a.isDestroyed)return@runOnUiThread;result.onSuccess(done).onFailure{p.info(tr("无法安装"),it.message.orEmpty())}}
    }.start()}
    fun preview(raw:String,url:String="") {
        val script=runCatching{UserScript(source=raw,installUrl=url)}.getOrElse{p.info(tr("脚本不兼容"),it.message.orEmpty());return}
        val existing=runCatching{a.scripts.read()}.getOrElse{p.info(tr("读取失败"),it.message.orEmpty());return}.find{it.name==script.name&&it.meta["namespace"]==script.meta["namespace"]}
        p.pages.show(tr("安装用户脚本")){col->
            col.addView(u.title(script.name));col.addView(u.label(tr("版本：%1\$s\n执行：%2\$s\n匹配：\n",script.version.ifBlank{tr("未注明")},script.runAt)+(script.meta["match"].orEmpty()+script.meta["include"].orEmpty()).ifEmpty{listOf("http://*/*","https://*/*")}.joinToString("\n")))
            col.addView(u.label(tr("授权：%1\$s\n联网范围：%2\$s\n依赖与资源：%3\$s",script.meta["grant"].orEmpty().joinToString(", ").ifBlank{"none"},script.meta["connect"].orEmpty().joinToString(", ").ifBlank{"self"},(script.meta["require"].orEmpty()+script.meta["resource"].orEmpty()).joinToString("\n").ifBlank{"—"}),13f))
            if(url.isNotBlank())col.addView(u.label(url,12f,u.muted))
            col.addView(u.label(tr("脚本与网页共享 JavaScript 环境，请确认来源与授权。"),13f,u.muted))
            col.addView(u.label(if(existing==null)tr("安装后默认关闭，可在脚本列表手动启用。")else tr("更新保留原脚本的启用状态和配置。"),13f,u.muted))
            col.addView(u.button(tr("查看源代码")){p.pages.show(tr("脚本源代码")){it.addView(u.label(script.source,12f).apply{setTextIsSelectable(true)})}})
            col.addView(u.button(if(existing==null)tr("安装")else tr("更新脚本"),true){val ua=userAgent();background({ScriptNetwork.prepare(script,ua)}){prepared->
                runCatching{val old=a.scripts.read();val prior=old.find{it.name==script.name&&it.meta["namespace"]==script.meta["namespace"]};val next=if(prior==null)prepared else prepared.copy(id=prior.id,enabled=prior.enabled);a.scripts.save(if(prior==null)old+next else old.map{if(it.id==prior.id)next else it});attach()}
                    .onSuccess{p.pages.back();p.pages.refresh();a.toast(if(existing==null)tr("已安装，默认关闭")else tr("已保存，下次加载网页生效"))}.onFailure{p.info(tr("无法安装"),it.message.orEmpty())}
            }})
        }
    }
    private fun detail(id:String){p.pages.show(tr("脚本详情")){col->val script=a.scripts.read().find{it.id==id}?:return@show;col.addView(u.label(script.name+"\n"+(script.meta["match"].orEmpty()+script.meta["include"].orEmpty()).joinToString("\n")));col.addView(u.button(tr("查看源代码")){p.pages.show(tr("脚本源代码")){it.addView(u.label(script.source,12f).apply{setTextIsSelectable(true)})}});if(script.installUrl.isNotBlank())col.addView(u.button(tr("检查脚本更新")){fromUrl(script.installUrl)});col.addView(u.button(tr("导出 .user.js")){a.writeDocument("qinglan-script.user.js","text/javascript",script.source)});col.addView(u.button(tr("删除脚本")){p.confirm(tr("删除脚本"),script.name){change(a.scripts.read().filterNot{it.id==id});p.pages.back()}})}}
    companion object {
        fun installUrl(raw:String):String {val uri=URI(raw);require(uri.scheme in listOf("http","https")&&uri.userInfo==null){"Invalid script URL"};if(ScriptNetwork.isInstallUrl(raw))return raw
            require(uri.scheme=="https"&&uri.host in listOf("greasyfork.org","www.greasyfork.org","sleazyfork.org","www.sleazyfork.org")){"Enter a Greasy Fork page or .user.js URL"}
            val id=Regex("/(?:[^/]+/)?scripts/(\\d+)(?:[-/]|$)").find(uri.path)?.groupValues?.get(1)?:error("Invalid script page URL")
            return "https://${uri.host}/scripts/$id/code/script.user.js"
        }
    }
}
