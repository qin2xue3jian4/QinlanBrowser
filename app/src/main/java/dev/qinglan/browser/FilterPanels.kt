package dev.qinglan.browser

import android.widget.Switch
import android.text.TextUtils

class FilterPanels(private val p: BrowserPanels) {
    private val a get() = p.a
    private val u get() = a.ui
    private val filters get() = a.filtering
    private val subscriptions get() = filters.subscriptions
    private fun changed() { subscriptions.rebuild { a.runOnUiThread { if (!a.isDestroyed) p.pages.refresh() } } }
    fun show() { p.pages.show(tr("广告过滤")) { col ->
        col.addView(Switch(a).apply { text=tr("启用广告过滤"); setTextColor(u.text); isChecked=a.prefs.getBoolean("adblockEnabled",true); setOnCheckedChangeListener { _, b -> a.prefs.edit().putBoolean("adblockEnabled",b).apply() } })
        col.addView(u.label(subscriptions.status,13f,u.muted))
        val url = a.currentUrl
        if (a.isHttp(url)) {
            col.addView(Switch(a).apply { text=tr("过滤当前网站 · %1\$s", FilterEngine.host(url)); setTextColor(u.text); isChecked=a.store.siteBool(url,"adblock",true); setOnCheckedChangeListener { _, b -> a.store.setSiteBool(url,"adblock",b) } })
            col.addView(u.item(tr("本页拦截记录"),tr("已拦截 %1\$s 次请求", a.current?.filterSession?.count() ?: 0)){ logs() })
            col.addView(u.item(tr("手动屏蔽元素"),tr("点选网页区域，确认后为此网站保存规则")){ filters.picker() })
        }
        col.addView(u.item(tr("规则订阅"),tr("EasyList、EasyList China 和自定义订阅")){ lists() })
        col.addView(u.item(tr("自定义规则"),tr("编辑、删除或导入自己编写的规则")){ custom() })
        col.addView(u.item(tr("规则来源与支持范围")){ information() })
        col.addView(u.button(tr("应用并刷新网页"),true){ p.pages.close(); a.reload() })
        col.addView(u.label(tr("开关、规则和订阅更改后刷新网页生效。拦截记录仅保存在本次页面会话中。"),12f,u.muted))
    } }
    fun lists() { p.pages.show(tr("规则订阅")) { col ->
        col.addView(Switch(a).apply { text=tr("自动更新（使用应用时，每 4 天）");setTextColor(u.text);isChecked=a.prefs.getBoolean("adblockAutoUpdate",true);setOnCheckedChangeListener { _, b -> a.prefs.edit().putBoolean("adblockAutoUpdate",b).apply() } })
        col.addView(u.label(subscriptions.status,13f,u.muted))
        subscriptions.entries().forEach { entry ->
            col.addView(u.item((if(entry.enabled)"✓ " else "")+entry.name,subscriptions.details[entry.id] ?: tr("尚未下载")){ detail(entry.id) })
        }
        col.addView(u.button(if(subscriptions.updating)tr("更新中…") else tr("立即更新"),true){
            subscriptions.update { a.runOnUiThread { if(!a.isDestroyed) { p.pages.refresh(); a.toast(tr("规则更新完成，请刷新网页")) } } };p.pages.refresh()
        }.apply { isEnabled=!subscriptions.updating })
        col.addView(u.button(tr("刷新状态")){p.pages.refresh()})
        col.addView(u.button(tr("添加订阅")){ p.pages.show(tr("添加订阅")) { form ->
            val name=u.edit(tr("订阅名称"));val url=u.edit(tr("HTTPS 规则地址"));form.addView(name);form.addView(url)
            form.addView(u.button(tr("保存"),true){ runCatching { subscriptions.save(subscriptions.entries()+FilterSubscriptions.newEntry(name.text.toString(),url.text.toString())) }.onSuccess { p.pages.back();changed() }.onFailure { a.toast(it.message ?: tr("无法保存")) } })
        } })
        col.addView(u.label(tr("规则首次使用需要下载。更新只访问订阅服务器，不发送浏览记录和网站 Cookie。每个订阅最多 8 MB。"),12f,u.muted))
    } }
    private fun detail(id: String) { p.pages.show(tr("订阅详情")) { col ->
        val entry=subscriptions.entries().find { it.id==id } ?: return@show
        col.addView(u.title(entry.name));col.addView(u.label(entry.url,13f,u.muted).apply { setTextIsSelectable(true) })
        col.addView(Switch(a).apply { text=tr("启用此订阅");setTextColor(u.text);isChecked=entry.enabled;setOnCheckedChangeListener { _, b -> subscriptions.save(subscriptions.entries().map { if(it.id==id)it.copy(enabled=b) else it });changed() } })
        col.addView(u.label(subscriptions.details[id] ?: tr("尚未下载")))
        col.addView(u.button(tr("删除订阅")){ p.confirm(tr("删除订阅"),entry.name){ subscriptions.save(subscriptions.entries().filterNot { it.id==id });p.pages.back();changed() } })
    } }
    private fun custom() { p.pages.show(tr("自定义规则")) { col ->
        col.addView(u.label(tr("每行一条。删除手动屏蔽生成的规则即可恢复元素。"),13f,u.muted))
        val edit=u.edit(tr("例如：example.com##.ad-banner"),subscriptions.custom(),true);col.addView(edit)
        col.addView(u.button(tr("保存规则"),true){ save(edit.text.toString()) })
        col.addView(u.button(tr("导入规则文本")){ a.readDocument("text/*"){ text -> if(text.length>65_536)a.toast(tr("自定义规则最多 64K 字符"))else edit.setText(text) } })
        col.addView(u.button(tr("导出规则文本")){ a.writeDocument("qinglan-filters.txt","text/plain",edit.text.toString()) })
    } }
    private fun save(text: String) { runCatching { subscriptions.saveCustom(text) }.onSuccess { changed();a.toast(tr("规则已保存，请刷新网页")) }.onFailure { a.toast(it.message ?: tr("保存失败")) } }
    fun picked(url: String, selector: String) {
        val rule="${FilterEngine.host(url)}##$selector"
        p.confirm(tr("屏蔽此元素"), tr("仅用于 %1\$s\n\n%2\$s\n\n可在自定义规则中删除，刷新后恢复。", FilterEngine.host(url), selector)){ runCatching { subscriptions.saveCustom(subscriptions.custom().trim()+"\n"+rule) }.onSuccess { subscriptions.rebuild { a.runOnUiThread { if(!a.isDestroyed && a.currentUrl==url) { p.pages.close();a.reload() } } } }.onFailure { a.toast(it.message ?: tr("保存失败")) } }
    }
    private fun logs() { val session=a.current?.filterSession; p.pages.show(tr("本页拦截记录")) { col ->
        col.addView(u.label(tr("本页共 %1\$s 次，显示最近 100 条。", session?.count() ?: 0),13f,u.muted))
        session?.logs()?.forEach { (url,rule) ->
            col.addView(u.label(rule,14f).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END })
            col.addView(u.item(url.take(180),tr("查看完整地址与规则")){ p.pages.show(tr("拦截详情")){ detail -> detail.addView(u.label(url).apply { setTextIsSelectable(true) });detail.addView(u.label(tr("命中规则：\n%1\$s", rule)));detail.addView(u.button(tr("复制地址")){ a.copy(tr("资源地址"),url) }) } })
        }
        col.addView(u.button(tr("刷新记录")){ p.pages.refresh() })
    } }
    fun information() { p.info(tr("规则来源与支持范围"), tr("默认订阅：EasyList、EasyList China\n作者：The EasyList authors\n项目：https://easylist.to/\n中文规则：https://github.com/easylist/easylistchina\n许可：GPL v3 或 CC BY-SA 3.0（或后续版本），以各订阅声明为准。\nhttps://easylist.to/pages/licence.html\n\n应用只预置订阅地址，不打包第三方广告规则；下载后保留原文件及来源说明。\n\n支持常用 URL 规则、通配符、域名限定、第三方条件、@@ 例外、标准 CSS 隐藏和隐藏例外。资源类型只在 WebView 提供明确类型时匹配。\n\n不支持的选项和高级语法整条跳过，包括脚本修正、重定向替代、正则规则与程序化元素匹配。不直接拦截主文档；Service Worker 请求暂不处理。并非完整 uBO/ABP 引擎。\n\n第三方域名判断使用 Public Suffix List（Mozilla / 社区，MPL 2.0）。https://publicsuffix.org/\n规则与设置可通过备份保存，下载缓存和拦截记录不导出。")) }
}
