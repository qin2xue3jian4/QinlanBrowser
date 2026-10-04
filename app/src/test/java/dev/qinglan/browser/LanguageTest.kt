package dev.qinglan.browser

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LanguageTest {
    @After fun reset(){LocalText.lookup=null}
    @Test fun systemLanguagesSelectTheFirstSupportedLanguage(){
        assertEquals("en",LanguageChoice.resolve(emptyList()))
        assertEquals("en",LanguageChoice.resolve(listOf("fr-FR","de-DE")))
        assertEquals("zh-Hant",LanguageChoice.resolve(listOf("ja-JP","zh-TW","en-US")))
        assertEquals("en",LanguageChoice.resolve(listOf("en-GB","zh-CN")))
    }
    @Test fun chineseScriptOverridesRegion(){
        for(tag in listOf("zh-TW","zh-HK","zh-MO","zh-Hant","zh-Hant-CN"))assertEquals(tag,"zh-Hant",LanguageChoice.match(tag))
        for(tag in listOf("zh","zh-CN","zh-SG","zh-Hans","zh-Hans-TW"))assertEquals(tag,"zh-Hans",LanguageChoice.match(tag))
    }
    @Test fun argumentsAreNotTranslatedOrFormattedAgain(){
        LocalText.lookup={if(it=="删除 %1\$s？")"Delete %1\$s?"else "SHOULD NOT TRANSLATE DATA"}
        assertEquals("Delete 设置 %2\$s \\ folder?",tr("删除 %1\$s？","设置 %2\$s \\ folder"))
    }
    @Test fun translatedTemplatesCanReorderArguments(){
        LocalText.lookup={"%2\$s → %1\$s; %2\$s"}
        assertEquals("B → A; B",tr("%1\$s %2\$s","A","B"))
    }
    @Test fun literalPercentAndSearchTemplatesRemainLiteral(){
        assertEquals("100% · https://example.com/?q=%s",tr("100% · https://example.com/?q=%s"))
        assertEquals("10%",tr("%1\$s%",10))
    }
    @Test fun settingsAndPresetsRefreshWithoutChangingIds(){
        val ids=SettingCatalog.entries.map{it.id}
        assertTrue(SettingCatalog.search("字号").any{it.id=="textZoom"})
        val bing=SearchEngines.presets.values.first()
        LocalText.lookup={when(it){"网页字号"->"Page text size";"外观"->"Appearance";"必应"->"Bing";"所有时间"->"All time";"视频"->"Video";else->it}}
        assertEquals(ids,SettingCatalog.entries.map{it.id})
        assertTrue(SettingCatalog.search("page text size").any{it.id=="textZoom"})
        assertEquals("Appearance",SettingCatalog.find("textZoom")!!.group)
        assertEquals(bing,SearchEngines.presets["Bing"])
        assertEquals("All time",HistoryRange.names[0])
        assertEquals("Video",ResourceKind.VIDEO.label)
        LocalText.lookup=null
        assertEquals("视频",ResourceKind.VIDEO.label)
        assertEquals("网页字号",SettingCatalog.find("textZoom")!!.title)
    }
}
