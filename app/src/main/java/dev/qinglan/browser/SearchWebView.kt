package dev.qinglan.browser
import android.content.Context
import android.graphics.Rect
import android.view.*
import android.webkit.WebView
import org.json.JSONTokener

class SearchWebView(context:Context,private val search:(String)->Unit):WebView(context){
    private fun wrap(original:ActionMode.Callback)=object:ActionMode.Callback2(){
        private fun add(menu:Menu){if(menu.findItem(0x514c)==null)menu.add(0,0x514c,1,"清岚搜索").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)}
        override fun onCreateActionMode(mode:ActionMode,menu:Menu):Boolean {val ok=original.onCreateActionMode(mode,menu);if(ok)add(menu);return ok}
        override fun onPrepareActionMode(mode:ActionMode,menu:Menu):Boolean {val result=original.onPrepareActionMode(mode,menu);add(menu);return result}
        override fun onActionItemClicked(mode:ActionMode,item:MenuItem):Boolean {if(item.itemId!=0x514c)return original.onActionItemClicked(mode,item);evaluateJavascript("window.getSelection().toString().slice(0,4096)"){raw->val s=runCatching{JSONTokener(raw).nextValue()as? String}.getOrNull().orEmpty();mode.finish();if(s.isNotBlank())search(s)};return true}
        override fun onDestroyActionMode(mode:ActionMode){original.onDestroyActionMode(mode)}
        override fun onGetContentRect(mode:ActionMode,view:View,out:Rect){if(original is ActionMode.Callback2)original.onGetContentRect(mode,view,out)else super.onGetContentRect(mode,view,out)}
    }
    override fun startActionMode(callback:ActionMode.Callback,type:Int):ActionMode?=super.startActionMode(wrap(callback),type)
    override fun startActionMode(callback:ActionMode.Callback):ActionMode?=super.startActionMode(wrap(callback))
}
