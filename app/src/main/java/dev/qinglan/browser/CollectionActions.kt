package dev.qinglan.browser

import android.view.Menu

object CollectionActions {
    /** Native submenus position themselves on the side with available screen space. */
    fun add(a:BrowserActivity,menu:Menu,url:String){
        val site=a.store.siteKey(url)
        val accounts=if(a.isIncognito)emptyList()else a.accounts.all().filter{it.site==site}
        val configured=!a.isIncognito&&(accounts.isNotEmpty()||a.accounts.hasDefaultName(site))
        listOf(tr("打开") to false,tr("在新标签页打开") to true).forEach{(title,newTab)->
            if(!configured)menu.add(title).setOnMenuItemClickListener{a.panels.pages.close();a.open(url,newTab);true}
            else {val sub=menu.addSubMenu(title)
                sub.add(a.accounts.label("",site)).setOnMenuItemClickListener{a.openWithAccount(url,"",newTab);true}
                accounts.forEach{account->sub.add(account.name).setOnMenuItemClickListener{a.openWithAccount(url,account.id,newTab);true}}
            }
        }
    }
}
