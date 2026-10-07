package dev.qinglan.browser

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WebDavSyncTest {
    private val config=WebDavConfig("https://dav.example.test/dav/","qa-user","synthetic-secret")
    private fun settings(theme:String="dark")=WebDavSettings.export(mapOf("theme" to theme,"site.example.test.js" to false),"system")
    private class Connection(val code:Int,val body:ByteArray=ByteArray(0),val tag:String?=null,val length:Long=body.size.toLong()):HttpURLConnection(URL("https://dav.example.test/dav/qinglan-settings.json")) {
        val output=ByteArrayOutputStream();var disconnected=false
        override fun connect(){}
        override fun disconnect(){disconnected=true}
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getInputStream()=ByteArrayInputStream(body)
        override fun getOutputStream()=output
        override fun getHeaderField(name:String)=if(name=="ETag")tag else null
        override fun getContentLengthLong()=length
    }
    private fun fails(block:()->Unit):Throwable {try{block();fail("Expected rejection")}catch(e:Exception){return e};error("unreachable")}
    private fun uploadClient(write:Connection,previous:WebDavClient.Remote?=null):WebDavClient {
        var reads=0
        return WebDavClient(config,connect={if(reads++==0){
            if(previous==null)Connection(404)else Connection(200,WebDavSettings.export(previous.snapshot.settings,previous.snapshot.language?:"system").toByteArray(),previous.etag)
        }else write})
    }
    @Test fun directoryAndCredentialsValidation() {
        assertEquals("https://dav.example.test/dav/",config.copy(directory=" https://dav.example.test/dav ").validated().directory)
        assertEquals("https://dav.example.test/",config.copy(directory="https://dav.example.test").validated().directory)
        assertEquals("https://dav.example.test/%E8%AE%BE%E7%BD%AE/",config.copy(directory="https://dav.example.test/设置/").validated().directory)
        for(url in listOf("http://dav.example.test/","https://user:pass@dav.example.test/","https://dav.example.test/?token=secret","https://dav.example.test/#fragment","file:///tmp/","https://dav.example.test:0/"))fails{config.copy(directory=url).validated()}
        fails{config.copy(username="user:pass").validated()};fails{config.copy(password="secret\r\n").validated()}
        assertFalse(config.toString().contains("synthetic-secret"))
    }
    @Test fun settingsRoundTripExcludesPrivateState() {
        val raw=WebDavSettings.export(mapOf("theme" to "dark","site.example.test.js" to false,"webdavPassword" to "private-value",
            "certificate.https://example.test" to "private-value","tabs" to "private-value","history" to "private-value","accounts" to "private-value"),"zh-Hant")
        assertFalse(raw.contains("private-value"));val restored=WebDavSettings.parse(raw)
        assertEquals("zh-Hant",restored.language);assertEquals(mapOf("theme" to "dark","site.example.test.js" to false),restored.settings)
        assertTrue(SettingCatalog.search("webdav").any{it.id=="webdav"})
        assertFalse(BackupCodec.allowed("webdav"))
    }
    @Test fun malformedOrWrongScopePayloadIsRejectedBeforeUse() {
        for(raw in listOf("<html>error</html>","{}",BackupCodec.export(null,null,emptyMap<String,Any>()),
            JSONObject(settings()).put("version",2).toString(),JSONObject(settings()).put("version",1.5).toString(),JSONObject(settings()).put("version","1").toString(),JSONObject(settings()).put("language","bad").toString(),
            JSONObject(settings()).put("passwords",org.json.JSONArray()).toString(),
            JSONObject(settings()).put("settings",JSONObject().put("theme","invalid")).toString()))fails{WebDavSettings.parse(raw)}
        assertTrue(WebDavSettings.parse(WebDavSettings.export(emptyMap<String,Any>(),"system")).settings.isEmpty())
        assertNull(WebDavSettings.parse(JSONObject(settings()).apply{remove("language")}.toString()).language)
    }
    @Test fun authenticatedReadUsesNoRedirectsAndAlwaysDisconnects() {
        val c=Connection(200,settings().toByteArray(),"\"v1\"")
        val client=WebDavClient(config,connect={c});val remote=client.download()!!
        assertEquals("dark",remote.snapshot.settings["theme"]);assertEquals("\"v1\"",remote.etag)
        assertEquals("GET",c.requestMethod);assertFalse(c.instanceFollowRedirects);assertFalse(c.useCaches)
        assertEquals(config.authorization(),c.getRequestProperty("Authorization"));assertNull(c.getRequestProperty("Cookie"))
        assertTrue(c.disconnected)
    }
    @Test fun firstUploadMustNotOverwriteAFileCreatedMeanwhile() {
        val c=Connection(201);uploadClient(c).upload(settings(),null)
        assertEquals("*",c.getRequestProperty("If-None-Match"));assertNull(c.getRequestProperty("If-Match"))
        assertEquals("PUT",c.requestMethod);assertEquals(settings(),c.output.toString("UTF-8"));assertTrue(c.disconnected)
        val conflict=Connection(412);assertTrue(fails{uploadClient(conflict).upload(settings(),null)} is WebDavFailure)
    }
    @Test fun replaceUsesStrongEtagAndRejectsStaleVersion() {
        val previous=WebDavClient.Remote(WebDavSettings.parse(settings()),"\"v1\"")
        val c=Connection(204);uploadClient(c,previous).upload(settings("light"),previous)
        assertEquals("\"v1\"",c.getRequestProperty("If-Match"));assertNull(c.getRequestProperty("If-None-Match"))
        val nutstore=Connection(204);uploadClient(nutstore,previous.copy(etag="opaque_unquoted-token")).upload(settings("light"),previous.copy(etag="opaque_unquoted-token"))
        assertEquals("opaque_unquoted-token",nutstore.getRequestProperty("If-Match"))
        val stale=Connection(412);assertTrue(fails{uploadClient(stale,previous).upload(settings("light"),previous)} is WebDavFailure)
        for(tag in listOf(null,"W/\"v1\"","*","\"v1\"\r\nX: y","\"v1\", \"v2\"")) {
            fails{WebDavClient(config,connect={error("Must not connect")}).upload(settings(),previous.copy(etag=tag))}
        }
    }
    @Test fun rechecksAfterConfirmationEvenIfServerIgnoresCreationCondition() {
        val c=Connection(200,settings("light").toByteArray(),"opaque-new-version")
        val client=WebDavClient(config,connect={c})
        assertTrue(fails{client.upload(settings(),null)} is WebDavFailure)
        assertEquals("GET",c.requestMethod);assertEquals(0,c.output.size())
        val old=WebDavClient.Remote(WebDavSettings.parse(settings()),"opaque-old-version")
        assertTrue(fails{client.upload(settings(),old)} is WebDavFailure)
        assertEquals(0,c.output.size())
    }
    @Test fun errorsDoNotConsumeOrExposeServerResponses() {
        for(code in listOf(301,302,307,308,401,403,409,429,500)) {
            val c=Connection(code,"server-secret".toByteArray())
            val e=fails{WebDavClient(config,connect={c}).download()}
            assertFalse(e.message.orEmpty().contains("server-secret"));assertTrue(c.disconnected)
        }
        assertNull(WebDavClient(config,connect={Connection(404)}).download())
    }
    @Test fun bothDeclaredAndStreamingOversizeAreRejected() {
        for(length in listOf(WebDavSettings.MAX_BYTES+1L,-1L)) {
            val c=Connection(200,ByteArray(WebDavSettings.MAX_BYTES+1),length=length)
            assertTrue(fails{WebDavClient(config,connect={c}).download()} is WebDavFailure);assertTrue(c.disconnected)
        }
        fails{WebDavSettings.parse(" ".repeat(WebDavSettings.MAX_BYTES+1))}
    }
    @Test fun invalidUploadNeverOpensAConnection() {
        fails{WebDavClient(config,connect={error("Must not connect")}).upload("bad",null)}
    }
}
