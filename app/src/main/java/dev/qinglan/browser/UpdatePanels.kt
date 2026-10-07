package dev.qinglan.browser

import android.app.AlertDialog
import android.content.Context

class UpdatePanels(private val p:BrowserPanels,private val fetch:()->AppRelease?={GitHubUpdates.latest()},stateName:String="app_updates") {
    private val a get()=p.a
    private val u get()=p.u
    private val state=a.getSharedPreferences(stateName,Context.MODE_PRIVATE)
    private var latest=runCatching{state.getString("release",null)?.let(AppRelease::parse)}.getOrNull()
    private var busy=false
    private var dead=false
    private var foreground=false
    private var prompt:AlertDialog?=null
    fun resumed(){foreground=true;automatic();notifyPending()}
    fun paused(){foreground=false;prompt?.dismiss();prompt=null}
    fun close(){dead=true;paused()}
    fun automatic() {
        if(dead||busy||a.isIncognito||!p.prefs.getBoolean("autoCheckUpdates",true))return
        val now=System.currentTimeMillis()
        if(!UpdatePolicy.due(now,state.getLong("attempt",0)))return
        check(false)
    }
    fun show() {
        p.pages.show(tr("检查更新")){col->
            col.addView(u.label(tr("当前版本：%1\$s",BuildConfig.VERSION_NAME)))
            col.addView(u.label(tr("仅检查 GitHub 公开发布的正式版本。自动检查最多每天一次，不发送浏览记录、设置或账号信息。"),13f,u.muted))
            latest?.takeIf{it.newerThan(BuildConfig.VERSION_NAME)}?.let{release->
                col.addView(u.item(tr("发现新版本 %1\$s",release.version.name),release.title){details(release)})
            }
            col.addView(u.button(tr("立即检查更新"),true){if(busy)a.toast(tr("正在检查更新，请稍候"))else check(true)})
            col.addView(u.item(tr("自动检查更新"),if(p.prefs.getBoolean("autoCheckUpdates",true))tr("开启")else tr("关闭")){p.settingsUi.open("autoCheckUpdates")})
            col.addView(u.button(tr("GitHub 发布页")){open(ProjectLinks.releases)})
            val time=state.getLong("checked",0)
            if(time>0)col.addView(u.label(tr("上次成功检查：%1\$s",java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))),12f,u.muted))
        }
    }
    private fun check(manual:Boolean) {
        busy=true;state.edit().putLong("attempt",System.currentTimeMillis()).apply()
        var active=manual
        if(manual)p.pages.showFixed(tr("检查更新"),onClose={active=false}){it.addView(u.label(tr("正在检查 GitHub 更新…")))}
        Thread {
            val result=runCatching(fetch)
            a.runOnUiThread {
                busy=false
                if(dead||a.isDestroyed||a.isFinishing)return@runOnUiThread
                result.onSuccess{release->latest=release
                    state.edit().putLong("checked",System.currentTimeMillis()).apply{
                        if(release==null)remove("release")else putString("release",release.cache())
                    }.apply()
                }
                if(manual&&active){
                    p.pages.back()
                    result.onSuccess{release->when{
                        release==null->p.info(tr("检查更新"),tr("未找到可安装的公开正式版本，请前往 GitHub 发布页查看。"))
                        release.newerThan(BuildConfig.VERSION_NAME)->details(release)
                        else->p.info(tr("检查更新"),tr("当前已是最新版本（%1\$s）。",BuildConfig.VERSION_NAME))
                    }}.onFailure{a.toast(it.message?:tr("检查更新失败"))}
                }else if(!manual)notifyPending()
            }
        }.start()
    }
    fun notifyPending() {
        if(dead||!foreground||prompt!=null||!a.hasWindowFocus()||a.isIncognito||p.pages.visible||a.overlayKind.isNotEmpty()||a.fullScreen||
            a.currentUrl!="about:home"||!p.prefs.getBoolean("autoCheckUpdates",true))return
        val release=latest?.takeIf{it.newerThan(BuildConfig.VERSION_NAME)}?:return
        if(state.getString("prompted",null)==release.tag)return
        state.edit().putString("prompted",release.tag).apply()
        prompt=AlertDialog.Builder(a).setTitle(tr("发现新版本 %1\$s",release.version.name))
            .setMessage(tr("清岚有新版本可用，可查看更新说明并前往 GitHub 下载。"))
            .setPositiveButton(tr("查看更新")){_,_->details(release)}.setNegativeButton(tr("稍后"),null)
            .create().also{it.setOnDismissListener{prompt=null};it.show()}
    }
    private fun details(release:AppRelease) {
        p.pages.show(tr("发现新版本 %1\$s",release.version.name)){col->
            col.addView(u.label(tr("当前 %1\$s → 新版 %2\$s",BuildConfig.VERSION_NAME,release.version.name)))
            col.addView(u.label(release.title,18f))
            col.addView(u.label(release.notes.ifBlank{tr("此版本未提供更新说明。")},14f))
            col.addView(u.label(tr("前往 GitHub 下载 APK 后，按系统提示安装。覆盖升级需要安装包签名与当前版本一致。"),13f,u.muted))
            col.addView(u.button(tr("前往 GitHub 下载"),true){open(release.page)})
        }
    }
    private fun open(url:String){p.pages.close();a.open(url,true)}
}
