package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class MaturityTest {
    @Test fun addressHandlesHostsPortsUnicodeAndSearch(){
        fun url(s:String)=(AddressInput.resolve(s) as AddressInput.Result.Navigate).url
        assertEquals("https://example.com:8443/a?x=1#p",url("example.com:8443/a?x=1#p"))
        assertEquals("http://localhost:8877/test",url("localhost:8877/test"))
        assertEquals("http://127.0.0.1:8877",url("127.0.0.1:8877"))
        assertEquals("http://[::1]:8877/",url("[::1]:8877/"))
        assertEquals("https://xn--fsqu00a.xn--0zwm56d/",url("https://例子.测试/"))
        assertTrue(AddressInput.resolve("搜索 两个词") is AddressInput.Result.Search)
        assertTrue(AddressInput.resolve("hello") is AddressInput.Result.Search)
    }
    @Test fun explicitUnsafeAndCredentialInputsNeverBecomeSearches(){
        listOf("javascript:alert(1)","file:///sdcard/private","content://secret","intent://test","https://user:password@example.com/","https://example.com:99999/","https://exa mple.com","https://example.com\\@evil.test/").forEach{assertTrue(it,AddressInput.resolve(it) is AddressInput.Result.Invalid)}
        assertTrue(AddressInput.resolve(" ") is AddressInput.Result.Invalid)
        assertEquals("https://search.test/?q=a%2Bb+%26+c",AddressInput.searchUrl("https://search.test/?q=%s","a+b & c"))
    }
    @Test fun localSuggestionsDeduplicateAndPreferBookmarks(){
        val bookmarks=listOf(Visit("Example bookmark","https://example.com/"),Visit("Example folder","",folder=true))
        val history=listOf(Visit("Example history","https://example.com/"),Visit("Another","https://example.net/"))
        val found=AddressInput.suggestions("example",bookmarks,history)
        assertEquals(2,found.size);assertEquals("书签",found.first().source)
        assertTrue(AddressInput.suggestions("e",bookmarks,history).isEmpty())
    }
    @Test fun webPermissionOriginsAreStrict(){
        assertTrue(WebPermissionPolicy.sameOrigin("https://EXAMPLE.com/path","https://example.com:443/"))
        assertFalse(WebPermissionPolicy.sameOrigin("https://example.com/","https://example.com:444/"))
        assertFalse(WebPermissionPolicy.sameOrigin("http://example.com/","http://example.com/"))
        assertFalse(WebPermissionPolicy.sameOrigin("https://example.com/","https://evil.example.com/"))
        assertFalse(WebPermissionPolicy.sameOrigin("https://user@example.com/","https://example.com/"))
        assertFalse(WebPermissionPolicy.sameOrigin("about:blank","about:blank"))
    }
    @Test fun newSettingsBackupAllowsValidValuesAndRejectsCorruption(){
        val settings=mapOf("recordHistory" to false,"collectionOpen" to "background","activeWebViews" to 6,"readerSpacing" to 160,"site.example.com.camera" to false,"site.example.com.autoplay" to true)
        assertEquals(settings,BackupCodec.parse(BackupCodec.export(null,null,settings)).settings)
        listOf(mapOf("activeWebViews" to 100),mapOf("externalApps" to "always"),mapOf("readerSpacing" to "large"),mapOf("site.example.com.camera" to "yes")).forEach{assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,null,it))}}
    }
}
