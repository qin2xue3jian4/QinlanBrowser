package dev.qinglan.browser

import android.content.Intent
import android.view.*
import android.widget.*

class MenuPanels(private val p:BrowserPanels) {
    private val a get()=p.a
    private val u get()=p.u
    data class Action(val id:String,val icon:String,val title:String,val run:()->Unit)
    private fun actions()=listOf(
        Action("bookmarks","bookmark",tr("书签")){p.library.bookmarks()},
        Action("history","history",tr("历史")){p.library.history()},
        Action("downloads","download",tr("下载")){p.downloads()},
        Action("find","search",tr("页面查找")){a.showFind()},
        Action("collect","bookmarkAdd",tr("收藏网页")){if(a.isHttp(a.currentUrl))p.library.collect(a.current?.title.orEmpty(),a.currentUrl)else a.addHomeChoice()},
        Action("share","share",tr("分享链接")){if(a.isHttp(a.currentUrl))a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,a.currentUrl),tr("分享链接")))else a.toast(tr("请先打开网页"))},
        Action("site","site",tr("网站设置")){p.site()},
        Action("refresh","refresh",tr("刷新")){a.reload()},
        Action("desktop","desktop",if(p.store.siteBool(a.currentUrl,"desktop",p.prefs.getBoolean("desktop",false)))tr("电脑模式 · 开")else tr("电脑模式")){if(a.isHttp(a.currentUrl)){p.store.setSiteBool(a.currentUrl,"desktop",!p.store.siteBool(a.currentUrl,"desktop",p.prefs.getBoolean("desktop",false)));a.reload()}else a.toast(tr("请先打开网页"))},
        Action("theme","moon",if(u.dark)tr("日间模式")else tr("夜间模式")){p.prefs.edit().putString("theme",if(u.dark)"light"else"dark").apply();a.retheme()},
        Action("images","image",if(p.prefs.getBoolean("noImages",false))tr("无图模式 · 开")else tr("无图模式")){p.prefs.edit().putBoolean("noImages",!p.prefs.getBoolean("noImages",false)).apply();a.reload()},
        Action("fullscreen","fullscreen",tr("全屏")){a.setFullscreen(true)},
        Action("filter","shield",tr("广告过滤")){p.filter.show()},
        Action("resources","media",tr("网页资源")){p.resources.show()},
        Action("speech","speaker",tr("朗读控制")){p.reading()},
        Action("reader","book",tr("阅读模式")){p.reader.show()},
        Action("readingList","offline",tr("离线文章")){p.reader.saved()},
        Action("qr","qr",tr("扫描二维码")){a.scanQr()},
        Action("pageTop","pageTop",tr("回到顶部")){a.current?.web?.pageUp(true)},
        Action("pageBottom","pageBottom",tr("跳到页底")){a.current?.web?.pageDown(true)},
        Action("print","printer",tr("打印 / 保存 PDF")){p.reader.printPage()},
        Action("cookies","cookie",tr("Cookie 管理")){p.cookies()},
        Action("undo","undo",tr("撤销关闭标签")){a.undoCloseTab()},
        Action("tabSearch","search",tr("搜索标签")){p.searchTabs()},
        Action("closeOtherTabs","close",tr("关闭其他标签")){p.closeOtherTabs()},
        Action("accounts","account",tr("网站账号")){p.accounts.current()},
        Action("incognito","incognito",if(a.isIncognito)tr("退出无痕")else tr("无痕模式")){a.privateMode()},
        Action("tools","tools",tr("更多工具")){allTools()},
        Action("menuEditor","customize",tr("菜单定制")){editor()},
        Action("settings","settings",tr("设置")){p.settings()}
    )
    fun show(){if(a.overlayKind=="menu"){a.dismissTabs();return};a.dismissTabs()
        val col=u.column(8);val columns=p.prefs.getInt("menuColumns",3).coerceIn(3,6)
        val grid=GridLayout(a).apply{columnCount=columns}
        val available=actions().associateBy{it.id}
        val ids=if(a.isIncognito)listOf("bookmarks","downloads","find","refresh","reader","resources","collect","share","incognito")else MenuLayout.parse(p.prefs.getString("menuLayout",null))
        ids.mapNotNull{available[it]}.forEach{item->
            val box=u.column(2).apply{gravity=Gravity.CENTER;isFocusable=true;contentDescription=item.title;setOnClickListener{a.dismissTabs();item.run()}}
            box.addView(u.icon(item.icon,item.title){a.dismissTabs();item.run()})
            box.addView(u.label(item.title,11f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER;setPadding(0,0,0,0)})
            grid.addView(box,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=u.dp(88)})
        }
        repeat((columns-grid.childCount%columns)%columns){grid.addView(View(a),GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=1})}
        col.addView(grid)
        a.showTabs(col,"menu")
    }
    fun allTools(){var query="";p.pages.show(tr("全部工具")){col->
        val input=u.edit(tr("搜索工具"),query);col.addView(input);val rows=u.column();col.addView(rows)
        val groups=linkedMapOf(tr("阅读与页面") to listOf("reader","readingList","speech","find","print","qr","refresh","share","pageTop","pageBottom"),tr("收藏与标签") to listOf("collect","bookmarkAdds","history","downloads","tabSearch","undo","closeOtherTabs"),tr("网站与外观") to listOf("accounts","incognito","site","desktop","theme","images","fullscreen","filter","resources","cookies"))
        fun render(){rows.removeAllViews();val map=actions().associateBy{it.id};var count=0
            groups.forEach{(title,ids)->val items=ids.mapNotNull{map[it]}.filter{it.title.contains(query,true)};if(items.isNotEmpty()){rows.addView(u.label(title,13f,u.accent));items.forEach{item->count++;rows.addView(u.item(item.title){p.pages.close();item.run()})}}}
            if(count==0)rows.addView(u.label(tr("没有匹配的工具")))
        };input.onChange{query=it;render()};render()
    }}
    fun editor(){p.pages.show(tr("菜单定制")){col->
        col.addView(u.label(tr("勾选显示，长按移动柄拖动排序，也可用箭头调整。隐藏的功能仍在更多工具中。"),13f,u.muted))
        val active=MenuLayout.parse(p.prefs.getString("menuLayout",null));val map=actions().associateBy{it.id}
        val order=active+MenuLayout.all.filterNot{it in active}
        fun save(ids:List<String>){p.prefs.edit().putString("menuLayout",ids.joinToString(",")).apply();p.pages.refresh()}
        order.forEach{id->val item=map[id]?:return@forEach;val row=u.row()
            row.addView(CheckBox(a).apply{text=item.title;setTextColor(u.text);isChecked=id in active;isEnabled=id!="settings";setOnCheckedChangeListener{_,checked->save(if(checked)active.filter{it!="settings"}+id+"settings" else active-id)}},LinearLayout.LayoutParams(0,u.dp(52),1f))
            if(id in active&&id!="settings"){
                val index=active.indexOf(id)
                row.addView(u.icon("up",tr("上移 %1\$s", item.title)){if(index>0)save(MenuLayout.move(active,id,active[index-1]))})
                row.addView(u.icon("down",tr("下移 %1\$s", item.title)){if(index<active.size-2){val next=active.toMutableList();next[index]=next[index+1];next[index+1]=id;save(next)}})
                val handle=u.icon("grip",tr("拖动 %1\$s", item.title)){a.toast(tr("长按移动柄后拖动排序"))}
                row.addView(handle);DragSupport.source(handle,DragSupport.Item("menu",id))
                DragSupport.target(row,"menu"){drag,_,_->save(MenuLayout.move(active,drag.id,id))}
            };col.addView(row)
        }
        col.addView(u.button(tr("恢复默认菜单")){p.prefs.edit().remove("menuLayout").apply();p.pages.refresh()})
    }}
}
