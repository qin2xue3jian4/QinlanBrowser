package dev.qinglan.browser

import android.annotation.SuppressLint
import android.content.Context
import android.util.AtomicFile
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import java.io.File

@SuppressLint("RequiresFeature") // A named profile is never loaded without checking both required features.
class AccountProfiles(context:Context) {
    private val defaultNames=context.getSharedPreferences("account_names",Context.MODE_PRIVATE)
    private val file=AtomicFile(File(context.filesDir,"accounts.json"))
    private var readable=true
    private var items=runCatching{AccountCodec.decode(file.openRead().bufferedReader().use{it.readText()})}.getOrElse{
        if(file.baseFile.exists()||File(file.baseFile.path+".bak").exists())readable=false
        emptyList()
    }
    companion object {private val loaded=mutableSetOf<String>()}
    fun all()=items.toList()
    fun find(id:String)=items.firstOrNull{it.id==id}
    fun label(id:String,site:String="")=if(id.isEmpty())defaultNames.getString(site,"默认账号")!!else find(id)?.name?:"已移除的账号"
    fun namedSites()=defaultNames.all.keys.toList()
    fun renameDefault(site:String,name:String){val value=AccountCodec.name(name);require(items.none{it.site==site&&it.name.equals(value,true)}){"此网站已有同名账号"};defaultNames.edit().apply{if(value=="默认账号")remove(site)else putString(site,value)}.apply()}
    fun available(id:String)=id.isEmpty()||(find(id)!=null&&PrivateSession.supported())
    fun profile(id:String):Profile {
        require(find(id)!=null){"账号空间已移除"};check(PrivateSession.supported()){"请更新系统 WebView 后使用多账号"}
        return ProfileStore.getInstance().getOrCreateProfile(id).also{loaded.add(id)}
    }
    fun attach(web:WebView,id:String){if(id.isNotEmpty()){profile(id);WebViewCompat.setProfile(web,id)}}
    fun cookies(id:String):CookieManager=if(id.isEmpty())CookieManager.getInstance()else profile(id).cookieManager
    fun flush(){CookieManager.getInstance().flush();items.filter{it.id in loaded}.forEach{runCatching{cookies(it.id).flush()}}}
    private fun save(next:List<SiteAccount>){
        check(readable){"账号列表读取失败，未覆盖原文件"}
        val out=file.startWrite()
        try{out.write(AccountCodec.encode(next).toByteArray());file.finishWrite(out);items=next}catch(e:Exception){file.failWrite(out);throw e}
    }
    fun create(name:String,url:String):SiteAccount {
        check(PrivateSession.supported()){"请更新系统 WebView 后使用多账号"}
        require(items.size<AccountCodec.LIMIT){"最多保存 ${AccountCodec.LIMIT} 个独立账号，请先移除不用的账号"}
        val account=AccountCodec.create(name,url);unique(account);save(items+account);return account
    }
    private fun unique(account:SiteAccount){require(!account.name.equals(label("",account.site),true)&&!account.name.equals("默认账号",true)&&items.none{it.id!=account.id&&it.site==account.site&&it.name.equals(account.name,true)}){"此网站已有同名账号"}}
    fun rename(id:String,name:String){val account=requireNotNull(find(id)).copy(name=AccountCodec.name(name));unique(account);save(items.map{if(it.id==id)account else it})}
    /** Caller must destroy all views using this account before removal. */
    fun remove(id:String,done:(Boolean)->Unit){
        val profile=profile(id)
        save(items.filterNot{it.id==id}) // Crash-safe tombstone: orphan profiles are removed next startup.
        try{WebStorageCompat.deleteBrowsingData(profile.webStorage){runCatching{ProfileStore.getInstance().deleteProfile(id)};done(true)}}catch(_:Exception){done(false)}
    }
    fun discardOrphans(){
        if(!readable||!PrivateSession.supported())return
        runCatching{val store=ProfileStore.getInstance();store.allProfileNames.filter{it.startsWith(AccountCodec.PREFIX)&&find(it)==null&&it !in loaded}.forEach{runCatching{store.deleteProfile(it)}}}
    }
}
