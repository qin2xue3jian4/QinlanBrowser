package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class BackupCodecTest {
    @Test fun exportExcludesPrivateStateAndKeepsFolderStructure(){
        val home=listOf(HomeItem("folder","AI",folder=true),HomeItem("site","ChatGPT","https://chatgpt.com/","folder"))
        val text=BackupCodec.export(home,listOf(Visit("Example","https://example.com/")),mapOf("theme" to "dark","site.example.com.js" to false,"tabs" to "secret-tab","history" to "secret-history","cookies" to "secret-cookie","downloads" to setOf("42")))
        assertFalse(text.contains("secret"));assertFalse(text.contains("downloads"))
        val restored=BackupCodec.parse(text);assertEquals("folder",restored.home!![1].parent);assertEquals(false,restored.settings!!["site.example.com.js"]);assertEquals(1,restored.bookmarks!!.size)
    }
    @Test fun selectionDoesNotInventMissingSections(){val parsed=BackupCodec.parse(BackupCodec.export(null,emptyList(),null));assertNull(parsed.home);assertNull(parsed.settings);assertTrue(parsed.bookmarks!!.isEmpty())}
    @Test fun rejectsDanglingOrNestedFolders(){
        val invalid=listOf(HomeItem("site","A","https://example.com/","missing"))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(invalid,null,null))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(listOf(HomeItem("folder","A",parent="folder",folder=true)),null,null))}
    }
    @Test fun rejectsExecutableUrlsAndInvalidSettings(){
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,listOf(Visit("bad","javascript:alert(1)")),null))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,null,mapOf("textZoom" to 100000)))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,null,mapOf("search" to "file:///%s")))}
    }
    @Test fun ignoresUnapprovedSettingsOnImport(){val raw=JSONObject(BackupCodec.export(null,null,mapOf("restore" to true)));raw.getJSONObject("settings").put("cookies","bad").put("tabs","bad");assertEquals(setOf("restore"),BackupCodec.parse(raw.toString()).settings!!.keys)}
    @Test fun rejectsDuplicateIdsAndFutureFormat(){
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(listOf(HomeItem("x","A",folder=true),HomeItem("x","B",folder=true)),null,null))}
        val raw=JSONObject(BackupCodec.export(null,emptyList(),null)).put("version",99)
        assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(raw.toString())}
    }
    @Test fun searchPresetsAndCustomValidation(){SearchEngines.presets.values.forEach{assertTrue(SearchEngines.valid(it))};assertFalse(SearchEngines.valid("https://example.com/"));assertFalse(SearchEngines.valid("https://user:secret@example.com/?q=%s"))}
}
