package dev.qinglan.browser

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/** DownloadManager follows insecure redirects; HTTPS-only transfers enforce every hop here. */
object SecureDownloads {
    internal fun transfer(url:String,ua:String,referer:String,cookie:(String)->String?,file:File,connect:(String)->HttpURLConnection={URL(it).openConnection() as HttpURLConnection}){
        var next=url
        repeat(9){
            require(URI(next).scheme=="https"){tr("仅 HTTPS 模式已阻止 HTTP 下载")}
            val connection=connect(next)
            connection.instanceFollowRedirects=false;connection.connectTimeout=20000;connection.readTimeout=30000
            connection.setRequestProperty("User-Agent",ua)
            cookie(next)?.let{connection.setRequestProperty("Cookie",it)}
            if(NavigationPolicy.origin(referer)!=null&&NavigationPolicy.origin(referer)==NavigationPolicy.origin(next))connection.setRequestProperty("Referer",referer)
            try{
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)){
                    next=URI(next).resolve(connection.getHeaderField("Location")?:error("Missing redirect")).toString()
                    require(URI(next).rawUserInfo==null)
                }else {check(code in 200..299){"HTTP $code"};connection.inputStream.use{input->file.outputStream().use{input.copyTo(it)}};return}
            }finally{connection.disconnect()}
        };error("Too many redirects")
    }
    fun start(a:BrowserActivity,url:String,ua:String,referer:String,cookies:android.webkit.CookieManager,name:String,mime:String){
        if(a.prefs.getBoolean("downloadWifiOnly",false)){
            val connectivity=a.getSystemService(ConnectivityManager::class.java)
            if(connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)!=true){a.toast(tr("等待 Wi-Fi"));return}
        }
        a.toast(tr("已开始下载"))
        Thread({val file=File(a.cacheDir,"https-${UUID.randomUUID()}.part")
            val result=runCatching{transfer(url,ua,referer,cookies::getCookie,file)}
            a.runOnUiThread{if(result.isSuccess)a.exports.saveFile(file,name,mime.ifBlank{"application/octet-stream"})else{file.delete();if(!a.isDestroyed)a.toast(tr("下载失败：%1\$s",result.exceptionOrNull()?.message))}}
        },"https-download").start()
    }
}
