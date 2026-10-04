package dev.qinglan.browser
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.text.DateFormat
import java.util.Date

class LibraryPanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    private val store get()=p.store
    private val pages get()=p.pages
    private fun row(v:Visit,subtitle:String,run:()->Unit):LinearLayout {val r=u.row().apply{setPadding(u.dp(4),0,u.dp(4),0);setOnClickListener{run()};isFocusable=true}
        r.addView(if(v.folder)IconView(a,"folder",u.accent).apply{layoutParams=LinearLayout.LayoutParams(u.dp(32),u.dp(32))}else a.icons.view(u,v.title,v.url));val col=u.column();col.gravity=Gravity.CENTER_VERTICAL
        col.addView(u.label(v.title,14f).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(u.dp(8),0,0,0)})
        col.addView(u.label(subtitle,11f,u.muted).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END;setPadding(u.dp(8),u.dp(3),0,0)})
        r.addView(col,LinearLayout.LayoutParams(0,u.dp(76),1f));r.layoutParams=LinearLayout.LayoutParams(-1,u.dp(76));return r
    }
    fun collect(title:String,url:String,bookmark:Boolean=true,home:Boolean=false){if(!a.isHttp(url)){a.toast(tr("请先打开网页"));return};pages.show(tr("收藏网页")){col->val name=u.edit(tr("名称"),title.ifBlank{url});col.addView(name)
        if(a.isIncognito)col.addView(u.label(tr("主动保存的书签和主页项目会保留，退出无痕不会删除。"),13f,u.muted))
        val b=CheckBox(a).apply{text=tr("保存到书签");isChecked=bookmark;setTextColor(u.text)};col.addView(b);val folders=store.bookmarks.filter{it.folder};val bp=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf(tr("书签根目录"))+folders.map{it.title})};col.addView(bp)
        val h=CheckBox(a).apply{text=tr("保存到主页");isChecked=home;setTextColor(u.text)};col.addView(h);val homes=store.home.filter{it.folder};val hp=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf(tr("主页根目录"))+homes.map{it.title})};col.addView(hp)
        col.addView(u.button(tr("保存"),true){if(!b.isChecked&&!h.isChecked){a.toast(tr("请至少选择一个位置"));return@button};val label=name.text.toString().trim().ifBlank{url}.take(1000)
            if(b.isChecked){val old=store.bookmarks.find{!it.folder&&it.url==url};store.bookmarks.removeAll{!it.folder&&it.url==url};store.bookmarks.add(0,Visit(label,url,id=old?.id?:java.util.UUID.randomUUID().toString(),parent=folders.getOrNull(bp.selectedItemPosition-1)?.id.orEmpty()))}
            if(h.isChecked){val old=store.home.find{!it.folder&&it.url==url};if(old!=null){old.title=label;old.parent=homes.getOrNull(hp.selectedItemPosition-1)?.id.orEmpty()}else store.home.add(HomeItem(title=label,url=url,parent=homes.getOrNull(hp.selectedItemPosition-1)?.id.orEmpty()))}
            store.save();pages.back();a.toast(tr("已保存到所选位置"));if(a.currentUrl=="about:home")a.renderHome()
        })
    }}
    fun bookmarks(parent:String=""){
        var query="";var editing=false;val selected=mutableSetOf<String>()
        pages.show(if(parent.isEmpty())tr("书签")else store.bookmarks.find{it.id==parent}?.title?:tr("文件夹")){col->
            selected.retainAll(store.bookmarks.map{it.id}.toSet())
            val search=u.edit(tr("搜索名称或网址"),query);col.addView(search)
            val controls=u.row();col.addView(controls);val status=u.label("",12f,u.muted);col.addView(status)
            val actions=u.row();col.addView(actions);val list=u.column();col.addView(list)
            fun data()=store.bookmarks.filter{(if(query.isBlank())it.parent==parent else true)&&(it.title.contains(query,true)||it.url.contains(query,true))}
            lateinit var render:()->Unit
            fun select(id:String){if(!selected.add(id))selected.remove(id);render()}
            render={
                list.removeAllViews();actions.visibility=if(editing)View.VISIBLE else View.GONE
                status.text=if(editing)tr("已选 %1\$s 项 · %2\$s", selected.size, if(query.isBlank())tr("按住右侧 ≡ 拖动排序")else tr("清空搜索后可拖动排序"))else tr("长按项目可编辑，或进入编辑模式批量整理")
                val data=data();if(data.isEmpty())list.addView(u.label(tr("暂无书签"),14f,u.muted))
                data.take(300).forEach{v->
                    val item=row(v,if(v.folder)tr("%1\$s 个网站", store.bookmarks.count{it.parent==v.id})else v.url){if(editing)select(v.id)else if(v.folder)bookmarks(v.id)else{pages.close();a.openCollection(v.url)}}
                    if(editing){
                        item.addView(CheckBox(a).apply{isChecked=v.id in selected;contentDescription=tr("选择 %1\$s", v.title);setOnCheckedChangeListener{_,b->if(b)selected.add(v.id)else selected.remove(v.id);status.text=tr("已选 %1\$s 项 · 按住右侧 ≡ 拖动排序", selected.size)}},0)
                        val handle=u.icon("grip",tr("移动 %1\$s", v.title)){a.toast(tr("按住拖动柄上下移动"))};item.addView(handle)
                        if(query.isBlank()){
                            DragSupport.source(handle,DragSupport.Item("bookmark",v.id),false)
                            DragSupport.target(item,"bookmark",{it.id!=v.id}){drag,_,y->LibraryOrder.bookmarks(store.bookmarks,setOf(drag.id),parent,v.id,y>=item.height/2f);store.save();render()}
                        }else handle.isEnabled=false
                    }
                    item.setOnLongClickListener{bookmarkActions(v,item);true};list.addView(item);list.addView(u.rule())
                };if(data.size>300)list.addView(u.label(tr("请搜索缩小范围"),12f,u.muted))
            }
            val mode=u.button(if(editing)tr("完成")else tr("编辑模式")){};mode.setOnClickListener{editing=!editing;if(!editing)selected.clear();mode.text=if(editing)tr("完成")else tr("编辑模式");render()};controls.addView(mode,LinearLayout.LayoutParams(0,-2,1f))
            if(parent.isEmpty())controls.addView(u.button(tr("新建文件夹")){editBookmark(null,true)},LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(u.button(tr("全选")){val ids=data().map{it.id}.toSet();if(selected.containsAll(ids))selected.removeAll(ids)else selected.addAll(ids);render()},LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(u.button(tr("移动")){if(selected.isEmpty()){a.toast(tr("请先选择书签"));return@button};val folders=store.bookmarks.filter{it.folder&&it.id !in selected};val ids=selected.toSet();val destinations=if(store.bookmarks.any{it.folder&&it.id in ids})emptyList()else folders
                p.choose(tr("移动所选 %1\$s 项", ids.size),listOf(tr("书签根目录"))+destinations.map{it.title}){i->LibraryOrder.bookmarks(store.bookmarks,ids,destinations.getOrNull(i-1)?.id.orEmpty());store.save();selected.clear();pages.refresh()}
            },LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(u.button(tr("删除")){if(selected.isEmpty()){a.toast(tr("请先选择书签"));return@button};val ids=selected.toSet();p.confirm(tr("删除 %1\$s 项", ids.size),tr("文件夹内未选中的网站会移回根目录。")){LibraryOrder.deleteBookmarks(store.bookmarks,ids);store.save();selected.clear();pages.refresh()}},LinearLayout.LayoutParams(0,-2,1f))
            watch(search){query=it;selected.clear();render()};render()
            col.addView(u.button(tr("导入 HTML")){a.readDocument{html->val imported=BookmarkHtml.parse(html);p.confirm(tr("导入书签"),tr("导入 %1\$s 个网站及 %2\$s 个文件夹；重复网址跳过。", imported.count{!it.folder}, imported.count{it.folder})){val existing=store.bookmarks.filterNot{it.folder}.map{it.url}.toMutableSet();store.bookmarks.addAll(imported.filter{it.folder||existing.add(it.url)});store.save();pages.refresh()}}})
            col.addView(u.button(tr("导出 HTML")){a.writeDocument("qinglan-bookmarks.html","text/html",store.bookmarkHtml())})
        }
    }
    private fun bookmarkActions(v:Visit,anchor:View){PopupMenu(a,anchor).apply{
        menu.add(tr("编辑")).setOnMenuItemClickListener{editBookmark(v,v.folder);true}
        if(!v.folder)menu.add(tr("收藏到主页")).setOnMenuItemClickListener{collect(v.title,v.url,false,true);true}
        menu.add(tr("删除")).setOnMenuItemClickListener{p.confirm(tr("删除书签"),if(v.folder)tr("文件夹内的网站移回根目录。")else tr("删除 %1\$s？", v.title)){LibraryOrder.deleteBookmarks(store.bookmarks,setOf(v.id));store.save();pages.refresh()};true};show()
    }}
    private fun editBookmark(v:Visit?,folder:Boolean){ItemEditor.show(a,if(folder)tr("编辑文件夹")else tr("编辑书签"),v?.title.orEmpty(),if(folder)null else v?.url.orEmpty()){name,url->
        val entry=v?.copy(title=name,url=if(folder)""else url)?:Visit(name,"",folder=true);val index=store.bookmarks.indexOfFirst{it.id==entry.id};if(index>=0)store.bookmarks[index]=entry else store.bookmarks.add(entry);store.save();pages.refresh()
    }}
    fun history(){var query="";var range=0;var batch=false;val selected=mutableSetOf<String>();pages.show(tr("历史")){col->val search=u.edit(tr("搜索名称或网址"),query);col.addView(search);val filter=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,HistoryRange.names);setSelection(range)};col.addView(filter)
        val status=u.label("",12f,u.muted);col.addView(status);val actions=u.row();col.addView(actions);val list=u.column();col.addView(list)
        fun data()=store.history.filter{it.time>=HistoryRange.start(range)&& (it.title.contains(query,true)||it.url.contains(query,true))}
        fun render(){list.removeAllViews();val all=data();status.text=tr("%1\$s 条 · 已选 %2\$s 条", all.size, selected.size);all.take(250).forEach{v->val r=row(v,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(v.time))+" · "+v.url){if(batch){if(!selected.add(v.id))selected.remove(v.id);render()}else{pages.close();a.open(v.url)}}
            if(batch)r.addView(CheckBox(a).apply{isChecked=v.id in selected;contentDescription=tr("选择记录");setOnCheckedChangeListener{_,b->if(b)selected.add(v.id)else selected.remove(v.id);status.text=tr("%1\$s 条 · 已选 %2\$s 条", all.size, selected.size)}})
            r.setOnLongClickListener{batch=true;selected.add(v.id);render();true};list.addView(r);list.addView(u.rule())};if(all.isEmpty())list.addView(u.label(tr("此时段暂无记录")));if(all.size>250)list.addView(u.label(tr("显示前 250 条，可搜索缩小范围；时段批量删除不受显示数量限制。"),12f,u.muted))}
        actions.addView(u.button(tr("选择 / 取消")){batch=!batch;if(!batch)selected.clear();render()},LinearLayout.LayoutParams(0,-2,1f));actions.addView(u.button(tr("删除所选")){if(selected.isEmpty()){a.toast(tr("请先选择记录"));return@button};p.confirm(tr("删除所选记录"),tr("删除 %1\$s 条历史记录？", selected.size)){store.history.removeAll{it.id in selected};selected.clear();store.save();pages.refresh()}},LinearLayout.LayoutParams(0,-2,1f))
        col.addView(u.button(tr("按时段批量删除")){p.choose(tr("删除哪个时段"),HistoryRange.names.toList()){i->val start=HistoryRange.start(i);val ids=store.history.filter{it.time>=start}.map{it.id}.toSet();p.confirm(tr("确认删除 %1\$s 条", ids.size),tr("%1\$s的全部记录，不受搜索词影响。", HistoryRange.names[i])){store.history.removeAll{it.id in ids};selected.removeAll(ids);store.save();pages.refresh()}}})
        filter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long){range=i;selected.clear();render()};override fun onNothingSelected(p:AdapterView<*>?) {}};watch(search){query=it;render()};render()
    }}
    private fun watch(edit:EditText,run:(String)->Unit){edit.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){run(s.toString())};override fun afterTextChanged(s:android.text.Editable?) {}})}
}
