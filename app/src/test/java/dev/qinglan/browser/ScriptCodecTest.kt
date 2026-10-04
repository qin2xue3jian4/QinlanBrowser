package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class ScriptCodecTest {
    private fun script(extra:String="",match:String="*://*.example.com/*")=UserScript(source="// ==UserScript==\n// @name Test\n// @match $match\n$extra\n// ==/UserScript==\ndocument.title='ok';")
    @Test fun matchesExactHostsSubdomainsAndHttpOnly(){val s=script();assertTrue(s.accepts("https://example.com/x"));assertTrue(s.accepts("http://a.example.com/x"));assertFalse(s.accepts("https://evil-example.com/x"));assertFalse(s.accepts("https://example.com.evil.test/x"));assertFalse(s.accepts("file://example.com/x"))}
    @Test fun excludesAndEscapesRegexCharacters(){val s=script("// @exclude *://*/private*", "https://example.com/a.b?x=*");assertTrue(s.accepts("https://example.com/a.b?x=1#hash"));assertFalse(s.accepts("https://example.com/axb?x=1"));val e=script("// @exclude-match https://example.com/private/*");assertFalse(e.accepts("https://example.com/private/a"));assertTrue(e.accepts("https://example.com/public/a"))}
    @Test fun includeAndExcludedGlob(){val s=UserScript(source="// ==UserScript==\n// @name Glob\n// @include https://*.example.org/*\n// @exclude *://*/private/*\n// ==/UserScript==\n");assertTrue(s.accepts("https://www.example.org/a"));assertFalse(s.accepts("https://www.example.org/private/a"))}
    @Test fun rejectsUnsupportedGrantsAndDependencies(){listOf("// @grant GM_xmlhttpRequest","// @grant GM_setValue","// @require https://x.test/lib.js","// @resource css https://x.test/a.css","// @run-at context-menu").forEach{assertThrows(IllegalArgumentException::class.java){script(it)}}}
    @Test fun rejectsMissingOrDangerousPatterns(){assertThrows(IllegalArgumentException::class.java){script(match="file:///*")};assertThrows(IllegalArgumentException::class.java){script(match="https://*evil.test/*")};assertThrows(IllegalArgumentException::class.java){UserScript(source="alert(1)")}}
    @Test fun preservesSourceAndSupportedMetadata(){val s=script("// @grant GM_addStyle\n// @run-at document-start");assertEquals("document-start",s.runAt);assertTrue(ScriptCodec.javascript(s).contains("DOMContentLoaded").not());assertTrue(ScriptCodec.javascript(s).contains(s.source));assertFalse(s.enabled)}
}
