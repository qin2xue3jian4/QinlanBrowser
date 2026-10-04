package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserPreferencesTest {
    @Test fun settingsSearchFindsSynonymsAndScopes(){
        assertTrue(SettingCatalog.search("缩放").any{it.id=="textZoom"})
        assertTrue(SettingCatalog.search("cookie").map{it.id}.containsAll(listOf("thirdParty","cookies","clear")))
        assertTrue(SettingCatalog.search("网站 覆盖").any{it.id=="siteOverrides"})
        assertTrue(SettingCatalog.search("does not exist").isEmpty())
    }
    @Test fun malformedMenuCannotRemoveSettingsOrDuplicateActions(){
        assertEquals(listOf("find","settings"),MenuLayout.parse("missing,find,find,settings,settings"))
        assertEquals(listOf("settings"),MenuLayout.parse(""))
        assertEquals(listOf("history","find","settings"),MenuLayout.move(listOf("find","history","settings"),"history","find"))
    }
    @Test fun newPreferencesRoundTripButSessionsStayPrivate(){
        val input=mapOf("js" to false,"webDark" to false,"site.example.com.textZoom" to 135,"menuLayout" to "find,downloads,settings","closedTabs" to "private")
        val parsed=BackupCodec.parse(BackupCodec.export(null,null,input)).settings!!
        assertEquals(135,parsed["site.example.com.textZoom"]);assertEquals(false,parsed["js"])
        assertEquals("find,downloads,settings",parsed["menuLayout"]);assertFalse(parsed.containsKey("closedTabs"))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,null,mapOf("site.example.com.textZoom" to 900)))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,null,mapOf("js" to "false")))}
    }
    @Test fun undoExpiresAndBoundsMetadata(){
        var now=1000L;val recent=ClosedTabs{now}
        repeat(12){recent.push(ClosedTab("https://example.com/$it","$it",it,null,null))}
        assertEquals("11",recent.pop()!!.title);repeat(9){assertNotNull(recent.pop())};assertNull(recent.pop())
        recent.push(ClosedTab("https://example.com/","Page",0,null,null));now+=600001
        assertFalse(recent.available());assertNull(recent.pop())
    }
}
