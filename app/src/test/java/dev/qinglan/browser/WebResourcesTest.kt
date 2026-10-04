package dev.qinglan.browser

import org.junit.Assert.*
import org.junit.Test

class WebResourcesTest {
    @Test fun recognizesSignedAndExtensionlessResources() {
        assertEquals(ResourceKind.VIDEO,ResourceClassifier.classify("https://cdn.test/a.MP4?token=synthetic#start"))
        assertEquals(ResourceKind.AUDIO,ResourceClassifier.classify("https://cdn.test/stream?id=1","audio/mpeg; charset=binary"))
        assertEquals(ResourceKind.IMAGE,ResourceClassifier.classify("https://cdn.test/image/123",hint="img"))
        assertEquals(ResourceKind.HLS,ResourceClassifier.classify("https://cdn.test/get?id=1","application/vnd.apple.mpegurl"))
        assertEquals(ResourceKind.DASH,ResourceClassifier.classify("https://cdn.test/manifest.mpd"))
        assertEquals(ResourceKind.SEGMENT,ResourceClassifier.classify("https://cdn.test/part001.m4s"))
        assertNull(ResourceClassifier.classify("https://cdn.test/fake.mp4","text/html"))
        assertNull(ResourceClassifier.classify("https://cdn.test/?redirect=file.mp4"))
    }
    @Test fun unsafeAndBlobUrlsAreSeparated() {
        assertNull(ResourceClassifier.normalize("file:///data/a.mp4"))
        assertNull(ResourceClassifier.normalize("javascript:alert(1)"))
        assertNull(ResourceClassifier.normalize("https://user:secret@cdn.test/a.mp4"))
        assertNull(ResourceClassifier.normalize("https://cdn.test/a.mp4\r\nHeader: x"))
        assertEquals(ResourceKind.BLOB,ResourceClassifier.classify("blob:https://site.test/123",hint="video"))
        assertNull(ResourceClassifier.classify("blob:https://site.test/123"))
        assertNull(ResourceClassifier.normalize("blob:null/123"))
    }
    @Test fun deduplicatesFragmentsButPreservesSignedQueries() {
        val s=ResourceSession();s.start("https://site.test/page","Test UA")
        s.add(s.epoch,WebResource("https://cdn.test/a.mp4?token=1#t=1",ResourceKind.VIDEO))
        s.add(s.epoch,WebResource("https://cdn.test/a.mp4?token=1",ResourceKind.VIDEO,"video/mp4",s.page,s.userAgent,"页面元素"))
        s.add(s.epoch,WebResource("https://cdn.test/a.mp4?token=2",ResourceKind.VIDEO))
        assertEquals(2,s.list().size);assertEquals("video/mp4",s.list()[0].mime)
        assertEquals("https://site.test/page",s.list()[0].referer)
        assertEquals("页面元素",s.list()[0].source)
    }
    @Test fun navigationDropsOldGenerationAndTabsStayIsolated() {
        val a=ResourceSession();val b=ResourceSession();a.start("https://a.test/","UA-a");b.start("https://b.test/","UA-b")
        val old=a.epoch;val item=WebResource("https://cdn.test/a.mp4",ResourceKind.VIDEO)
        a.add(old,item);assertEquals(1,a.list().size);assertTrue(b.list().isEmpty())
        a.start("https://a.test/next","UA-a");a.add(old,item);assertTrue(a.list().isEmpty())
        b.add(b.epoch,item);assertEquals(1,b.list().size)
    }
    @Test fun boundedListKeepsMediaAheadOfImageFloods() {
        val s=ResourceSession();s.start("https://site.test/","UA")
        repeat(350){s.add(s.epoch,WebResource("https://cdn.test/image$it.jpg",ResourceKind.IMAGE))}
        assertEquals(300,s.list().size)
        s.add(s.epoch,WebResource("https://cdn.test/master.m3u8",ResourceKind.HLS));assertEquals(300,s.list().size)
        assertTrue(s.list().last().playlist)
        s.clear();assertTrue(s.list().isEmpty())
    }
    @Test fun originAndDocumentChecksIncludeSchemeAndPort() {
        assertTrue(ResourceClassifier.sameOrigin("https://a.test/p","https://a.test:443"))
        assertFalse(ResourceClassifier.sameOrigin("https://a.test:444/p","https://a.test"))
        assertFalse(ResourceClassifier.sameOrigin("http://a.test/p","https://a.test"))
        assertTrue(ResourceClassifier.sameDocument("https://a.test/p#1","https://a.test/p#2"))
        assertFalse(ResourceClassifier.sameDocument("https://a.test/old","https://a.test/new"))
    }
}
