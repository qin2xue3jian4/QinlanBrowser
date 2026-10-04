package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class OrganizeTest {
    @Test fun homeReordersBothDirectionsAndMovesAcrossFolder(){
        val items=mutableListOf(HomeItem("a","A","https://a.test/"),HomeItem("b","B","https://b.test/"),HomeItem("f","Folder",folder=true))
        LibraryOrder.home(items,"a","","b",true);assertEquals(listOf("b","a","f"),items.map{it.id})
        LibraryOrder.home(items,"a","","b",false);assertEquals(listOf("a","b","f"),items.map{it.id})
        LibraryOrder.home(items,"a","f");assertEquals("f",items.find{it.id=="a"}!!.parent)
        LibraryOrder.home(items,"a","");assertEquals("",items.find{it.id=="a"}!!.parent)
        assertEquals(3,items.size)
    }
    @Test fun preventsFolderCyclesWithoutMutating(){val items=mutableListOf(HomeItem("a","A",folder=true),HomeItem("b","B",folder=true));assertThrows(IllegalArgumentException::class.java){LibraryOrder.home(items,"a","b")};assertEquals("",items[0].parent)}
    @Test fun bookmarkBatchPreservesOrderAndUnselectedChildren(){val items=mutableListOf(Visit("F","",id="f",folder=true),Visit("A","https://a.test",id="a",parent="f"),Visit("B","https://b.test",id="b"),Visit("C","https://c.test",id="c"))
        LibraryOrder.bookmarks(items,setOf("b","c"),"f","a");assertEquals(listOf("b","c","a"),items.filter{it.parent=="f"}.map{it.id})
        LibraryOrder.deleteBookmarks(items,setOf("f","b"));assertEquals(setOf("a","c"),items.map{it.id}.toSet());assertTrue(items.all{it.parent.isEmpty()})
    }
    @Test fun multipleNamedEnginesSurviveSettingsBackup(){val engines=listOf(SearchEngines.Custom("a","引擎甲","https://a.test/?q=%s"),SearchEngines.Custom("b","引擎乙","https://b.test/?q=%s"));val raw=SearchEngines.json(engines);val snapshot=BackupCodec.parse(BackupCodec.export(null,null,mapOf("customSearches" to raw,"palette" to "custom","customColor" to "#12AB34")));assertEquals(engines,SearchEngines.parse(snapshot.settings!!["customSearches"] as String));assertEquals("引擎乙",SearchEngines.name(engines[1].url,engines))}
    @Test fun rejectsInvalidEnginesAndColors(){assertThrows(IllegalArgumentException::class.java){SearchEngines.parse(SearchEngines.json(listOf(SearchEngines.Custom(name="bad",url="javascript:%s"))))};assertThrows(IllegalArgumentException::class.java){BackupCodec.parse(BackupCodec.export(null,null,mapOf("customColor" to "#FFFF")))} }
}
