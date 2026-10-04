package dev.qinglan.browser
import android.text.InputType
import android.view.View
import android.widget.*
import org.json.*

class VaultPanels(private val p:BrowserPanels){
    private val a get()=p.a
    private val u get()=p.u
    private val pages get()=p.pages
    private val store get()=p.store
    private val vault=PasswordVault(p.a)
    private fun secret(hint:String)=u.edit(hint).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO}
    private fun fail(e:Throwable){p.info("操作失败",e.message?:"未完成操作")}
    fun passwords(){pages.show("密码管理"){col->col.addView(u.label("密码仅在本机加密保存。手动保存、手动填入，不自动提交登录。",13f,u.muted));col.addView(u.button("新增密码",true){edit()});col.addView(u.button("读取本页已填写的账号密码"){capture()})
        val entries=runCatching{vault.read()}.getOrElse{col.addView(u.label(it.message.orEmpty()));return@show}
        entries.forEach{e->col.addView(u.item(e.name.ifBlank{e.origin},e.username){pages.show("已保存密码"){detail->detail.addView(u.label("${e.origin}\n${e.username}\n密码：••••••••"));detail.addView(u.button("填入当前网页",true){fill(e)});detail.addView(u.button("编辑"){edit(e)});detail.addView(u.button("删除"){p.confirm("删除密码","删除 ${e.origin} 的此账号？"){runCatching{vault.write(vault.read().filterNot{it.origin==e.origin&&it.username==e.username})}.onSuccess{pages.back()}.onFailure(::fail)}})}})}
        col.addView(u.button("加密导出密码"){runCatching{BackupCodec.export(null,null,null,vault.read())}.onSuccess{exportEncrypted(it)}.onFailure(::fail)});col.addView(u.button("导入加密密码文件"){readBackup()})
    }}
    private fun edit(existing:SavedPassword?=null){pages.show("保存密码"){col->val name=u.edit("名称",existing?.name.orEmpty());val url=u.edit("HTTPS 网址",existing?.url?:a.currentUrl.takeIf{it.startsWith("https://")}.orEmpty());val user=u.edit("账号",existing?.username.orEmpty());val pass=secret("密码").apply{setText(existing?.password.orEmpty())};listOf(name,url,user,pass).forEach(col::addView)
        col.addView(u.button("保存",true){val e=SavedPassword(url.text.toString().trim(),user.text.toString(),pass.text.toString(),name.text.toString());runCatching{PasswordCodec.validate(e);val all=vault.read().filterNot{(existing!=null&&it.origin==existing.origin&&it.username==existing.username)||(it.origin==e.origin&&it.username==e.username)}+e;vault.write(all)}.onSuccess{pass.text.clear();pages.back();a.toast("密码已加密保存")}.onFailure(::fail)})
    }}
    private fun capture(){val url=a.currentUrl;if(PasswordCodec.origin(url).isBlank()){a.toast("仅支持 HTTPS 登录页");return};a.current?.web?.evaluateJavascript("""(()=>{const ps=[...document.querySelectorAll('input[type=password]')].filter(e=>e.getClientRects().length);const p=ps.find(e=>e.value);if(!p)return null;const scope=p.form||document;const inputs=[...scope.querySelectorAll('input')];const user=inputs.find(e=>e.autocomplete==='username')||inputs.find(e=>['email','text','tel'].includes(e.type)&&e.getClientRects().length);return {username:user?user.value:'',password:p.value};})()"""){raw->if(a.currentUrl!=url)return@evaluateJavascript;val o=runCatching{JSONObject(raw)}.getOrNull();if(o==null)a.toast("未找到已填写的密码；可手动新增")else edit(SavedPassword(url,o.optString("username"),o.optString("password"),a.current?.title.orEmpty()))}}
    private fun fill(e:SavedPassword){val url=a.currentUrl;if(PasswordCodec.origin(url)!=e.origin){a.toast("只能填入完全相同的 HTTPS 站点");return};val origin=JSONObject.quote(e.origin);val user=JSONObject.quote(e.username);val pass=JSONObject.quote(e.password)
        a.current?.web?.evaluateJavascript("""(()=>{if(location.origin!==$origin)return false;const ps=[...document.querySelectorAll('input[type=password]')].filter(e=>e.getClientRects().length&&!e.disabled);if(ps.length!==1)return false;const p=ps[0],scope=p.form||document;const inputs=[...scope.querySelectorAll('input')];const user=inputs.find(e=>e.autocomplete==='username')||inputs.find(e=>['email','text','tel'].includes(e.type)&&e.getClientRects().length);const set=(e,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};if(user)set(user,$user);set(p,$pass);return true;})()"""){ok->if(ok=="true"){pages.close();a.toast("已填入，尚未提交登录")}else a.toast("没有找到单一可填写的登录表单")}
    }
    fun backup(){pages.show("备份与恢复"){col->col.addView(u.label("可选书签、主页、设置和已保存密码。不包含 Cookie、历史、下载记录或标签会话。",13f,u.muted));val boxes=listOf("书签（含文件夹）","主页（含文件夹）","浏览器及网站设置","已保存密码（必须加密）").mapIndexed{i,t->CheckBox(a).apply{text=t;isChecked=i<3;setTextColor(u.text);minimumHeight=u.dp(48)}.also(col::addView)}
        col.addView(u.button("导出所选项目",true){if(boxes.none{it.isChecked}){a.toast("至少选择一项");return@button};runCatching{BackupCodec.export(if(boxes[1].isChecked)store.home else null,if(boxes[0].isChecked)store.bookmarks else null,if(boxes[2].isChecked)p.prefs.all else null,if(boxes[3].isChecked)vault.read()else null)}.onSuccess{if(boxes[3].isChecked)exportEncrypted(it)else a.writeDocument("qinglan-backup.json","application/json",it)}.onFailure(::fail)});col.addView(u.button("选择备份文件恢复"){readBackup()})
    }}
    private fun exportEncrypted(text:String){pages.show("设置导出密码"){col->col.addView(u.label("文件包含敏感密码，将使用 AES-256-GCM 加密。导入时需要同一个密码，无法找回。至少 4 个字符，建议使用更长的密码。",14f));val pass=secret("导出密码");val again=secret("再次输入密码");col.addView(pass);col.addView(again);val status=u.label("",12f);col.addView(status);val save=u.button("加密并保存",true){}
        save.setOnClickListener{val chars=pass.text.toString().toCharArray();if(chars.size<4||!chars.contentEquals(again.text.toString().toCharArray())){chars.fill('\u0000');status.text="至少 4 个字符，且两次输入应一致";return@setOnClickListener};save.isEnabled=false;status.text="正在加密…";pass.text.clear();again.text.clear();Thread{val result=runCatching{BackupCrypto.encrypt(text,chars)};chars.fill('\u0000');a.runOnUiThread{if(!a.isDestroyed){save.isEnabled=true;status.text="";result.onSuccess{a.writeDocument("qinglan-backup.qlb","application/octet-stream",it)}.onFailure(::fail)}}}.start()};col.addView(save)
    }}
    private fun readBackup(){a.readDocument{raw->if(BackupCrypto.encrypted(raw)){pages.show("解密备份"){col->val pass=secret("导出时设置的密码");col.addView(pass);val status=u.label("");col.addView(status);val unlock=u.button("解密",true){};unlock.setOnClickListener{val chars=pass.text.toString().toCharArray();pass.text.clear();unlock.isEnabled=false;status.text="正在解密…";Thread{val result=runCatching{BackupCrypto.decrypt(raw,chars)};chars.fill('\u0000');a.runOnUiThread{if(!a.isDestroyed){unlock.isEnabled=true;status.text="";result.onSuccess{preview(it,true)}.onFailure{status.text=it.message}}}}.start()};col.addView(unlock)}}else preview(raw,false)}}
    private fun preview(raw:String,protected:Boolean){val snap=runCatching{BackupCodec.parse(raw).also{require(it.passwords==null||protected){"密码只能从加密备份恢复"}}}.getOrElse{fail(it);return}
        pages.show("选择恢复项目"){col->col.addView(u.label("书签、主页和设置替换所选分类；密码按站点和账号合并。未选择的分类保留。",13f,u.muted));val checks=linkedMapOf<String,CheckBox>();fun add(key:String,label:String){checks[key]=CheckBox(a).apply{text=label;isChecked=key!="passwords";setTextColor(u.text)}.also(col::addView)};snap.bookmarks?.let{add("bookmarks","书签 ${it.size} 项")};snap.home?.let{add("home","主页 ${it.size} 项")};snap.settings?.let{add("settings","设置 ${it.size} 项")};snap.passwords?.let{add("passwords","密码 ${it.size} 项（加密保存到本机）")}
            col.addView(u.button("恢复所选项目",true){val selected=checks.filterValues{it.isChecked}.keys;if(selected.isEmpty()){a.toast("请选择项目");return@button};p.confirm("确认恢复","即将恢复 ${selected.size} 类数据。"){val oldHome=store.home.toList();val oldBookmarks=store.bookmarks.toList();runCatching{
                // Validate/decrypt the vault before touching any other data.
                val oldVault=if("passwords" in selected)vault.read()else null
                if("home" in selected){store.home.clear();store.home.addAll(snap.home!!)};if("bookmarks" in selected){store.bookmarks.clear();store.bookmarks.addAll(snap.bookmarks!!)};store.save()
                if(oldVault!=null){val merged=linkedMapOf<Pair<String,String>,SavedPassword>();(oldVault+snap.passwords!!).forEach{merged[it.origin to it.username]=it};vault.write(merged.values.toList())}
                if("settings" in selected){val edit=p.prefs.edit();p.prefs.all.keys.filter(BackupCodec::allowed).forEach(edit::remove);snap.settings!!.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v)}};edit.apply();a.filtering.subscriptions.rebuild()}
            }.onSuccess{a.retheme();pages.back();a.toast("已恢复所选项目")}.onFailure{store.home.clear();store.home.addAll(oldHome);store.bookmarks.clear();store.bookmarks.addAll(oldBookmarks);runCatching{store.save()};fail(it)}}})
        }
    }
}
