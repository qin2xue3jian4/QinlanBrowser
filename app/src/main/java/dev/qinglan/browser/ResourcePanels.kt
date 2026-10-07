package dev.qinglan.browser

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch

class ResourcePanels(private val p: BrowserPanels) {
    private val a get()=p.a
    private val u get()=a.ui
    fun show() {
        val tab=a.current ?: return
        var category=0
        val categories=listOf(tr("媒体"),tr("视频"),tr("音频"),tr("图片"),tr("播放列表"),tr("分片"),"Blob",tr("全部"))
        p.pages.show(tr("网页资源")) { col ->
            col.addView(Switch(a).apply { text=tr("收集网页资源");setTextColor(u.text);isChecked=a.sniffer.enabled();setOnCheckedChangeListener { _, b -> a.prefs.edit().putBoolean("resourceSniffing",b).apply() } })
            col.addView(u.label(tab.title.take(120),14f,u.muted).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END })
            val all=tab.resources.list()
            fun matches(index:Int,resource:WebResource):Boolean=with(resource){when(index) {
                0->kind in setOf(ResourceKind.VIDEO,ResourceKind.AUDIO,ResourceKind.HLS,ResourceKind.DASH,ResourceKind.BLOB)
                1->kind==ResourceKind.VIDEO;2->kind==ResourceKind.AUDIO;3->kind==ResourceKind.IMAGE
                4->playlist;5->kind==ResourceKind.SEGMENT;6->kind==ResourceKind.BLOB;else->true
            } }
            val entries=all.filter{matches(category,it)}
            col.addView(u.item(tr("分类 · %1\$s", categories[category]),tr("显示 %1\$s 项 · 本页共 %2\$s 项（最多 300）", entries.size, all.size)){p.choose(tr("资源分类"),categories.mapIndexed{i,name->"$name · ${all.count{matches(i,it)}}"},category){category=it;p.pages.refresh()}})
            val row=u.row()
            row.addView(u.button(tr("重新扫描"),true){tab.web?.let { a.sniffer.scan(it,tab.resources){if(!a.isDestroyed)p.pages.refresh()} } ?: p.pages.refresh()},LinearLayout.LayoutParams(0,-2,1f))
            row.addView(u.button(tr("清空列表")){tab.resources.clear();p.pages.refresh()},LinearLayout.LayoutParams(0,-2,1f));col.addView(row)
            if(entries.isEmpty())col.addView(u.label(tr("暂无此类资源。可先播放音视频或滚动网页，再重新扫描；图片在分类中单独查看。"),14f,u.muted))
            entries.forEach { resource ->
                val box=u.column(4).apply { minimumHeight=u.dp(76);isClickable=true;isFocusable=true;setOnClickListener{detail(resource)};contentDescription="${resource.kind.label} ${resource.name}" }
                box.addView(u.label(resource.name,14f).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END },LinearLayout.LayoutParams(-1,u.dp(30)))
                box.addView(u.label("${resource.kind.label} · ${FilterEngine.host(resource.url.removePrefix("blob:"))}",12f,u.muted).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,u.dp(26)))
                col.addView(box);col.addView(u.rule())
            }
            col.addView(u.item(tr("资源识别与下载说明")){p.info(tr("资源识别与下载说明"),tr("资源按标签页保存在内存中，页面导航或刷新时重置，不包含在备份中。收集开关不影响正常浏览，更改后刷新网页以完全启用或停用页面观察。\n\n发现地址不代表已验证可下载。普通 HTTP(S) 资源可交给系统下载器，携带当前该地址的 Cookie、浏览器标识和发现资源时的来源页。\n\nHLS / DASH 是播放列表。可复制或交给支持它们的外部下载器；下载清单不会合并分片。外部应用仅接收 URL 和资源类型，不自动接收 Cookie。\n\nBlob 是页面内地址，不能直接交给下载器；可查找同时发现的播放列表或 HTTP 资源。DRM 媒体不能靠嗅探直接解密。\n\n识别结合请求地址、页面元素、加载记录与接口响应类型，不读取响应正文。不保证发现所有跨域内嵌页面、Service Worker、重定向和加密播放器资源。"))})
        }
        tab.web?.let { a.sniffer.scan(it,tab.resources){if(!a.isDestroyed)p.pages.refresh()} }
    }
    private fun detail(resource: WebResource) { p.pages.show(resource.kind.label) { col ->
        col.addView(u.title(resource.name).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END })
        col.addView(u.label(tr("来源：%1\$s", tr(resource.source))+(if(resource.mime.isNotBlank())tr("\n类型：%1\$s", resource.mime) else ""),13f,u.muted))
        col.addView(u.label(resource.url,13f).apply{setTextIsSelectable(true)})
        if(resource.kind==ResourceKind.BLOB){
            col.addView(u.label(tr("读取当前网页生成的 Blob 文件，选择位置保存；每个文件最多 64 MB。"),14f,u.muted))
            col.addView(u.button(tr("下载资源"),true){a.requestDownload(resource.url,resource.userAgent,"",resource.mime,resource.referer)})
        }
        else {
            if(resource.playlist)col.addView(u.label(tr("这是播放列表，完整视频需要下载分片并合并。下方下载操作只保存清单。"),14f,u.muted))
            if(resource.kind==ResourceKind.SEGMENT)col.addView(u.label(tr("这是单个媒体分片，通常不是完整音视频。"),14f,u.muted))
            col.addView(u.button(if(resource.playlist)tr("下载播放列表（不含视频）") else if(resource.kind==ResourceKind.SEGMENT)tr("下载此分片") else tr("下载资源"),true){a.requestDownload(resource.url,resource.userAgent,"",resource.mime,resource.referer)})
            col.addView(u.button(tr("用外部应用打开")){runCatching { val intent=Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(resource.url),resource.mime.ifBlank{ResourceClassifier.mime(resource.kind)});a.startActivity(Intent.createChooser(intent,tr("选择播放器或下载器"))) }.onFailure { a.toast(tr("没有可用的外部应用")) }})
            col.addView(u.button(tr("在新标签页打开")){p.pages.close();a.open(resource.url,true)})
        }
        col.addView(u.button(tr("复制资源地址")){a.copy(tr("资源地址"),resource.url)})
    } }
}
