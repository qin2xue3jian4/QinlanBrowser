package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class CookieCodecTest {
    private val target="https://chatgpt.com/"
    @Test fun editorRoundTripPreservesSecurityScopeAndExpiry(){
        val e=CookieCodec.Entry("session","a=b==",".chatgpt.com","/auth",true,true,false,"no_restriction",2000000000.0)
        val result=CookieCodec.parseImport(CookieCodec.export(listOf(e)),target,1900000000.0)
        assertEquals(listOf(e),result.entries);assertTrue(result.warnings.isEmpty())
        assertTrue(e.header().contains("HttpOnly"));assertTrue(e.header().contains("SameSite=None"));assertTrue(e.header().contains("Domain=.chatgpt.com"))
    }
    @Test fun preventsCrossSiteAndSuffixConfusion(){
        val raw="""[{"name":"a","value":"x","domain":"evilchatgpt.com"},{"name":"b","value":"x","domain":"chatgpt.com.evil.test"},{"name":"c","value":"x","domain":"auth.openai.com"}]"""
        val r=CookieCodec.parseImport(raw,target);assertEquals(0,r.entries.size);assertEquals(3,r.warnings.size)
    }
    @Test fun rejectsHeaderInjectionExpiredAndPartitioned(){
        val raw="""[{"name":"a","value":"x; Secure","domain":"chatgpt.com"},{"name":"b","value":"x","expirationDate":10},{"name":"c","value":"x","partitionKey":{"topLevelSite":"https://chatgpt.com"}}]"""
        val r=CookieCodec.parseImport(raw,target);assertEquals(0,r.entries.size);assertEquals(3,r.warnings.size)
    }
    @Test fun enforcesHostPrefixAndSameSiteRequirements(){
        val raw="""[{"name":"__Host-session","value":"x","secure":true,"hostOnly":false,"domain":".chatgpt.com"},{"name":"b","value":"x","sameSite":"no_restriction","secure":false}]"""
        assertEquals(0,CookieCodec.parseImport(raw,target).entries.size)
    }
    @Test fun parsesHttpOnlyAndHostOnlyWithoutDroppingEquals(){
        val e=CookieCodec.parseSetCookie("__Host-s=a=b==; Path=/; Secure; HttpOnly; SameSite=Lax",target)
        assertEquals("a=b==",e.value);assertTrue(e.hostOnly);assertEquals("lax",e.sameSite);assertTrue(e.httpOnly)
        assertFalse(e.header().contains("Domain="))
    }
    @Test fun parsesPersistentDomainCookie(){
        val e=CookieCodec.parseSetCookie("token=x; Domain=.chatgpt.com; Path=/; Expires=Wed, 18 May 2033 03:33:20 GMT; Secure",target)
        assertFalse(e.hostOnly);assertEquals(2000000000.0,e.expiry!!,1.0)
    }
    @Test fun webviewCanonicalDomainWithoutDotRemainsHostOnly(){
        val e=CookieCodec.parseSetCookie("__Host-s=x; Domain=chatgpt.com; Path=/; Secure; HttpOnly",target)
        assertTrue(e.hostOnly)
        assertEquals(1,CookieCodec.parseImport(CookieCodec.export(listOf(e)),target).entries.size)
        assertFalse(e.header().contains("Domain="))
    }
    @Test fun preservesSameNameDifferentPathsAndDeduplicatesIdentity(){
        val raw="""[{"name":"a","value":"one","path":"/"},{"name":"a","value":"two","path":"/auth"},{"name":"a","value":"three","path":"/"}]"""
        val r=CookieCodec.parseImport(raw,target);assertEquals(2,r.entries.size);assertEquals("three",r.entries[0].value);assertEquals(1,r.warnings.size)
    }
    @Test fun deleteUsesOriginalScope(){
        val e=CookieCodec.Entry("a","x",".chatgpt.com","/auth",true,true,false)
        assertTrue(e.header(true).contains("Path=/auth"));assertTrue(e.header(true).contains("Domain=.chatgpt.com"));assertTrue(e.header(true).contains("Max-Age=0"))
    }
    @Test(expected=IllegalArgumentException::class) fun partitionedExportFailsExplicitly(){CookieCodec.parseSetCookie("a=x; Secure; Partitioned",target)}
}
