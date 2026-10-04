package dev.qinglan.browser

import android.content.Intent
import android.view.*
import android.widget.*

class MenuPanels(private val p:BrowserPanels) {
    private val a get()=p.a
    private val u get()=p.u
    data class Action(val id:String,val icon:String,val title:String,val run:()->Unit)
    private fun actions()=listOf(
        Action("bookmarks","bookmark","书签"){p.library.bookmarks()},
        Action("history","history","历史"){p.library.history()},
        Action("downloads","download","下载"){p.downloads()},
        Action("find","search","页面查找"){a.showFind()},
        Action("collect","bookmark","收藏网页"){if(a.isHttp(a.currentUrl))p.library.collect(a.current?.title.orEmpty(),a.currentUrl)else a.addHomeChoice()},
        Action("share","globe","分享链接"){if(a.isHttp(a.currentUrl))a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,a.currentUrl),"分享链接"))else a.toast("请先打开网页")},
        Action("site","site","网站设置"){p.site()},
        Action("refresh","refresh","刷新"){a.reload()},
        Action("desktop","desktop",if(p.store.siteBool(a.currentUrl,"desktop",p.prefs.getBoolean("desktop",false)))"电脑模式 · 开"else"电脑模式"){if(a.isHttp(a.currentUrl)){p.store.setSiteBool(a.currentUrl,"desktop",!p.store.siteBool(a.currentUrl,"desktop",p.prefs.getBoolean("desktop",false)));a.reload()}else a.toast("请先打开网页")},
        Action("theme","moon",if(u.dark)"日间模式"else"夜间模式"){p.prefs.edit().putString("theme",if(u.dark)"light"else"dark").apply();a.retheme()},
        Action("images","image",if(p.prefs.getBoolean("noImages",false))"无图模式 · 开"else"无图模式"){p.prefs.edit().putBoolean("noImages",!p.prefs.getBoolean("noImages",false)).apply();a.reload()},
        Action("fullscreen","fullscreen","全屏"){a.setFullscreen(true)},
        Action("filter","shield","广告过滤"){p.filter.show()},
        Action("resources","media","网页资源"){p.resources.show()},
        Action("speech","speaker","朗读控制"){p.reading()},
        Action("reader","book","阅读模式"){p.reader.show()},
        Action("print","download","打印 / 保存 PDF"){p.reader.printPage()},
        Action("cookies","cookie","Cookie 管理"){p.cookies()},
        Action("undo","back","撤销关闭标签"){a.undoCloseTab()},
        Action("tabSearch","search","搜索标签"){p.searchTabs()},
        Action("settings","settings","设置"){p.settings()}
    )
    fun show(){if(a.overlayKind=="menu"){a.dismissTabs();return};a.dismissTabs()
        val col=u.column(8);val columns=p.prefs.getInt("menuColumns",3).coerceIn(3,6)
        val grid=GridLayout(a).apply{columnCount=columns}
        val available=actions().associateBy{it.id}
        MenuLayout.parse(p.prefs.getString("menuLayout",null)).mapNotNull{available[it]}.forEach{item->
            val box=u.column(2).apply{gravity=Gravity.CENTER;isFocusable=true;contentDescription=item.title;setOnClickListener{a.dismissTabs();item.run()}}
            box.addView(u.icon(item.icon,item.title){a.dismissTabs();item.run()})
            box.addView(u.label(item.title,11f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER;setPadding(0,0,0,0)})
            grid.addView(box,GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=u.dp(76)})
        }
        repeat((columns-grid.childCount%columns)%columns){grid.addView(View(a),GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f)).apply{width=0;height=1})}
        col.addView(grid)
        val footer=u.row();footer.addView(u.button("更多工具"){a.dismissTabs();p.pages.show("网页工具"){out->actions().filter{it.id!="settings"}.forEach{item->out.addView(u.item(item.title){p.pages.close();item.run()})}}},LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(u.button("定制菜单"){a.dismissTabs();editor()},LinearLayout.LayoutParams(0,-2,1f));col.addView(footer);a.showTabs(col,"menu")
    }
    fun editor(){p.pages.show("菜单定制"){col->
        col.addView(u.label("勾选显示，长按移动柄拖动排序，也可用箭头调整。隐藏的功能仍在更多工具中。",13f,u.muted))
        val active=MenuLayout.parse(p.prefs.getString("menuLayout",null));val map=actions().associateBy{it.id}
        val order=active+MenuLayout.all.filterNot{it in active}
        fun save(ids:List<String>){p.prefs.edit().putString("menuLayout",ids.joinToString(",")).apply();p.pages.refresh()}
        order.forEach{id->val item=map[id]?:return@forEach;val row=u.row()
            row.addView(CheckBox(a).apply{text=item.title;setTextColor(u.text);isChecked=id in active;isEnabled=id!="settings";setOnCheckedChangeListener{_,checked->save(if(checked)active.filter{it!="settings"}+id+"settings" else active-id)}},LinearLayout.LayoutParams(0,u.dp(52),1f))
            if(id in active&&id!="settings"){
                val index=active.indexOf(id)
                row.addView(u.icon("back","上移 ${item.title}"){if(index>0)save(MenuLayout.move(active,id,active[index-1]))})
                row.addView(u.icon("forward","下移 ${item.title}"){if(index<active.size-2){val next=active.toMutableList();next[index]=next[index+1];next[index+1]=id;save(next)}})
                val handle=u.icon("menu","拖动 ${item.title}"){a.toast("长按移动柄后拖动排序")}
                row.addView(handle);DragSupport.source(handle,DragSupport.Item("menu",id))
                DragSupport.target(row,"menu"){drag,_,_->save(MenuLayout.move(active,drag.id,id))}
            };col.addView(row)
        }
        col.addView(u.button("恢复默认菜单"){p.prefs.edit().remove("menuLayout").apply();p.pages.refresh()})
    }}
}
