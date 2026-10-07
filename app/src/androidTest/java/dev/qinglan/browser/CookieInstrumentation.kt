package dev.qinglan.browser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses synthetic cookies in a reserved .test domain. Never reads real-site cookies. */
class CookieInstrumentation:Instrumentation(){
    private var userscripts=""
    private var webdav=""
    private var updates=""
    private var controlSite=""
    private var scriptHistory=""
    private var accounts=""
    private var controls=""
    private var incognito=""
    private var importAuthorizedFile=false
    private var revision4=""
    private var filtering=false
    private var resources=false
    private var cleanupResources=false
    private var features=false
    private var cleanupFeatures=false
    private var improvements=false
    private var maturity=""
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);updates=arguments?.getString("updates").orEmpty();controlSite=arguments?.getString("controlSite").orEmpty();webdav=arguments?.getString("webdav").orEmpty();userscripts=arguments?.getString("userscripts").orEmpty();scriptHistory=arguments?.getString("scriptHistory").orEmpty();controls=arguments?.getString("controls").orEmpty();accounts=arguments?.getString("accounts").orEmpty();incognito=arguments?.getString("incognito").orEmpty();maturity=arguments?.getString("maturity").orEmpty();improvements=arguments?.getString("improvements")=="true";cleanupResources=arguments?.getString("cleanupResources")=="true";resources=arguments?.getString("resources")=="true";filtering=arguments?.getString("filtering")=="true";revision4=arguments?.getString("revision4").orEmpty();cleanupFeatures=arguments?.getString("cleanupFeatures")=="true";features=arguments?.getString("features")=="true";importAuthorizedFile=arguments?.getString("importAuthorizedFile")=="true";start()}
    override fun onStart(){
        if(updates.isNotEmpty()){UpdateChecks.run(this,updates);return}
        if(webdav.isNotEmpty()){WebDavChecks.run(this,webdav);return}
        if(userscripts.isNotEmpty()){when(userscripts){"cleanup"->UserScriptCleanupChecks.run(this,scriptHistory);"review"->UserScriptReviewChecks.run(this);"popular"->UserScriptPopularChecks.run(this);"install"->UserScriptInstallChecks.run(this);"remote"->UserScriptInstallChecks.run(this,true);else->UserScriptChecks.run(this)};return}
        if(controls=="navigation"){NavigationChecks.run(this);return}
        if(controls=="menu"){MenuChecks.run(this);return}
        if(controls.isNotEmpty()){ControlsChecks.run(this,controls,controlSite);return}
        if(accounts.isNotEmpty()){AccountChecks.run(this,accounts);return}
        if(incognito.isNotEmpty()){IncognitoChecks.run(this,incognito);return}
        if(maturity.isNotEmpty()){if(maturity=="download")MaturityChecks.download(this)else MaturityChecks.run(this);return}
        if(improvements){ImprovementChecks.run(this);return}
        if(cleanupResources){ResourceChecks.cleanup(this);return}
        if(resources){ResourceChecks.run(this);return}
        if(filtering){FilterChecks.run(this);return}
        if(revision4.isNotEmpty()){when(revision4){"seed"->RevisionChecks.seed(this);"cleanup"->RevisionChecks.cleanup(this);else->RevisionChecks.run(this)};return}
        if(cleanupFeatures){FeatureChecks.cleanup(this);return}
        if(features){FeatureChecks.run(this);return}
        if(importAuthorizedFile){importUserFile();return}
        val result=Bundle()
        try{
            lateinit var manager:CookieManager
            runOnMainSync{WebView(targetContext).destroy();manager=CookieManager.getInstance();manager.setAcceptCookie(true)}
            check(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)){"GET_COOKIE_INFO unsupported"}
            val url="https://qinglan-cookie-test.example.test/auth/page"
            // Chromium caps persistent cookies at 400 days; keep fixture inside that bound.
            val expiry=(System.currentTimeMillis()/1000+30*86400).toDouble()
            val entry=CookieCodec.Entry("__Secure-ql_smoke","synthetic=only",".qinglan-cookie-test.example.test","/auth",true,true,false,"lax",expiry)
            fun set(e:CookieCodec.Entry,delete:Boolean=false){val latch=CountDownLatch(1);var accepted=false;runOnMainSync{manager.setCookie(e.url(),e.header(delete)){ok->accepted=ok;latch.countDown()}};check(latch.await(10,TimeUnit.SECONDS)&&accepted){"Cookie write rejected"}}
            try{
                set(entry);manager.flush()
                val raw=CookieManagerCompat.getCookieInfo(manager,url)
                val found=raw.map{CookieCodec.parseSetCookie(it,url)}.single{it.name==entry.name}
                check(found.value==entry.value&&found.path==entry.path&&found.secure&&found.httpOnly){"Cookie attributes did not survive"}
                check(found.expiry==entry.expiry){"Expiry lost"}
                val round=CookieCodec.parseImport(CookieCodec.export(listOf(found)),url)
                check(round.entries.size==1&&round.warnings.isEmpty()){"Round trip rejected"}
                check(CookieManagerCompat.getCookieInfo(manager,"https://qinglan-cookie-test.example.test/other").none{it.startsWith(entry.name+"=")}){"Path isolation failed"}
                set(entry,true)
                check(CookieManagerCompat.getCookieInfo(manager,url).none{it.startsWith(entry.name+"=")}){"Deletion failed"}
                val hostEntry=CookieCodec.Entry("__Host-ql_smoke","synthetic-only","qinglan-cookie-test.example.test","/",true,true,true,"lax")
                try {
                    set(hostEntry)
                    val hostCookie=CookieManagerCompat.getCookieInfo(manager,url).map{CookieCodec.parseSetCookie(it,url)}.single{it.name==hostEntry.name}
                    check(hostCookie.hostOnly){"Host-only scope not preserved: synthetic domain=${hostCookie.domain}"}
                    check(CookieCodec.parseImport(CookieCodec.export(listOf(hostCookie)),url).entries.size==1){"Host-prefix export cannot be imported"}
                } finally { set(hostEntry,true) }
                result.putString("stream","PASS: GET_COOKIE_INFO, HttpOnly, Secure, domain/path, expiry, JSON round trip, path isolation, host-only prefixes, exact-scope deletion\n")
            }finally{set(entry,true);manager.flush()}
            finish(Activity.RESULT_OK,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n");finish(Activity.RESULT_CANCELED,result)}
    }
    /** Explicitly invoked test-only path. The file stays in this app's private storage. */
    private fun importUserFile(){
        val result=Bundle();val fileName="authorized-cookie-import.json"
        try{
            lateinit var manager:CookieManager
            runOnMainSync{WebView(targetContext).destroy();manager=CookieManager.getInstance();manager.setAcceptCookie(true)}
            val text=targetContext.openFileInput(fileName).bufferedReader().use{it.readText()}
            val parsed=CookieCodec.parseImport(text,"https://chatgpt.com/")
            val latch=CountDownLatch(parsed.entries.size);val accepted=java.util.concurrent.atomic.AtomicInteger()
            runOnMainSync{parsed.entries.forEach{e->manager.setCookie(e.url(),e.header()){ok->if(ok)accepted.incrementAndGet();latch.countDown()}}}
            check(latch.await(30,TimeUnit.SECONDS)){"Timed out while writing"};manager.flush()
            // Only counts. Never print cookie names, values, or the imported JSON.
            result.putString("stream","Authorized import: valid=${parsed.entries.size}, skipped=${parsed.warnings.size}, accepted=${accepted.get()}, rejected=${parsed.entries.size-accepted.get()}\n")
            targetContext.deleteFile(fileName)
            finish(Activity.RESULT_OK,result)
        }catch(e:Throwable){targetContext.deleteFile(fileName);result.putString("stream","Import failed: ${e.javaClass.simpleName}; no credential data logged\n");finish(Activity.RESULT_CANCELED,result)}
    }
}
