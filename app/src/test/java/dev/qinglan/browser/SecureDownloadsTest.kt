package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream

class SecureDownloadsTest {
    @get:Rule val folder=TemporaryFolder()
    private class Connection(url:String,private val code:Int,private val location:String?=null):HttpURLConnection(URL(url)){
        override fun connect(){}
        override fun disconnect(){}
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getHeaderField(name:String)=if(name=="Location")location else null
        override fun getInputStream()=ByteArrayInputStream("fixture".toByteArray())
    }
    @Test fun downgradeRedirectIsRejectedBeforeAnyHttpRequest(){
        val requests=mutableListOf<String>();val file=folder.newFile()
        assertThrows(IllegalArgumentException::class.java){SecureDownloads.transfer("https://source.test/file","UA","https://source.test/page",{null},file){url->requests.add(url);Connection(url,302,"http://source.test/insecure")}}
        assertEquals(listOf("https://source.test/file"),requests);assertEquals(0L,file.length())
    }
    @Test fun redirectCookiesAreSelectedForDestinationAndRefererIsSameOrigin(){
        val cookies=mutableListOf<String>();val requests=mutableListOf<Connection>();val file=folder.newFile()
        SecureDownloads.transfer("https://source.test/file","UA","https://source.test/page",{url->cookies.add(url);if(url.contains("source.test"))"first=1"else"second=2"},file){url->Connection(url,if(requests.isEmpty())302 else 200,if(requests.isEmpty())"https://other.test/file"else null).also(requests::add)}
        assertEquals(listOf("https://source.test/file","https://other.test/file"),cookies)
        assertEquals("first=1",requests[0].getRequestProperty("Cookie"));assertEquals("second=2",requests[1].getRequestProperty("Cookie"))
        assertEquals("https://source.test/page",requests[0].getRequestProperty("Referer"));assertNull(requests[1].getRequestProperty("Referer"))
        assertEquals("fixture",file.readText())
    }
}
