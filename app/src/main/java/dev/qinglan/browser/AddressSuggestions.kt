package dev.qinglan.browser

import android.view.View
import android.widget.*

class AddressSuggestions(private val a:BrowserActivity){
    private var popup:PopupWindow?=null
    fun dismiss(){popup?.dismiss();popup=null}
    fun update(query:String){
        if(!a.address.hasFocus()||!a.prefs.getBoolean("localSuggestions",true)){dismiss();return}
        val found=AddressInput.suggestions(query,a.store.bookmarks,if(a.prefs.getBoolean("recordHistory",true))a.store.history else emptyList())
        if(found.isEmpty()){dismiss();return}
        val col=a.ui.column(4)
        found.forEach{item->col.addView(a.ui.item(item.title.take(80),"${item.source} · ${item.url.take(100)}"){dismiss();a.open(item.url)})}
        val scroll=ScrollView(a).apply{addView(col);setBackgroundColor(a.ui.panel)}
        val existing=popup
        if(existing!=null){(existing.contentView as ScrollView).apply{removeAllViews();scroll.removeView(col);addView(col)};return}
        if(!a.address.isAttachedToWindow||a.address.width==0)return
        popup=PopupWindow(scroll,a.address.width,a.ui.dp(190),false).apply{
            elevation=a.ui.dp(6).toFloat();isOutsideTouchable=true;setBackgroundDrawable(a.ui.round(a.ui.panel));inputMethodMode=PopupWindow.INPUT_METHOD_NEEDED
            setOnDismissListener{if(popup===this)popup=null};showAsDropDown(a.address)
        }
    }
}
