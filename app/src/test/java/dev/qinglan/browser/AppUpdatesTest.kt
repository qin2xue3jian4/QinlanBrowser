package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdatesTest {
    private fun payload(tag:String="v0.12.0")=JSONObject().put("tag_name",tag).put("html_url","${ProjectLinks.releases}/tag/$tag")
        .put("draft",false).put("prerelease",false).put("name","Release $tag").put("body","Synthetic notes")
        .put("assets",JSONArray().put(JSONObject().put("name","qinglan-${tag.removePrefix("v")}.apk").put("state","uploaded")
            .put("browser_download_url","${ProjectLinks.releases}/download/$tag/qinglan-${tag.removePrefix("v")}.apk")))
    private fun version(s:String)=requireNotNull(AppVersion.parse(s))
    private fun rejects(block:()->Unit){try{block();fail("Expected rejection")}catch(e:Exception){}}
    @Test fun semanticVersionOrderingAndStablePromotion() {
        val versions=listOf("0.9.0","0.10.0","0.11.3","0.12.0-alpha","0.12.0-alpha.2","0.12.0-alpha.10","0.12.0-beta","0.12.0-rc.1","0.12.0","1.0.0")
        versions.zipWithNext().forEach{(a,b)->assertTrue("$a < $b",version(a)<version(b))}
        assertEquals(0,version("v1.0.0+build.1").compareTo(version("1.0.0+build.2")))
        for(s in listOf("1.0","01.0.0","1.0.0-01","1.0.0-","latest","1.0.0 /url","9999999999999999999999.0.0"))assertNull(AppVersion.parse(s))
    }
    @Test fun onlyPublishedStableReleasesWithOfficialApkAreOffered() {
        val release=AppRelease.parse(payload().toString())!!
        assertTrue(release.newerThan("0.11.3"));assertFalse(release.newerThan("0.13.0"));assertFalse(release.newerThan("invalid"))
        assertEquals(release,AppRelease.parse(release.cache()))
        assertNull(AppRelease.parse(payload().put("prerelease",true).toString()))
        assertNull(AppRelease.parse(payload().put("draft",true).toString()))
        assertNull(AppRelease.parse(payload("v0.12.0-beta.1").toString()))
        assertNull(AppRelease.parse(payload().put("assets",JSONArray()).toString()))
        rejects{AppRelease.parse(payload().put("html_url","https://github.com.evil.test/releases/v0.12.0").toString())}
        val wrong=payload();wrong.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://example.test/fake.apk")
        assertNull(AppRelease.parse(wrong.toString()))
    }
    @Test fun releaseUiTagsWithoutVKeepTheirExactOfficialDownloadPage() {
        val release=AppRelease.parse(payload("1.0.0").toString())!!
        assertEquals("1.0.0",release.tag);assertEquals("${ProjectLinks.releases}/tag/1.0.0",release.page)
        assertTrue(release.newerThan("0.11.3"));assertEquals(release,AppRelease.parse(release.cache()))
        assertFalse(release.newerThan("1.0.0"))
    }
    @Test fun dailyChecksHandleFailuresAndClockChangesWithoutRequestLoops() {
        val now=UpdatePolicy.INTERVAL*10
        assertTrue(UpdatePolicy.due(now,0));assertFalse(UpdatePolicy.due(now,now))
        assertFalse(UpdatePolicy.due(now,now-UpdatePolicy.INTERVAL+1));assertTrue(UpdatePolicy.due(now,now-UpdatePolicy.INTERVAL))
        assertTrue(UpdatePolicy.due(now,now+1))
    }
    private class Connection(val code:Int,val bytes:ByteArray=ByteArray(0),val length:Long=bytes.size.toLong()):HttpURLConnection(URL(ProjectLinks.latestApi)) {
        var disconnected=false
        override fun connect(){}
        override fun disconnect(){disconnected=true}
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getInputStream()=ByteArrayInputStream(bytes)
        override fun getContentLengthLong()=length
    }
    @Test fun anonymousRequestUsesFixedSourceNoBrowserCredentialsAndDisconnects() {
        val c=Connection(200,payload().toString().toByteArray())
        assertEquals("0.12.0",GitHubUpdates.latest{url->assertEquals(ProjectLinks.latestApi,url.toString());c}!!.version.name)
        assertFalse(c.instanceFollowRedirects);assertFalse(c.useCaches)
        assertNull(c.getRequestProperty("Authorization"));assertNull(c.getRequestProperty("Cookie"));assertTrue(c.disconnected)
    }
    @Test fun noReleaseRateLimitRedirectAndMalformedResponsesAreDistinct() {
        assertNull(GitHubUpdates.latest{Connection(404)})
        for(code in listOf(301,302,403,429,500)) {
            val c=Connection(code,"private server message".toByteArray())
            val failure=runCatching{GitHubUpdates.latest{c}}.exceptionOrNull()
            assertTrue(failure is UpdateFailure);assertFalse(failure!!.message.orEmpty().contains("private server"));assertTrue(c.disconnected)
        }
        assertTrue(runCatching{GitHubUpdates.latest{Connection(200,"<html>bad</html>".toByteArray())}}.exceptionOrNull() is UpdateFailure)
    }
    @Test fun responseLimitAppliesToDeclaredAndChunkedBodies() {
        for(length in listOf(GitHubUpdates.MAX_BYTES+1L,-1L)) {
            val c=Connection(200,ByteArray(GitHubUpdates.MAX_BYTES+1),length)
            assertTrue(runCatching{GitHubUpdates.latest{c}}.exceptionOrNull() is UpdateFailure);assertTrue(c.disconnected)
        }
    }
    @Test fun updateSettingsParticipateInSearchAndSafeBackups() {
        assertTrue(SettingCatalog.search("autoCheckUpdates").any{it.id=="autoCheckUpdates"})
        val raw=BackupCodec.export(null,null,mapOf("autoCheckUpdates" to false,"app_updates" to "private-state","updates.attempt" to 123L))
        assertEquals(mapOf("autoCheckUpdates" to false),BackupCodec.parse(raw).settings)
        assertFalse(raw.contains("private-state"));assertFalse(BackupCodec.allowed("updates"))
    }
}
