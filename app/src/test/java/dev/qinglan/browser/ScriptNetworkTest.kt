package dev.qinglan.browser

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.*
import org.junit.Test

class ScriptNetworkTest {
    private fun script(connect:String="")=UserScript(source="// ==UserScript==\n// @name Request\n// @match https://example.com/*\n$connect\n// ==/UserScript==")
    @Test fun connectScopesDoNotAcceptLookalikeHostsOrProtocols(){val s=script("// @connect api.example.org");assertTrue(ScriptNetwork.allowed(s,"https://api.example.org/a","https://example.com/"));assertTrue(ScriptNetwork.allowed(s,"https://sub.api.example.org/a","https://example.com/"));assertTrue(ScriptNetwork.allowed(s,"https://example.com/a","https://example.com/"));listOf("https://api.example.org.evil.test/a","https://evil-example.org/a","file:///a","https://user:pass@api.example.org/a").forEach{assertFalse(ScriptNetwork.allowed(s,it,"https://example.com/"))}}
    @Test fun recognizesOnlyScriptPathsAndGreasyForkPages(){assertTrue(ScriptNetwork.isInstallUrl("https://update.greasyfork.org/scripts/1/code/name.user.js?version=2"));assertFalse(ScriptNetwork.isInstallUrl("https://evil.test/?file=x.user.js"));assertEquals("https://greasyfork.org/scripts/123/code/script.user.js",ScriptPanels.installUrl("https://greasyfork.org/zh-CN/scripts/123-example"));assertThrows(IllegalArgumentException::class.java){ScriptPanels.installUrl("https://evil.test/scripts/123-name")}}
    @Test fun verifiesLegacyAndModernIntegrityFormats(){val bytes="abc".toByteArray();val hex="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";val base64="ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=";listOf("sha256=$hex","sha256-$base64","md5=00000000;sha256=$base64","md5=900150983cd24fb0d6963f7d28e17f72").forEach{ScriptNetwork.verifyIntegrity("https://cdn.test/a.js#$it",bytes)};assertThrows(IllegalArgumentException::class.java){ScriptNetwork.verifyIntegrity("https://cdn.test/a.js#sha256=0000",bytes)}}
    @Test fun inlineDependenciesPreservePlusAndDecodePercentOrBase64(){assertEquals("window.Vue=Vue;+",ScriptNetwork.dependency("data:application/javascript,window.Vue%3DVue%3B+").text());assertEquals("abc",ScriptNetwork.dependency("data:text/plain;base64,YWJj").text());assertThrows(IllegalArgumentException::class.java){ScriptNetwork.dependency("data:text/plain,%GG")}}
    @Test fun redirectTargetsAreRecheckedAndResponsesBounded(){val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/ok"){ex->val data="synthetic".toByteArray();ex.responseHeaders.add("Content-Type","text/plain; charset=utf-8");ex.sendResponseHeaders(200,data.size.toLong());ex.responseBody.use{it.write(data)}}
        server.createContext("/redirect"){ex->ex.responseHeaders.add("Location","/ok");ex.sendResponseHeaders(302,-1);ex.close()}
        server.createContext("/large"){ex->ex.sendResponseHeaders(200,ScriptNetwork.MAX_RESPONSE.toLong()+1);ex.close()}
        server.start();val base="http://127.0.0.1:${server.address.port}"
        try{assertEquals("synthetic",ScriptNetwork.fetch("$base/redirect").text());assertThrows(IllegalArgumentException::class.java){ScriptNetwork.fetch("$base/redirect",allowed={!it.endsWith("/ok")})};assertThrows(IllegalArgumentException::class.java){ScriptNetwork.fetch("$base/large")};assertThrows(IllegalArgumentException::class.java){ScriptNetwork.fetch("$base/ok",headers=mapOf("Cookie" to "forbidden"))}}finally{server.stop(0)}
    }
}
