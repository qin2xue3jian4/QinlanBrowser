@file:Suppress("DEPRECATION")
package dev.qinglan.browser

import android.app.Dialog
import android.os.Build
import android.view.*
import android.widget.*

/** One full-window surface, rebuilt from a route stack. Back always visits its parent. */
class PageHost(private val a:BrowserActivity){
    private data class Route(val title:String,val build:(LinearLayout)->Unit,var scrollY:Int=0)
    private val stack=mutableListOf<Route>()
    private var dialog:Dialog?=null
    private var backRegistered=false
    private var scroll:ScrollView?=null
    private fun remember(){stack.lastOrNull()?.scrollY=scroll?.scrollY?:0}
    fun show(title:String,build:(LinearLayout)->Unit){remember();stack.add(Route(title,build));render()}
    fun back(){if(stack.isNotEmpty())stack.removeAt(stack.lastIndex);if(stack.isEmpty())close()else render()}
    fun close(){stack.clear();scroll=null;dialog?.dismiss();dialog=null;backRegistered=false}
    fun refresh(){if(stack.isNotEmpty()){remember();render()}}
    private fun render(){
        val route=stack.lastOrNull()?:return;val u=a.ui;a.dismissTabs()
        val d=dialog?:object:Dialog(a,if(u.dark)R.style.AppThemeDark else R.style.AppTheme){
            // API 33+ registers OnBackInvokedDispatcher below; this path is Android 8-12.
            @android.annotation.SuppressLint("GestureBackNavigation") override fun onBackPressed(){back()}
        }.also{dialog=it;it.setOnCancelListener{close()}}
        val root=u.column().apply{setBackgroundColor(u.bg)};val bar=u.row().apply{setBackgroundColor(u.panel)}
        bar.addView(u.icon("back",if(stack.size>1)"返回上一级"else"返回网页"){back()});bar.addView(u.title(route.title).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
        root.addView(bar,LinearLayout.LayoutParams(-1,u.dp(56)))
        val col=u.column(16);route.build(col);val pageScroll=ScrollView(a).apply{addView(col)};scroll=pageScroll;root.addView(pageScroll,LinearLayout.LayoutParams(-1,0,1f))
        d.setContentView(root);d.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
        if(!d.isShowing)d.show()
        pageScroll.post{pageScroll.scrollTo(0,route.scrollY)}
        d.window?.apply{
            setLayout(-1,-1);statusBarColor=u.panel;navigationBarColor=u.bg
            if(Build.VERSION.SDK_INT>=30){setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener{v,ins->val b=ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);WindowInsets.CONSUMED};val flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;insetsController?.setSystemBarsAppearance(if(u.dark)0 else flags,flags);root.requestApplyInsets()}else root.fitsSystemWindows=true
            if(Build.VERSION.SDK_INT>=33&&!backRegistered){onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){back()};backRegistered=true}
        }
    }
}
