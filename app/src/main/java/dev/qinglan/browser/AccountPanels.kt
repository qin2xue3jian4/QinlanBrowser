package dev.qinglan.browser

/** Site is an organizer, not an automatically guessed list of login domains. */
class AccountPanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    fun current(){
        if(a.isIncognito){a.toast(tr("请退出无痕后管理已保存的账号"));return}
        val account=a.accounts.find(a.current?.accountId.orEmpty())
        val url=account?.startUrl?:a.currentUrl
        if(!a.isHttp(url)){all();return}
        val site=account?.site?:a.store.siteKey(url)
        p.pages.show(tr("切换网站账号")){col->
            col.addView(u.label(site,18f));col.addView(u.label(tr("当前：%1\$s", a.accounts.label(a.current?.accountId.orEmpty(),site)),14f,u.accent))
            col.addView(u.label(tr("只切换当前标签并重新加载，未提交的输入会丢失；其他标签不变。登录跳转及此标签打开的链接沿用同一账号空间。"),13f,u.muted))
            col.addView(u.item(a.accounts.label("",site),tr("默认空间 · 使用原有登录状态")){switch("",url)})
            a.accounts.all().filter{it.site==site}.forEach{item->col.addView(u.item(item.name,if(a.current?.accountId==item.id)tr("正在使用")else tr("切换并重新加载")){switch(item.id,url)})}
            col.addView(u.button(tr("添加账号"),true){edit(null,url)})
            col.addView(u.item(tr("命名默认账号"),tr("给此网站原有的登录状态设置备注")){renameDefault(site)})
            col.addView(u.item(tr("管理已保存账号"),tr("重命名、在新标签中打开或删除")){all()})
        }
    }
    private fun switch(id:String,fallback:String){
        if(a.current?.accountId==id){p.pages.close();return}
        val target=a.currentUrl.takeIf{a.isHttp(it)&&a.store.siteKey(it)==a.store.siteKey(fallback)}?:fallback
        runCatching{a.switchAccount(id,target)}.onFailure{a.toast(it.message?:tr("账号切换失败"))}
    }
    fun all(){
        if(a.isIncognito){a.toast(tr("请退出无痕后管理已保存的账号"));return}
        p.pages.show(tr("网站多账号")){col->
            col.addView(u.label(tr("为常用网站保存独立登录。首次需要在新空间登录，之后保留 Cookie 和网站存储；关闭标签不会删除账号。"),14f))
            col.addView(u.label(tr("账号名称只是你的备注。书签、历史、密码管理与浏览设置仍共用；账号空间不属于无痕，不包含在备份中。"),13f,u.muted))
            if(!PrivateSession.supported())col.addView(u.label(tr("当前 WebView 不支持账号隔离，请更新 Android System WebView。"),14f))
            val list=a.accounts.all()
            if(list.isEmpty())col.addView(u.label(tr("尚未添加账号。打开网站后，在地址栏的网站设置中选择「网站账号」。")))
            (list.map{it.site}+a.accounts.namedSites()).distinct().forEach{site->col.addView(u.label(site,13f,u.accent));if(site in a.accounts.namedSites())col.addView(u.item(a.accounts.label("",site),tr("默认空间 · 修改备注")){renameDefault(site)});list.filter{it.site==site}.forEach{item->col.addView(u.item(item.name,tr("管理账号空间")){detail(item.id)})}}
            if(a.isHttp(a.currentUrl))col.addView(u.button(tr("为当前网站添加账号")){edit(null,a.accounts.find(a.current?.accountId.orEmpty())?.startUrl?:a.currentUrl)})
        }
    }
    private fun renameDefault(site:String){p.pages.show(tr("命名默认账号")){col->
        col.addView(u.label(site));val input=u.edit(tr("账号备注"),a.accounts.label("",site));col.addView(input)
        col.addView(u.label(tr("只修改这个网站的备注，不移动 Cookie 或改变登录。名称改回「默认账号」可移除备注。"),13f,u.muted))
        col.addView(u.button(tr("保存"),true){runCatching{a.accounts.renameDefault(site,input.text.toString());p.pages.back();p.pages.refresh();a.updateAccountBadge()}.onFailure{a.toast(it.message?:tr("无法保存备注"))}})
    }}
    private fun edit(account:SiteAccount?,url:String){p.pages.show(if(account==null)tr("添加网站账号")else tr("重命名账号")){col->
        val input=u.edit(tr("账号备注，例如：工作、个人"),account?.name.orEmpty());col.addView(input)
        col.addView(u.label(if(account==null)tr("创建空白登录空间，并在当前标签重新打开网站首页。原有登录不变；请登录另一个账号。")else tr("只改变备注，不改变登录状态。"),13f,u.muted))
        col.addView(u.button(if(account==null)tr("创建并切换")else tr("保存"),true){runCatching{
            if(account==null){val created=a.accounts.create(input.text.toString(),url);a.switchAccount(created.id,created.startUrl)}
            else {a.accounts.rename(account.id,input.text.toString());p.pages.back();p.pages.refresh();a.updateAccountBadge()}
        }.onFailure{a.toast(it.message?:tr("无法保存账号"))}})
    }}
    private fun detail(id:String){p.pages.show(tr("管理账号")){col->
        val account=a.accounts.find(id)?:return@show
        col.addView(u.title(account.name));col.addView(u.label(account.site))
        col.addView(u.button(tr("在新标签中打开"),true){runCatching{a.openAccount(id)}.onFailure{a.toast(it.message?:tr("无法打开账号"))}})
        col.addView(u.button(tr("重命名")){edit(account,account.startUrl)})
        col.addView(u.button(tr("删除账号空间")){p.confirm(tr("删除 %1\$s？", account.name),tr("将关闭使用此账号的全部标签，清除该空间所有网站的 Cookie、缓存和本地存储，无法撤销。其他账号、书签、历史和已下载文件会保留。")){runCatching{a.deleteAccount(id)}.onFailure{a.toast(it.message?:tr("无法删除账号"))}}})
    }}
}
