package dev.qinglan.browser

import android.app.AlertDialog
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import java.security.MessageDigest

class WebSecurity(private val a:BrowserActivity) {
    private val temporary=mutableMapOf<Long,Pair<String,String>>()
    private val exceptionPages=mutableSetOf<Long>()
    private val beforeStart=mutableMapOf<Long,String>()
    private var prompt:AlertDialog?=null
    private var pending:SslErrorHandler?=null
    private fun fingerprint(error:SslError):String?=SslCertificate.saveState(error.certificate)?.getByteArray("x509-certificate")?.let {
        MessageDigest.getInstance("SHA-256").digest(it).joinToString(""){n->"%02x".format(n.toInt() and 255)}
    }
    fun cancel(){prompt?.dismiss();prompt=null;pending?.cancel();pending=null}
    fun started(tab:BrowserTab,url:String){
        // Chromium may report SSL before onPageStarted. Preserve only that just-approved request.
        tab.web?.clearSslPreferences()
        if(beforeStart.remove(tab.id)!=url){temporary.remove(tab.id);exceptionPages.remove(tab.id)}
    }
    fun begin(tab:BrowserTab){tab.web?.clearSslPreferences();temporary.remove(tab.id);exceptionPages.remove(tab.id);beforeStart.remove(tab.id)}
    fun finished(tab:BrowserTab,web:WebView){temporary.remove(tab.id);beforeStart.remove(tab.id);web.clearSslPreferences()}
    fun trusted(tab:BrowserTab)=tab.id in exceptionPages
    @android.annotation.SuppressLint("WebViewClientOnReceivedSslError") // Explicit, certificate-pinned opt-in, disabled by default.
    fun error(tab:BrowserTab,web:WebView,handler:SslErrorHandler,error:SslError){
        val origin=NavigationPolicy.origin(error.url);val pin=runCatching{fingerprint(error)}.getOrNull()
        val key=origin?.let{"certificate.$it"}
        val allowed=a.prefs.getBoolean("certificateExceptions",false)
        if(allowed&&origin!=null&&pin!=null&&(temporary[tab.id]==(origin to pin)||(!tab.incognito&&a.prefs.getString(key,null)==pin))){
            temporary[tab.id]=origin to pin;exceptionPages.add(tab.id);beforeStart[tab.id]=error.url;handler.proceed();return
        }
        val main=error.url==web.url||error.url==tab.url
        fun block(){handler.cancel();if(main){exceptionPages.remove(tab.id);tab.error={tr("证书验证失败，连接已停止。请检查网址及设备日期时间，或稍后重试。")};a.showPageError(tab)}}
        if(!allowed||origin==null||pin==null||!main||tab!==a.current||!a.hasWindowFocus()){block();return}
        cancel();pending=handler
        val reason=listOf(SslError.SSL_UNTRUSTED to tr("证书颁发者不可信"),SslError.SSL_IDMISMATCH to tr("证书与网址不匹配"),SslError.SSL_EXPIRED to tr("证书已过期"),SslError.SSL_NOTYETVALID to tr("证书尚未生效"),SslError.SSL_INVALID to tr("证书无效")).filter{error.hasError(it.first)}.joinToString("、"){it.second}
        fun decide(i:Int){pending=null;prompt=null
                if(i==0||tab!==a.current||tab !in a.tabs||!a.prefs.getBoolean("certificateExceptions",false)){block();return}
                temporary[tab.id]=origin to pin
                exceptionPages.add(tab.id);beforeStart[tab.id]=error.url
                if(i==2)a.prefs.edit().putString(key,pin).apply()
                tab.error=null;handler.proceed()
        }
        val builder=AlertDialog.Builder(a).setTitle(tr("证书异常"))
            .setMessage(tr("%1\$s\n%2\$s\n此连接无法验证网站身份。例外仅适用于该网址及当前证书，证书变化后会重新询问。\nSHA-256：%3\$s",origin,reason,pin))
            .setNegativeButton(tr("取消")){_,_->decide(0)}.setPositiveButton(tr("信任一次")){_,_->decide(1)}
            .setOnCancelListener{pending=null;prompt=null;block()}
        if(!tab.incognito)builder.setNeutralButton(tr("信任当前网址")){_,_->decide(2)}
        prompt=builder.show()
        if(tab.incognito)prompt?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    // Revocation does not retroactively verify the identity of an already rendered page.
    fun clear(){cancel();temporary.clear();beforeStart.clear();a.tabs.forEach{it.web?.clearSslPreferences()}}
}
