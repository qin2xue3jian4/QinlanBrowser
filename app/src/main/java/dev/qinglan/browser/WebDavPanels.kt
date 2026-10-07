package dev.qinglan.browser

import android.text.InputType
import android.view.View
import android.widget.LinearLayout

class WebDavPanels(private val p:BrowserPanels) {
    private val a get()=p.a
    private val u get()=p.u
    private val configStore=WebDavConfigStore(a)
    private var busy=false
    private var status=""
    fun show() {
        p.pages.show(tr("WebDAV 设置同步")) { col->
            val saved=runCatching{configStore.read()}.getOrElse{status=it.message.orEmpty();null}
            col.addView(u.label(tr("手动同步浏览器、网站、菜单、搜索与过滤设置及界面语言。不会同步书签、主页收藏、密码、Cookie、历史或标签。"),13f,u.muted))
            col.addView(u.label(tr("设置以 JSON 保存在你指定的 WebDAV 目录。请先创建目录；坚果云使用第三方应用密码。"),13f,u.muted))
            val server=u.edit(tr("HTTPS WebDAV 目录地址"),saved?.directory.orEmpty()).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI}
            val user=u.edit(tr("WebDAV 账号"),saved?.username.orEmpty())
            val password=u.edit(if(saved==null)tr("WebDAV 应用密码")else tr("WebDAV 应用密码（留空保留已保存密码）")).apply{
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
                imeOptions=imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            listOf(server,user,password).forEach(col::addView)
            fun configured(run:(WebDavClient)->Unit) {
                if(password.text.isNotEmpty()||server.text.toString().trim()!=saved?.directory||user.text.toString().trim()!=saved?.username){
                    a.toast(tr("请先保存 WebDAV 配置"));return
                }
                withConfig(run)
            }
            col.addView(u.button(tr("保存 WebDAV 配置"),true){
                if(busy){a.toast(tr("正在同步，请稍候"));return@button}
                runCatching{
                    val secret=password.text.toString().ifEmpty{saved?.password.orEmpty()}
                    require(secret.isNotEmpty()||saved==null){tr("请输入有效的 WebDAV 账号和应用密码")}
                    // A retained password must not be sent to a newly entered server/account.
                    val config=WebDavConfig(server.text.toString().trim(),user.text.toString().trim(),secret).validated()
                    require(password.text.isNotEmpty()||saved==null||config.directory==saved.directory&&config.username==saved.username){tr("更改服务器或账号时，请重新输入应用密码")}
                    configStore.write(config)
                }.onSuccess{password.text.clear();status=tr("WebDAV 配置已加密保存");p.pages.refresh()}.onFailure{a.toast(it.message?:tr("操作失败"))}
            })
            col.addView(u.label(tr("远端文件：%1\$s",WebDavClient.FILENAME),12f,u.muted))
            col.addView(u.button(tr("检查连接 / 下载设置")){configured{client->
                work(tr("正在下载设置…"),{client.download()}){remote->
                    if(remote==null){status=tr("远端尚无设置文件，可上传本机设置；上传时验证写入权限");a.toast(status);p.pages.refresh()}
                    else p.confirm(tr("恢复远端设置"),tr("远端包含 %1\$s 项设置。确认后替换本机可同步设置，未在文件中的项目恢复默认值。",remote.snapshot.settings.size)){
                        runCatching{p.settingsUi.restoreSynced(remote.snapshot)}
                            .onSuccess{status=tr("已恢复远端设置");a.toast(status);if(p.pages.visible)p.pages.refresh()else show()}
                            .onFailure{a.toast(it.message?:tr("操作失败"))}
                    }
                }
            }})
            col.addView(u.button(tr("上传本机设置")){configured{client->
                val raw=runCatching{WebDavSettings.export(p.prefs.all,AppLanguage.choice(a))}.getOrElse{a.toast(it.message?:tr("操作失败"));return@configured}
                work(tr("正在检查远端设置…"),{client.download()}){remote->
                    p.confirm(tr("上传本机设置"),if(remote==null)tr("将在 WebDAV 目录创建设置文件。确认上传当前本机设置？")else tr("远端已有 %1\$s 项设置，确认用当前本机设置替换？",remote.snapshot.settings.size)){
                        work(tr("正在上传设置…"),{client.upload(raw,remote)}){
                            status=tr("本机设置已上传");a.toast(status);p.pages.refresh()
                        }
                    }
                }
            }})
            col.addView(u.button(tr("删除本机 WebDAV 配置")){
                if(busy){a.toast(tr("正在同步，请稍候"));return@button}
                p.confirm(tr("删除本机 WebDAV 配置"),tr("删除本机保存的服务器和凭据；远端设置文件仍保留。")){
                    configStore.clear();status="";p.pages.refresh()
                }
            })
            if(status.isNotBlank())col.addView(u.label(status,13f,u.muted))
        }
    }
    private fun withConfig(run:(WebDavClient)->Unit) {
        if(busy){a.toast(tr("正在同步，请稍候"));return}
        val client=runCatching{WebDavClient(requireNotNull(configStore.read()){tr("请先保存 WebDAV 配置")})}
            .getOrElse{a.toast(it.message?:tr("操作失败"));return}
        run(client)
    }
    private fun <T> work(message:String,operation:()->T,done:(T)->Unit) {
        if(busy){a.toast(tr("正在同步，请稍候"));return}
        busy=true
        var active=true
        p.pages.showFixed(tr("WebDAV 设置同步"),onClose={active=false}){col:LinearLayout->col.addView(u.label(message));col.addView(u.label(tr("可以返回；传输完成后不会自动恢复设置。"),13f,u.muted))}
        Thread {
            val result=runCatching(operation)
            a.runOnUiThread {
                busy=false
                if(!a.isDestroyed&&!a.isFinishing){
                    if(active)p.pages.back()
                    result.onSuccess{if(active)done(it)else a.toast(tr("WebDAV 操作已完成"))}
                        .onFailure{status=it.message?:tr("操作失败");a.toast(status);if(active)p.pages.refresh()}
                }
            }
        }.start()
    }
}
