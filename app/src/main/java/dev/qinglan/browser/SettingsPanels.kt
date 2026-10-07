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
        "language"->AppLanguage.names()[LanguageChoice.supported.indexOf(AppLanguage.choice(a))]
        "bottomAddress"->if(value(s)==true)tr("底部") else tr("顶部")
        "search"->p.searches.name(p.prefs.getString("search",SearchEngines.default)!!)
        "palette"->Palette.names[p.prefs.getString("palette","green")].orEmpty()
        else->value(s)?.let{s.display(it)}?:s.description
    }
    fun show(){var query="";p.pages.show(tr("设置")){col->
        val search=u.edit(tr("搜索设置，例如：字号、Cookie、菜单"),query)
        col.addView(search)
        val results=u.column();col.addView(results)
        fun render(query:String){results.removeAllViews()
            if(query.isBlank()){
                results.addView(u.item(tr("已修改设置"),tr("查看与默认值不同的设置，逐项恢复")){modified()})
                SettingCatalog.groups.forEach{group->results.addView(u.item(group,SettingCatalog.entries.filter{it.group==group}.take(3).joinToString(" · "){it.title}){group(group)})}}
            else {val found=SettingCatalog.search(query);if(found.isEmpty())results.addView(u.label(tr("没有找到设置，试试其他关键词"),14f,u.muted))
                found.forEach{s->results.addView(u.item(s.title,"${s.group} · ${summary(s)}"){open(s.id)})}}
        }
        search.onChange{query=it;render(it)};render(query)
    }}
    fun group(name:String){p.pages.show(name){col->SettingCatalog.sections[name].orEmpty().forEach{section->
        if(section.title.isNotEmpty())col.addView(u.label(section.title,13f,u.accent))
        section.ids.mapNotNull(SettingCatalog::find).forEach{s->col.addView(u.item(s.title,if(s.default!=null)"${summary(s)} · ${s.description}"else s.description){open(s.id)})}
    }}}
    private fun modified(){p.pages.show(tr("已修改设置")){col->
        val changed=SettingCatalog.entries.filter{if(it.id=="language")AppLanguage.choice(a)!="system" else it.default!=null&&value(it)!=it.default}
        if(changed.isEmpty())col.addView(u.label(tr("全局设置均为默认值")))
        changed.forEach{s->col.addView(u.item(s.title,tr("当前：%1\$s · 默认：%2\$s", summary(s), if(s.id=="language")AppLanguage.names()[0]else s.display(s.default!!))){open(s.id)})}
        col.addView(u.item(tr("网站例外"),tr("单独查看各网站覆盖项")){sites()});col.addView(u.item(tr("菜单定制"),tr("菜单排序与显隐可在此恢复默认")){p.menuEditor()})
    }}
    fun open(id:String){val s=SettingCatalog.find(id)?:return
        when(id){
            "certificateTrust"->p.pages.show(s.title){col->
                val keys=p.prefs.all.keys.filter{it.startsWith("certificate.")}.sorted()
                if(keys.isEmpty())col.addView(u.label(tr("没有已信任的证书网址")))
                keys.forEach{key->col.addView(u.item(key.removePrefix("certificate."),tr("点击删除此例外")){p.prefs.edit().remove(key).apply();a.security.clear();p.pages.refresh()})}
            }
            "language"->p.pages.show(s.title){col->
                col.addView(u.label(s.description,14f,u.muted))
                val selected=AppLanguage.choice(a)
                LanguageChoice.supported.zip(AppLanguage.names()).forEach{(tag,name)->
                    col.addView(RadioButton(a).apply{text=name;setTextColor(u.text);isChecked=tag==selected;minHeight=u.dp(52);setOnClickListener{AppLanguage.set(a,tag)}})
                }
            }
            "palette"->p.appearancePanels.palette();"menuLayout"->p.menuEditor()
            "textZoom"->zoom(null);"siteOverrides"->sites();"search"->p.searchEngine(true)
            "filter"->p.filter.show();"scripts"->p.scripts.show();"speech"->p.reading()
            "reader"->{p.pages.close();p.reader.show()};"print"->{p.pages.close();p.reader.printPage()}
            "readingList"->p.reader.saved()
            "accounts"->p.accounts.all()
            "incognito"->a.privateMode()
            "tabSearch"->p.searchTabs();"undo"->{p.pages.close();a.undoCloseTab()};"closeOtherTabs"->p.closeOtherTabs();"tools"->p.menuPanels.allTools()
            "backup"->p.vault.backup();"passwords"->p.vault.passwords();"cookies"->p.cookies()
            "webdav"->p.webdav.show()
            "clear"->p.clearData();"about"->p.about();"help"->p.help()
            "defaultBrowser"->runCatching{
                if(android.os.Build.VERSION.SDK_INT>=29){val roles=a.getSystemService(android.app.role.RoleManager::class.java);if(roles.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER))a.toast(tr("清岚已经是默认浏览器"))else if(roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER))a.startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),106)else a.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}
                else a.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }.onFailure{a.toast(tr("请到系统设置选择默认浏览器"))}
            else->p.pages.show(s.title){col->
                col.addView(u.label(s.description,14f,u.muted))
                col.addView(u.label(tr("默认：%1\$s", if(id=="bottomAddress")tr("顶部")else s.display(s.default!!)),12f,u.muted))
                if(id=="bottomAddress")col.addView(u.label(if(value(s)==true)tr("网页内容\n────────────\n地址栏\n后退　主页　标签　菜单") else tr("地址栏\n────────────\n网页内容\n后退　主页　标签　菜单"),16f).apply{gravity=Gravity.CENTER;background=u.round(u.soft)})
                val choices=if(s.default is Boolean)listOf(false,true)else s.values
                choices.forEach{v->val title=if(id=="bottomAddress")if(v==true)tr("底部")else tr("顶部") else s.display(v)
                    col.addView(RadioButton(a).apply{text=title;setTextColor(u.text);isChecked=value(s)==v;minHeight=u.dp(52);setOnClickListener{set(s,v);p.pages.refresh()}})}
                col.addView(u.button(tr("恢复此项默认")){p.prefs.edit().remove(s.id).apply();apply(s.id);p.pages.refresh()})
            }
        }
    }
    private fun set(s:SettingSpec,v:Any){val e=p.prefs.edit();when(v){is Boolean->e.putBoolean(s.id,v);is Int->e.putInt(s.id,v);is String->e.putString(s.id,v)};e.apply();apply(s.id)}
    fun restoreSynced(snapshot:WebDavSettings.Snapshot) {
        // Revalidate at the write boundary before removing anything.
        val checked=WebDavSettings.parse(WebDavSettings.export(snapshot.settings,snapshot.language?:AppLanguage.choice(a)))
        val old=p.prefs.all.filterKeys(BackupCodec::allowed)
        fun replace(values:Map<String,*>)=p.prefs.edit().apply {
            p.prefs.all.keys.filter(BackupCodec::allowed).forEach(::remove)
            values.forEach{(key,value)->when(value){is Boolean->putBoolean(key,value);is Int->putInt(key,value);is String->putString(key,value)}}
        }
        if(!replace(checked.settings).commit()){
            replace(old).commit()
            error(tr("保存同步设置失败，已恢复原设置"))
        }
        a.security.clear()
        a.webPermissions.cancel()
        if(!p.prefs.getBoolean("blobDownloads",false))a.tabs.forEach{it.web?.let(a.blobs::finished)}
        a.filtering.subscriptions.rebuild()
        apply("httpsOnly")
        a.retheme();a.trimTabs();a.showAddress();a.updateScrollButtons()
        snapshot.language?.takeIf{it!=AppLanguage.choice(a)}?.let{p.pages.close();AppLanguage.set(a,it)}
    }
    private fun apply(id:String){
        if(id in listOf("theme","bottomAddress","toolbarAction","homeColumns","homeTitle"))a.retheme()
        if(id=="autoHideAddress")a.showAddress()
        if(id=="activeWebViews")a.trimTabs()
        if(id=="edgeScroll")a.updateScrollButtons()
        if(id=="certificateExceptions")a.security.clear()
        if(id=="blobDownloads")a.tabs.forEach{it.web?.let(a.blobs::finished)}
        if(id=="httpsOnly"&&p.prefs.getBoolean(id,false)){a.security.clear();a.tabs.filter{it.url.startsWith("http://")}.forEach{t->t.url=NavigationPolicy.secure(t.url,true);t.saved=null;t.web?.stopLoading();t.web?.loadUrl(t.url)}}
        a.tabs.forEach{t->t.web?.let{a.configure(it,t.url)}}
    }
    fun zoom(url:String?){p.pages.show(if(url==null)tr("网页字号")else tr("此网站字号")){col->
        val key=if(url==null)"textZoom"else"site.${p.store.siteKey(url)}.textZoom"
        val original=p.prefs.getInt(key,p.prefs.getInt("textZoom",100)).coerceIn(50,200)
        var selected=original
        val label=u.label("$selected%",18f)
        val preview=u.label(tr("清爽阅读，自在浏览。\nThe quick brown fox jumps over the lazy dog."),16f)
        col.addView(label);col.addView(preview)
        col.addView(SeekBar(a).apply{max=30;progress=(original-50)/5;contentDescription=tr("网页字号百分比");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(bar:SeekBar?,v:Int,fromUser:Boolean){selected=50+v*5;label.text="$selected%";preview.textSize=16f*selected/100}
            override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
        })});preview.textSize=16f*selected/100
        col.addView(u.label(if(url==null)tr("应用于未设置单独字号的网站。")else tr("仅 %1\$s；默认字号 %2\$s%。", p.store.siteKey(url), p.prefs.getInt("textZoom",100)),13f,u.muted))
        col.addView(u.button(tr("应用字号"),true){p.prefs.edit().putInt(key,selected).apply();apply("textZoom");p.pages.back();p.pages.refresh()})
        col.addView(u.button(if(url==null)tr("恢复 100%")else tr("恢复继承默认")){p.prefs.edit().remove(key).apply();apply("textZoom");p.pages.back();p.pages.refresh()})
    }}
    fun sites(){p.pages.show(tr("网站例外")){col->
        val hosts=p.prefs.all.keys.filter{it.startsWith("site.")}.map{it.removePrefix("site.").substringBeforeLast('.')}.distinct().sorted()
        col.addView(u.label(tr("这里只列出单独设置过的网站。恢复默认不删除 Cookie 或登录数据。"),13f,u.muted))
        if(hosts.isEmpty())col.addView(u.label(tr("所有网站均使用默认设置")))
        hosts.forEach{host->col.addView(u.item(host,tr("查看覆盖项或恢复默认")){p.pages.show(host){detail->
            val order=listOf("adblock","js","desktop","noImages","dark","textZoom","autoplay","thirdParty","camera","microphone","location")
            p.prefs.all.filterKeys{it.startsWith("site.$host.")}.toList().sortedBy{(key,_)->order.indexOf(key.substringAfterLast('.')).takeIf{it>=0}?:Int.MAX_VALUE}.forEach{(key,v)->val name=key.substringAfterLast('.');detail.addView(u.label("${siteTitle(name)}：${if(v is Boolean)if(v)tr("开启")else tr("关闭") else v}",14f))}
            detail.addView(u.button(tr("恢复此网站默认")){p.store.resetSite("https://$host");apply("site");p.pages.back();p.pages.refresh();a.toast(tr("已恢复默认，刷新网页后完全生效"))})
        }})}
    }}
    fun siteTitle(key:String)=when(key){"adblock"->tr("广告过滤");"dark"->tr("网页深色");"textZoom"->tr("网页字号");"camera"->tr("允许询问摄像头");"microphone"->tr("允许询问麦克风");"location"->tr("允许询问位置");else->SettingCatalog.find(key)?.title?:key}
}
