package dev.qinglan.browser

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.webkit.WebSettings

class DownloadPanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    private val manager get()=a.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    internal data class Entry(val id:Long,val title:String,val url:String,val mime:String,val status:Int,val reason:Int,val done:Long,val total:Long)
    private fun entries():List<Entry>{
        val ids=p.prefs.getStringSet("downloads",emptySet()).orEmpty().mapNotNull{it.toLongOrNull()}.toLongArray()
        if(ids.isEmpty())return emptyList()
        return runCatching{buildList{manager.query(DownloadManager.Query().setFilterById(*ids))?.use{c->
            fun str(key:String)=c.getString(c.getColumnIndexOrThrow(key)).orEmpty()
            fun long(key:String)=c.getLong(c.getColumnIndexOrThrow(key))
            while(c.moveToNext())add(Entry(long(DownloadManager.COLUMN_ID),str(DownloadManager.COLUMN_TITLE),str(DownloadManager.COLUMN_URI),str(DownloadManager.COLUMN_MEDIA_TYPE),long(DownloadManager.COLUMN_STATUS).toInt(),long(DownloadManager.COLUMN_REASON).toInt(),long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
        }}.sortedByDescending{it.id}}.getOrElse{a.toast("系统下载记录暂不可用");emptyList()}
    }
    private fun size(n:Long)=if(n<0)"大小未知"else Formatter.formatFileSize(a,n)
    private fun state(e:Entry)=when(e.status){
        DownloadManager.STATUS_SUCCESSFUL->"已完成"
        DownloadManager.STATUS_FAILED->"失败："+when(e.reason){
            DownloadManager.ERROR_INSUFFICIENT_SPACE->"存储空间不足";DownloadManager.ERROR_FILE_ALREADY_EXISTS->"同名文件已存在";DownloadManager.ERROR_DEVICE_NOT_FOUND->"存储设备不可用";DownloadManager.ERROR_CANNOT_RESUME->"无法续传";DownloadManager.ERROR_HTTP_DATA_ERROR->"网络传输错误";DownloadManager.ERROR_UNKNOWN->"系统未提供具体原因";in 400..599->"HTTP ${e.reason}";else->"系统错误 ${e.reason}"}
        DownloadManager.STATUS_PAUSED->when(e.reason){DownloadManager.PAUSED_WAITING_FOR_NETWORK->"等待网络";DownloadManager.PAUSED_QUEUED_FOR_WIFI->"等待 Wi-Fi";DownloadManager.PAUSED_WAITING_TO_RETRY->"等待系统重试";else->"已暂停"}
        DownloadManager.STATUS_PENDING->"等待下载"
        else->"下载中"}
    private fun summary(e:Entry)=state(e)+" · "+size(e.done)+" / "+size(e.total)+(if(e.total>0&&e.status!=DownloadManager.STATUS_SUCCESSFUL)" · ${(100.0*e.done/e.total).toInt().coerceIn(0,100)}%" else "")
    fun show(){var query="";var filter=0;p.pages.show("下载"){col->
        col.addView(u.label("下载由系统继续处理。点击记录查看详情。",13f,u.muted))
        val input=u.edit("搜索下载文件",query);col.addView(input)
        val tabs=u.row();listOf("全部","进行中","已完成","失败").forEachIndexed{i,title->tabs.addView(u.button((if(filter==i)"✓ "else"")+title){filter=i;p.pages.refresh()},android.widget.LinearLayout.LayoutParams(0,-2,1f))};col.addView(tabs)
        val rows=u.column();col.addView(rows)
        var fingerprint=""
        fun update(){val list=entries().filter{(it.title.contains(query,true)||it.url.contains(query,true))&&when(filter){1->it.status in setOf(DownloadManager.STATUS_PENDING,DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PAUSED);2->it.status==DownloadManager.STATUS_SUCCESSFUL;3->it.status==DownloadManager.STATUS_FAILED;else->true}};val next=list.toString()+query+filter;if(next==fingerprint)return;fingerprint=next;rows.removeAllViews()
            if(list.isEmpty())rows.addView(u.label("没有匹配的下载记录"))
            list.forEach{e->rows.addView(u.item(e.title,summary(e)){detail(e.id)})}
        }
        input.onChange{query=it;update()}
        update();col.addView(u.button("刷新列表"){update()})
        val tick=object:Runnable{override fun run(){if(!col.isAttachedToWindow)return;update();col.postDelayed(this,1500)}}
        col.postDelayed(tick,1500)
    }}
    private fun detail(id:Long){p.pages.show("下载详情"){col->
        val entry=entries().firstOrNull{it.id==id}
        if(entry==null){col.addView(u.label("此系统下载记录已不存在"));return@show}
        val e=entry
        col.addView(u.title(e.title));val status=u.label(summary(e));col.addView(status)
        col.addView(u.label("来源：${android.net.Uri.parse(e.url).host.orEmpty()}\n类型：${e.mime.ifBlank{"未知"}}",13f,u.muted))
        if(e.status==DownloadManager.STATUS_SUCCESSFUL){
            col.addView(u.button("打开文件",true){file(e,false)})
            col.addView(u.button("分享文件"){file(e,true)})
        }else if(e.status==DownloadManager.STATUS_FAILED){col.addView(u.button("重新下载"){a.requestDownload(e.url,p.prefs.getString("download.$id.ua",null)?:WebSettings.getDefaultUserAgent(a),"",e.mime,p.prefs.getString("download.$id.referer","").orEmpty(),suggestedName=e.title,downloadScope=p.prefs.getString("download.$id.account","").orEmpty())})}
        col.addView(u.button("复制来源链接"){a.copy("下载来源",e.url)})
        if(e.status==DownloadManager.STATUS_FAILED||e.status==DownloadManager.STATUS_PAUSED)col.addView(u.item("下载排错帮助","检查网络、仅 Wi-Fi 限制、可用空间和链接是否过期"){p.help()})
        col.addView(u.button("仅移除清岚记录"){AlertDialog.Builder(a).setTitle("移除记录？").setMessage("文件会保留，进行中的系统下载会继续。").setNegativeButton("取消",null).setPositiveButton("移除"){_,_->forget(id);p.pages.back()}.show()})
        col.addView(u.button(if(e.status==DownloadManager.STATUS_SUCCESSFUL)"删除文件及记录"else"取消下载并删除文件"){AlertDialog.Builder(a).setTitle("删除下载？").setMessage("系统下载任务及其文件将被删除，无法撤销。").setNegativeButton("取消",null).setPositiveButton("删除"){_,_->runCatching{manager.remove(id);forget(id);p.pages.back()}.onFailure{a.toast("系统未能删除此下载")}}.show()})
        col.addView(u.button("刷新详情"){p.pages.refresh()})
        val tick=object:Runnable{override fun run(){if(!col.isAttachedToWindow)return;val fresh=entries().firstOrNull{it.id==id};if(fresh!=null){if(fresh.status!=e.status){p.pages.refresh();return};status.text=summary(fresh)};col.postDelayed(this,1500)}};col.postDelayed(tick,1500)
    }}
    private fun forget(id:Long){p.prefs.edit().putStringSet("downloads",p.prefs.getStringSet("downloads",emptySet()).orEmpty()-id.toString()).remove("download.$id.referer").remove("download.$id.ua").remove("download.$id.account").apply()}
    private fun file(e:Entry,share:Boolean){runCatching{
        val uri=manager.getUriForDownloadedFile(e.id)?:error("Missing file")
        a.contentResolver.openFileDescriptor(uri,"r")?.close()?:error("Missing file")
        val intent=if(share)Intent(Intent.ACTION_SEND).setType(e.mime.ifBlank{"*/*"}).putExtra(Intent.EXTRA_STREAM,uri).apply{clipData=android.content.ClipData.newRawUri("下载文件",uri)}else Intent(Intent.ACTION_VIEW).setDataAndType(uri,e.mime.ifBlank{"*/*"})
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        a.startActivity(if(share)Intent.createChooser(intent,"分享文件")else intent)
    }.onFailure{a.toast("文件已移除，或没有可打开它的应用")}}
}
