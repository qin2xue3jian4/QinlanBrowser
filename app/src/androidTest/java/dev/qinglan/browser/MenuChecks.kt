package dev.qinglan.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu

/** Check submenu titles and cancel the actual close-all dialog without closing user tabs. */
object MenuChecks {
    fun run(r:Instrumentation){
        val result=Bundle();var passed=true;var dialog:AlertDialog?=null
        fun main(run:()->Unit){var failure:Throwable?=null;r.runOnMainSync{try{run()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
        try {
            val a=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity
            var before=emptyList<Long>();var pagesVisible=false
            main {
                val site="menu-check.example.test";val oldName=a.accounts.label("",site)
                try {
                    a.accounts.renameDefault(site,"Menu check")
                    val menu=PopupMenu(a,a.bottom).menu;CollectionActions.add(a,menu,"https://$site/")
                    listOf(tr("打开"),tr("在新标签页打开")).forEachIndexed{i,title->
                        check(menu.getItem(i).title==title){"Redundant arrow in submenu title"}
                        check(menu.getItem(i).hasSubMenu());check(menu.getItem(i).subMenu!!.getItem(0).title=="Menu check")
                    }
                    val plain=PopupMenu(a,a.bottom).menu;CollectionActions.add(a,plain,"https://plain-menu-check.example.test/")
                    check(!plain.getItem(0).hasSubMenu()&&!plain.getItem(1).hasSubMenu())
                } finally {a.accounts.renameDefault(site,oldName)}
                check(MenuLayout.parse(a.prefs.getString("menuLayout",null))==MenuLayout.defaults){"Defaults differ from the phone's menu"}
                check(a.prefs.getInt("menuColumns",MenuLayout.defaultColumns)==MenuLayout.defaultColumns)
                check(SettingCatalog.find("menuColumns")!!.default==MenuLayout.defaultColumns)
                before=a.tabs.map{it.id};pagesVisible=a.panels.pages.visible
                dialog=a.panels.confirmCloseAllTabs()
                check(a.panels.pages.visible==pagesVisible){"Confirmation navigated to a subpage"}
            }
            r.waitForIdleSync()
            main {
                val d=dialog!!;check(d.isShowing){"Confirmation popup did not open"}
                val decor=d.window!!.decorView;val screen=a.resources.displayMetrics
                check(decor.height in 1 until screen.heightPixels/2){"Confirmation is not a small popup"}
                check(d.getButton(AlertDialog.BUTTON_POSITIVE).text==tr("确认"))
                d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            r.waitForIdleSync()
            main {check(!dialog!!.isShowing){"Cancel did not dismiss popup"};check(a.tabs.map{it.id}==before){"Cancel closed user tabs"}}
            result.putString("stream","PASS: single native submenu arrow; phone menu defaults/5 columns; compact close-all popup and cancellation preserving tabs.\n")
        } catch(e:Throwable){passed=false;result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}
        finally {main{dialog?.dismiss()}}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
