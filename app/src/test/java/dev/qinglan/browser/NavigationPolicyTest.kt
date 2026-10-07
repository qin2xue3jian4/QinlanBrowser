package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class NavigationPolicyTest {
    @Test fun httpsUpgradePreservesAddressAndNeverDowngrades(){
        assertEquals("https://172.16.0.252/login?a=1#next",NavigationPolicy.secure("http://172.16.0.252/login?a=1#next",true))
        assertEquals("https://localhost:8080/a",NavigationPolicy.secure("HTTP://localhost:8080/a",true))
        assertEquals("http://host/",NavigationPolicy.secure("http://host/",false))
        assertEquals("https://host/",NavigationPolicy.secure("https://host/",true))
        assertTrue(NavigationPolicy.blocked("HTTP://host/path",true))
        assertFalse(NavigationPolicy.blocked("https://host/path",true))
    }
    @Test fun certificateExceptionsHaveExactOriginScope(){
        assertEquals("https://example.com:443",NavigationPolicy.origin("https://EXAMPLE.com/login?q=secret"))
        assertEquals(NavigationPolicy.origin("https://example.com"),NavigationPolicy.origin("https://example.com:443/a"))
        assertNotEquals(NavigationPolicy.origin("https://example.com"),NavigationPolicy.origin("https://example.com:8443"))
        assertNotEquals(NavigationPolicy.origin("https://example.com"),NavigationPolicy.origin("https://sub.example.com"))
        assertNull(NavigationPolicy.origin("http://example.com"))
        assertNull(NavigationPolicy.origin("https://user:pass@example.com"))
    }
    @Test fun backupsKeepSettingsButNeverCertificateTrust(){
        val prefs=mapOf("httpsOnly" to true,"certificateExceptions" to false,"blobDownloads" to true,"edgeScroll" to true,"shareFormat" to "qr","certificate.https://example.com:443" to "unsafe-pin","menuLayout" to "screenshot,exit,ua,source,settings")
        val result=BackupCodec.parse(BackupCodec.export(null,null,prefs)).settings!!
        assertEquals(true,result["httpsOnly"]);assertEquals("qr",result["shareFormat"])
        assertFalse(result.containsKey("certificate.https://example.com:443"))
        assertEquals(false,SettingCatalog.find("httpsOnly")!!.default)
        assertEquals(false,SettingCatalog.find("certificateExceptions")!!.default)
        assertTrue(MenuLayout.all.containsAll(listOf("screenshot","exit","ua","source")))
    }
}
