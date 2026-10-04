package dev.qinglan.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/** Never reuses a private profile, including after a crash or failed cleanup. UI thread only. */
@SuppressLint("RequiresFeature") // All entry points check both features before creating a session.
class PrivateSession private constructor(val profile:Profile) {
    val name get()=profile.name
    val cookies get()=profile.cookieManager
    private var closing=false
    fun attach(web:WebView){check(!closing);WebViewCompat.setProfile(web,name)}
    /** Call only after every WebView using this profile has been destroyed. */
    fun close(done:(Boolean)->Unit={}){
        if(closing)return
        closing=true
        try{WebStorageCompat.deleteBrowsingData(profile.webStorage){
            // Loaded profiles cannot be deleted by some providers until the process restarts.
            // Website data is already cleared; startup removes their remaining profile metadata.
            runCatching{ProfileStore.getInstance().deleteProfile(name)}
            done(true)
        }}catch(_:Exception){done(false)}
    }
    companion object {
        private const val PREFIX="qinglan_private_"
        private val loaded=mutableSetOf<String>()
        fun supported()=WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)&&WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
        fun discardStale(){
            if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))return
            runCatching{
                val store=ProfileStore.getInstance()
                store.allProfileNames.filter{it.startsWith(PREFIX)&&it !in loaded}.forEach{runCatching{store.deleteProfile(it)}}
            }
        }
        fun create():PrivateSession {
            check(supported()){tr("请更新 Android System WebView 后使用无痕模式")}
            discardStale()
            val name=PREFIX+UUID.randomUUID().toString()
            val profile=ProfileStore.getInstance().getOrCreateProfile(name)
            loaded.add(name)
            return PrivateSession(profile)
        }
    }
}
