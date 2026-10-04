package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class FilterEngineTest {
    private val psl = PublicSuffixes("com\nco.uk\ngithub.io\n*.ck\n!www.ck")
    private fun engine(text: String) = FilterEngine(listOf(text), psl)
    private fun request(url: String, page: String="https://news.example.com/", type: String?=null) = FilterEngine.Request(url,page,type)
    @Test fun domainAnchorAndSeparatorDoNotOvermatch() {
        val e=engine("||ads.example.com^")
        assertNotNull(e.blocked(request("https://sub.ads.example.com/ad.js")))
        assertNotNull(e.blocked(request("https://ads.example.com:8443/ad.js")))
        assertNull(e.blocked(request("https://ads.example.com.evil.test/ad.js")))
        assertNull(e.blocked(request("https://normal.test/path/ads.example.com/ad.js")))
        assertNull(e.blocked(request("https://normal.test/?url=ads.example.com/ad.js")))
        assertNull(e.blocked(FilterEngine.Request("https://ads.example.com/","https://example.com/",mainFrame=true)))
    }
    @Test fun wildcardAnchorsAndCase() {
        assertTrue(FilterEngine.matchesPattern("/ads/*/banner^", "https://x.test/ads/123/banner?x=1"))
        assertFalse(FilterEngine.matchesPattern("/ads/*/banner^", "https://x.test/ads/123/bannerExtra"))
        assertTrue(FilterEngine.matchesPattern("|https://x.test/a|", "https://x.test/a"))
        assertFalse(FilterEngine.matchesPattern("|https://x.test/a|", "https://x.test/abc"))
        assertTrue(FilterEngine.matchesPattern("/AD.js", "https://x.test/ad.js"))
        assertFalse(FilterEngine.matchesPattern("/AD.js", "https://x.test/ad.js",true))
        assertTrue(FilterEngine.matchesPattern("||ads.*.test^", "https://ads.foo.test"))
    }
    @Test fun exceptionsAlwaysWinAndDomainExclusionsApply() {
        val e=engine("/ad.js\n@@||cdn.example.com/ad.js\n/banner\$domain=example.com|~safe.example.com")
        assertNull(e.blocked(request("https://cdn.example.com/ad.js")))
        assertNotNull(e.blocked(request("https://cdn.test/ad.js")))
        assertNotNull(e.blocked(request("https://cdn.test/banner")))
        assertNull(e.blocked(request("https://cdn.test/banner","https://safe.example.com/")))
        assertNull(e.blocked(request("https://cdn.test/banner","https://elsewhere.test/")))
    }
    @Test fun unsupportedOptionsNeverBecomeUnconditionalBlocks() {
        val e=engine("/a\$redirect=noop.js\n/b\$important\n/c\$badfilter\n/d\$script,unsupported\n/regex.*/\nexample.com##+js(noop)\nexample.com#?#div:has-text(Ad)")
        assertEquals(0,e.accepted);assertEquals(7,e.skipped)
        assertNull(e.blocked(request("https://example.com/abcd")))
    }
    @Test fun unknownResourceTypeDoesNotMatchTypedRules() {
        val e=engine("/ads/*\$script\n/banner/*\$~image")
        assertNull(e.blocked(request("https://cdn.test/ads/file")))
        assertNotNull(e.blocked(request("https://cdn.test/ads/file",type="script")))
        assertNull(e.blocked(request("https://cdn.test/ads/file",type="image")))
        assertNull(e.blocked(request("https://cdn.test/banner/file")))
        assertNull(e.blocked(request("https://cdn.test/banner/file",type="image")))
        assertNotNull(e.blocked(request("https://cdn.test/banner/file",type="script")))
    }
    @Test fun thirdPartyUsesRegistrableAndPrivateSuffixes() {
        val e=engine("/ads/*\$third-party")
        assertNull(e.blocked(request("https://cdn.example.co.uk/ads/","https://www.example.co.uk/")))
        assertNotNull(e.blocked(request("https://another.co.uk/ads/","https://www.example.co.uk/")))
        assertNotNull(e.blocked(request("https://other.github.io/ads/","https://my.github.io/")))
        assertEquals("a.b.ck",psl.site("www.a.b.ck"))
        assertEquals("www.ck",psl.site("sub.www.ck"))
        assertEquals("127.0.0.1",psl.site("127.0.0.1"))
    }
    @Test fun cosmeticScopesAndExceptions() {
        val e=engine("##.generic\nexample.com##.ad\nexample.com#@#.generic\nexample.com,~safe.example.com###banner\nexample.com##div:has-text(Ad)")
        assertEquals(setOf(".ad","#banner"),e.selectors("https://example.com/page").toSet())
        assertEquals(listOf(".ad"),e.selectors("https://safe.example.com/").toList())
        assertEquals(listOf(".generic"),e.selectors("https://other.test/"))
        assertEquals(1,e.skipped)
        assertFalse(FilterEngine.validSelector("div { color:red }"))
    }
    @Test fun indexedAndShortRulesMatch() {
        val e=engine((0..5000).joinToString("\n"){"||ads$it.example.test^"}+"\n/a^\n/alpha-long-token/*\n@@||ads123.example.test/allowed")
        assertNotNull(e.blocked(request("https://ads123.example.test/banner")))
        assertNull(e.blocked(request("https://ads123.example.test/allowed")))
        assertNotNull(e.blocked(request("https://cdn.test/a?1")))
        assertNotNull(e.blocked(request("https://cdn.test/alpha-long-token/123")))
    }
    @Test fun subscriptionValidationAndBackup() {
        assertEquals(FilterSubscriptions.defaults,FilterSubscriptions.parse(FilterSubscriptions.encode(FilterSubscriptions.defaults)))
        assertFalse(FilterSubscriptions.validUrl("http://example.com/list.txt"))
        assertFalse(FilterSubscriptions.validUrl("https://user:pass@example.com/list.txt"))
        val config=mapOf("filterRules" to "example.com##.ad","filterSubscriptions" to FilterSubscriptions.encode(FilterSubscriptions.defaults),"adblockEnabled" to true,"site.example.com.adblock" to false)
        assertEquals(config,BackupCodec.parse(BackupCodec.export(null,null,config)).settings)
    }
}
