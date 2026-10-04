package dev.qinglan.browser
import android.webkit.CookieManager
import android.widget.*
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature

class CookiePanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    private val pages get()=p.pages
    fun show(){val url=a.currentUrl;val manager=a.cookieManager();if(!a.isHttp(url)){a.toast(tr("请先打开目标网站"));return};pages.show(tr("Cookie 管理")){col->col.addView(u.label(android.net.Uri.parse(url).host.orEmpty()+" · "+a.accounts.label(a.current?.accountId.orEmpty(),a.store.siteKey(url))));col.addView(u.label(tr("仅当前 URL 可读取的 Cookie，其他路径、子域需分别访问。"),12f,u.muted));val entries=mutableListOf<CookieCodec.Entry>();var skipped=0
        val supported=WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO))runCatching{CookieManagerCompat.getCookieInfo(manager,url)}.onSuccess{raw->raw.forEach{runCatching{CookieCodec.parseSetCookie(it,url)}.onSuccess(entries::add).onFailure{skipped++}}}.onFailure{skipped++}
        col.addView(u.label(tr("%1\$s 项，%2\$s 项无法读取", entries.size, skipped)));entries.forEach{e->col.addView(u.item(e.name,"${e.domain}${e.path}"){edit(e,url,manager)})}
        col.addView(u.button(tr("导入 Cookie"),true){import(url,manager)});col.addView(u.button(tr("导出 JSON")){if(!supported){a.toast(tr("请更新系统 WebView 后再导出"));return@button};p.confirm(tr("导出 Cookie"),tr("此文件含明文登录凭据。仅导出当前可读取的 %1\$s 项；跳过 %2\$s 项。请妥善保存。", entries.size, skipped)){a.writeDocument("cookies-${android.net.Uri.parse(url).host}.json","application/json",CookieCodec.export(entries))}});col.addView(u.button(tr("新增 Cookie")){edit(null,url,manager)})
    }}
    private fun import(url:String,manager:CookieManager){pages.show(tr("导入 Cookie")){col->val input=u.edit(tr("粘贴 Cookie-Editor JSON"),multiline=true);col.addView(input);col.addView(u.button(tr("检查并预览"),true){preview(input.text.toString(),url,manager)});col.addView(u.button(tr("选择 JSON 文件")){a.readDocument{preview(it,url,manager)}})}}
    private fun preview(raw:String,url:String,manager:CookieManager){val parsed=runCatching{CookieCodec.parseImport(raw,url)}.getOrElse{p.info(tr("无法导入"),it.message.orEmpty());return};pages.show(tr("导入预览")){col->col.addView(u.label(tr("可导入 %1\$s 项；提示 %2\$s 项\n", parsed.entries.size, parsed.warnings.size)+parsed.warnings.take(12).joinToString("\n")));if(parsed.entries.isNotEmpty())col.addView(u.button(tr("导入有效条目"),true){var pending=parsed.entries.size;var success=0;parsed.entries.forEach{e->manager.setCookie(e.url(),e.header()){ok->if(ok)success++;if(--pending==0){manager.flush();pages.back();p.info(tr("导入完成"),tr("成功 %1\$s 项；拒绝 %2\$s 项。返回网页刷新后检查登录状态。", success, parsed.entries.size-success))}}}})}}
    private fun edit(entry:CookieCodec.Entry?,url:String,manager:CookieManager){pages.show(if(entry==null)tr("新增 Cookie")else tr("编辑 Cookie")){col->val name=u.edit(tr("名称"),entry?.name.orEmpty());val value=u.edit(tr("值"),entry?.value.orEmpty(),true);val domain=u.edit(tr("域名"),entry?.domain?:android.net.Uri.parse(url).host.orEmpty());val path=u.edit(tr("路径"),entry?.path?:"/");val secure=CheckBox(a).apply{text="Secure";isChecked=entry?.secure?:true;setTextColor(u.text)};val httpOnly=CheckBox(a).apply{text="HttpOnly";isChecked=entry?.httpOnly?:false;setTextColor(u.text)};listOf(name,value,domain,path,secure,httpOnly).forEach(col::addView)
        if(entry!=null){name.isEnabled=false;domain.isEnabled=false;path.isEnabled=false;col.addView(u.label(tr("保留 SameSite、有效期和 hostOnly 属性。"),12f,u.muted))}
        col.addView(u.button(tr("保存"),true){val e=CookieCodec.Entry(name.text.toString(),value.text.toString(),domain.text.toString(),path.text.toString(),secure.isChecked,httpOnly.isChecked,entry?.hostOnly?:!domain.text.startsWith('.'),entry?.sameSite?:"unspecified",entry?.expiry);val parsed=runCatching{CookieCodec.parseImport(CookieCodec.export(listOf(e)),url)}.getOrElse{p.info(tr("无法保存"),it.message.orEmpty());return@button};if(parsed.entries.size!=1){p.info(tr("无法保存"),parsed.warnings.joinToString("\n"));return@button};manager.setCookie(e.url(),e.header()){ok->manager.flush();if(ok){pages.back();a.toast(tr("已保存"))}else a.toast(tr("浏览器拒绝此 Cookie"))}})
        if(entry!=null)col.addView(u.button(tr("删除此 Cookie")){p.confirm(tr("删除 Cookie"),tr("删除可能使当前网站退出登录。")){manager.setCookie(entry.url(),entry.header(true)){ok->manager.flush();if(ok)pages.back()else a.toast(tr("删除失败"))}}})
    }}
}
