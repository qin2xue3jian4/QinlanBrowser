package dev.qinglan.browser

import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class SettingsPanels(private val p:BrowserPanels) {
    private val a get()=p.a
    private val u get()=p.u
    private fun value(s:SettingSpec)=p.prefs.all[s.id]?.takeIf(s::valid)?:s.default
    private fun summary(s:SettingSpec):String = when(s.id) {
        "bottomAddress"->if(value(s)==true)"底部" else "顶部"
        "search"->p.searches.name(p.prefs.getString("search",SearchEngines.default)!!)
        "palette"->Palette.names[p.prefs.getString("palette","green")].orEmpty()
        else->value(s)?.let{s.display(it)}?:s.description
    }
    fun show(){p.pages.show("设置"){col->
        val search=u.edit("搜索设置，例如：字号、Cookie、菜单")
        col.addView(search)
        val results=u.column();col.addView(results)
        fun render(query:String){results.removeAllViews()
            if(query.isBlank())SettingCatalog.groups.forEach{group->results.addView(u.item(group,SettingCatalog.entries.filter{it.group==group}.take(3).joinToString(" · "){it.title}){group(group)})}
            else {val found=SettingCatalog.search(query);if(found.isEmpty())results.addView(u.label("没有找到设置，试试其他关键词",14f,u.muted))
                found.forEach{s->results.addView(u.item(s.title,"${s.group} · ${summary(s)}"){open(s.id)})}}
        }
        search.onChange(::render);render("")
    }}
    fun group(name:String){p.pages.show(name){col->SettingCatalog.entries.filter{it.group==name}.forEach{s->col.addView(u.item(s.title,summary(s)){open(s.id)})}}}
    fun open(id:String){val s=SettingCatalog.find(id)?:return
        when(id){
            "palette"->p.appearancePanels.palette();"menuLayout"->p.menuEditor()
            "textZoom"->zoom(null);"siteOverrides"->sites();"search"->p.searchEngine(true)
            "filter"->p.filter.show();"scripts"->p.scripts.show();"speech"->p.reading()
            "reader"->{p.pages.close();p.reader.show()};"print"->{p.pages.close();p.reader.printPage()}
            "backup"->p.vault.backup();"passwords"->p.vault.passwords();"cookies"->p.cookies()
            "clear"->p.clearData();"about"->p.about()
            "defaultBrowser"->runCatching{a.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}.onFailure{a.toast("请到系统设置选择默认浏览器")}
            else->p.pages.show(s.title){col->
                col.addView(u.label(s.description,14f,u.muted))
                if(id=="bottomAddress")col.addView(u.label(if(value(s)==true)"网页内容\n────────────\n地址栏\n后退　主页　标签　菜单" else "地址栏\n────────────\n网页内容\n后退　主页　标签　菜单",16f).apply{gravity=Gravity.CENTER;background=u.round(u.soft)})
                val choices=if(s.default is Boolean)listOf(false,true)else s.values
                choices.forEach{v->val title=if(id=="bottomAddress")if(v==true)"底部"else"顶部" else s.display(v)
                    col.addView(u.item((if(value(s)==v)"✓ "else"")+title){set(s,v);p.pages.refresh()})}
                col.addView(u.button("恢复此项默认"){p.prefs.edit().remove(s.id).apply();apply(s.id);p.pages.refresh()})
            }
        }
    }
    private fun set(s:SettingSpec,v:Any){val e=p.prefs.edit();when(v){is Boolean->e.putBoolean(s.id,v);is Int->e.putInt(s.id,v);is String->e.putString(s.id,v)};e.apply();apply(s.id)}
    private fun apply(id:String){
        if(id in listOf("theme","bottomAddress"))a.retheme()
        if(id=="autoHideAddress")a.showAddress()
        a.tabs.forEach{t->t.web?.let{a.configure(it,t.url)}}
    }
    fun zoom(url:String?){p.pages.show(if(url==null)"网页字号"else"此网站字号"){col->
        val key=if(url==null)"textZoom"else"site.${p.store.siteKey(url)}.textZoom"
        val original=p.prefs.getInt(key,p.prefs.getInt("textZoom",100)).coerceIn(50,200)
        var selected=original
        val label=u.label("$selected%",18f)
        val preview=u.label("清爽阅读，自在浏览。\nThe quick brown fox jumps over the lazy dog.",16f)
        col.addView(label);col.addView(preview)
        col.addView(SeekBar(a).apply{max=30;progress=(original-50)/5;contentDescription="网页字号百分比";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(bar:SeekBar?,v:Int,fromUser:Boolean){selected=50+v*5;label.text="$selected%";preview.textSize=16f*selected/100}
            override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
        })});preview.textSize=16f*selected/100
        col.addView(u.label(if(url==null)"应用于未设置单独字号的网站。"else"仅 ${p.store.siteKey(url)}；默认字号 ${p.prefs.getInt("textZoom",100)}%。",13f,u.muted))
        col.addView(u.button("应用字号",true){p.prefs.edit().putInt(key,selected).apply();apply("textZoom");p.pages.back();p.pages.refresh()})
        col.addView(u.button(if(url==null)"恢复 100%"else"恢复继承默认"){p.prefs.edit().remove(key).apply();apply("textZoom");p.pages.back();p.pages.refresh()})
    }}
    fun sites(){p.pages.show("网站例外"){col->
        val hosts=p.prefs.all.keys.filter{it.startsWith("site.")}.map{it.removePrefix("site.").substringBeforeLast('.')}.distinct().sorted()
        col.addView(u.label("这里只列出单独设置过的网站。恢复默认不删除 Cookie 或登录数据。",13f,u.muted))
        if(hosts.isEmpty())col.addView(u.label("所有网站均使用默认设置"))
        hosts.forEach{host->col.addView(u.item(host,"查看覆盖项或恢复默认"){p.pages.show(host){detail->
            p.prefs.all.filterKeys{it.startsWith("site.$host.")}.forEach{(key,v)->val name=key.substringAfterLast('.');detail.addView(u.label("${siteTitle(name)}：${if(v is Boolean)if(v)"开启"else"关闭" else v}",14f))}
            detail.addView(u.button("恢复此网站默认"){p.store.resetSite("https://$host");apply("site");p.pages.back();p.pages.refresh();a.toast("已恢复默认，刷新网页后完全生效")})
        }})}
    }}
    fun siteTitle(key:String)=when(key){"adblock"->"广告过滤";"dark"->"网页深色";"textZoom"->"网页字号";else->SettingCatalog.find(key)?.title?:key}
}
