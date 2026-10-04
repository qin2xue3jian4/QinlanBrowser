package dev.qinglan.browser

import android.view.View
import android.widget.*

class SearchPanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    fun custom()=runCatching{SearchEngines.parse(p.prefs.getString("customSearches","[]")!!)}.getOrDefault(emptyList())
    fun name(url:String)=SearchEngines.name(url,custom())
    private fun save(entries:List<SearchEngines.Custom>){p.prefs.edit().putString("customSearches",SearchEngines.json(entries)).apply()}
    fun show(permanent:Boolean=false){p.pages.show(tr("搜索引擎")){col->
        val active=if(permanent)p.prefs.getString("search",SearchEngines.default)!!else a.current?.searchOverride?:p.prefs.getString("search",SearchEngines.default)!!
        val items=SearchEngines.presets.toList()+custom().map{it.name to it.url}+(if(!SearchEngines.presets.containsValue(active)&&custom().none{it.url==active})listOf(tr("原自定义引擎") to active)else emptyList())
        val spinner=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,items.map{it.first});setSelection(items.indexOfFirst{it.second==active}.coerceAtLeast(0))};col.addView(spinner)
        val scope=RadioGroup(a);val temp=RadioButton(a).apply{id=View.generateViewId();text=tr("仅当前标签页");setTextColor(u.text)};val global=RadioButton(a).apply{id=View.generateViewId();text=tr("设为默认搜索引擎");setTextColor(u.text)};scope.addView(temp);scope.addView(global);scope.check(if(permanent)global.id else temp.id);col.addView(scope)
        col.addView(u.button(tr("应用"),true){val url=items[spinner.selectedItemPosition].second;if(scope.checkedRadioButtonId==global.id){p.prefs.edit().putString("search",url).apply();a.current?.searchOverride=null}else a.current?.searchOverride=url;p.pages.back()})
        if(a.current?.searchOverride!=null)col.addView(u.button(tr("当前标签恢复默认")){a.current?.searchOverride=null;p.pages.back()})
        col.addView(u.item(tr("管理自定义搜索引擎"),tr("命名保存多个搜索引擎")){manage()})
    }}
    fun manage(){p.pages.show(tr("自定义搜索引擎")){col->col.addView(u.button(tr("新增搜索引擎"),true){edit(null)})
        val old=p.prefs.getString("search",SearchEngines.default)!!
        if(!SearchEngines.presets.containsValue(old)&&custom().none{it.url==old})col.addView(u.button(tr("保存原自定义引擎")){edit(SearchEngines.Custom(name=tr("自定义"),url=old))})
        custom().forEach{e->val row=u.row();row.addView(u.item(e.name,e.url){edit(e)},LinearLayout.LayoutParams(0,-2,1f));row.addView(u.icon("close",tr("删除 %1\$s", e.name)){p.confirm(tr("删除搜索引擎"),e.name+tr("；正在使用它的标签将恢复默认搜索。")){save(custom().filterNot{it.id==e.id});if(p.prefs.getString("search",SearchEngines.default)==e.url)p.prefs.edit().putString("search",SearchEngines.default).apply();a.tabs.filter{it.searchOverride==e.url}.forEach{it.searchOverride=null};p.pages.refresh()}});col.addView(row)}
    }}
    private fun edit(old:SearchEngines.Custom?){p.pages.show(if(old==null)tr("新增搜索引擎")else tr("编辑搜索引擎")){col->val name=u.edit(tr("名称"),old?.name.orEmpty());val url=u.edit(tr("网址模板，%s 表示关键词"),old?.url.orEmpty());col.addView(name);col.addView(url);col.addView(u.label(tr("例如 https://example.com/search?q=%s"),12f,u.muted))
        col.addView(u.button(tr("保存"),true){val n=name.text.toString().trim();val link=url.text.toString().trim();val entries=custom().toMutableList();if(n.isBlank()||n.length>40||entries.any{it.name==n&&it.id!=old?.id}){name.error=tr("请输入不重复的名称（最多 40 字）");return@button};if(!SearchEngines.valid(link)){url.error=tr("需要包含 %s 的 HTTP/HTTPS 地址");return@button};val entry=SearchEngines.Custom(old?.id?:java.util.UUID.randomUUID().toString(),n,link);val i=entries.indexOfFirst{it.id==entry.id};if(i<0&&entries.size>=50){a.toast(tr("最多 50 个自定义引擎"));return@button};if(i>=0)entries[i]=entry else entries.add(entry);save(entries)
            if(old!=null){if(p.prefs.getString("search",SearchEngines.default)==old.url)p.prefs.edit().putString("search",link).apply();a.tabs.filter{it.searchOverride==old.url}.forEach{it.searchOverride=link}};p.pages.back()
        })
    }}
}
