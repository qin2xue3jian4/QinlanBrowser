package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class AccountCodecTest {
    @Test fun metadataKeepsNamesAndDropsLoginQuery(){
        val a=AccountCodec.create(" 工作 ","https://EXAMPLE.com:8443/login?token=secret#private")
        assertEquals("工作",a.name);assertEquals("example.com",a.site);assertEquals("https://example.com:8443/",a.startUrl)
        assertEquals(listOf(a),AccountCodec.decode(AccountCodec.encode(listOf(a))))
        assertFalse(AccountCodec.encode(listOf(a)).contains("secret"))
    }
    @Test fun rejectsUnsafeOriginsAndNames(){
        listOf("file:///etc/test","javascript:alert(1)","https://user:pass@example.com/","https:///path").forEach{assertTrue(runCatching{AccountCodec.create("工作",it)}.isFailure)}
        listOf(" ","a".repeat(25),"work\nother").forEach{assertTrue(runCatching{AccountCodec.name(it)}.isFailure)}
    }
    @Test fun rejectsDuplicateOrForeignProfileIdentifiers(){
        val a=AccountCodec.create("个人","https://example.com")
        assertTrue(runCatching{AccountCodec.decode(AccountCodec.encode(listOf(a,a)))}.isFailure)
        assertTrue(runCatching{AccountCodec.decode(AccountCodec.encode(listOf(a.copy(id="Default"))))}.isFailure)
        assertTrue(runCatching{AccountCodec.decode(AccountCodec.encode(listOf(a,a.copy(id=AccountCodec.create("other",a.startUrl).id))))}.isFailure)
    }
    @Test fun closedTabsRememberAccountAndDeletionRemovesOnlyThatAccount(){
        val tabs=ClosedTabs();tabs.push(ClosedTab("https://a.test","A",0,null,null,"a"));tabs.push(ClosedTab("https://b.test","B",1,null,null,"b"));tabs.removeAccount("a")
        assertEquals("b",tabs.pop()!!.accountId);assertNull(tabs.pop())
    }
}
