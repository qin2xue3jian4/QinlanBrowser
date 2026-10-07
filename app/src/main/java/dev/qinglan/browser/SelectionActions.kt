package dev.qinglan.browser

import android.app.AlertDialog
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import org.json.JSONArray

object SelectionActions {
    interface Decorated
    private const val TRANSLATE=0x514c1001
    private const val FIND=0x514c1002
    fun wrap(a:BrowserActivity,tab:BrowserTab,web:WebView,original:ActionMode.Callback):ActionMode.Callback = if(original is Decorated)original else object:ActionMode.Callback2(),Decorated{
        private fun add(menu:Menu){
            if(menu.findItem(TRANSLATE)==null&&(0 until menu.size()).none{menu.getItem(it).title.toString()==tr("翻译")})menu.add(0,TRANSLATE,100,tr("翻译")).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            if(menu.findItem(FIND)==null)menu.add(0,FIND,101,tr("页面查找")).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        override fun onCreateActionMode(mode:ActionMode,menu:Menu):Boolean {
            val created=original.onCreateActionMode(mode,menu)
            if(created)add(menu)
            return created
        }
        override fun onPrepareActionMode(mode:ActionMode,menu:Menu):Boolean {original.onPrepareActionMode(mode,menu);add(menu);return true}
        override fun onDestroyActionMode(mode:ActionMode)=original.onDestroyActionMode(mode)
        override fun onGetContentRect(mode:ActionMode,view:android.view.View,outRect:android.graphics.Rect){if(original is ActionMode.Callback2)original.onGetContentRect(mode,view,outRect)else super.onGetContentRect(mode,view,outRect)}
        override fun onActionItemClicked(mode:ActionMode,item:MenuItem):Boolean {
            if(item.itemId !in listOf(TRANSLATE,FIND))return original.onActionItemClicked(mode,item)
            web.evaluateJavascript("window.getSelection().toString().slice(0,80000)"){raw->
                if(a.current!==tab||tab.web!==web)return@evaluateJavascript
                val text=runCatching{JSONArray("[$raw]").getString(0)}.getOrDefault("")
                mode.finish()
                if(text.isBlank())return@evaluateJavascript
                if(item.itemId==FIND)a.showFind(text)else translate(a,text)
            };return true
        }
    }
    @Suppress("DEPRECATION")
    private fun translate(a:BrowserActivity,text:String){
        // Android's PROCESS_TEXT providers include translation apps; launch the explicit provider.
        val intent=Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT,text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true)
        val candidates=a.packageManager.queryIntentActivities(intent,0).filter{it.activityInfo.packageName!=a.packageName}
        val translations=candidates.filter{val label=it.loadLabel(a.packageManager).toString();label.contains("translat",true)||label.contains("翻译")||label.contains("翻譯")||it.activityInfo.packageName.contains("translat",true)}
        val providers=translations.ifEmpty{candidates}
        if(providers.isEmpty()){a.toast(tr("未找到系统翻译服务，请安装支持文本处理的翻译应用"));return}
        AlertDialog.Builder(a).setTitle(tr("翻译")).setItems(providers.map{it.loadLabel(a.packageManager).toString()}.toTypedArray()){_,i->
            val info=providers[i].activityInfo
            runCatching{a.startActivity(Intent(intent).setClassName(info.packageName,info.name))}.onFailure{a.toast(tr("无法打开翻译应用"))}
        }.show()
    }
}
