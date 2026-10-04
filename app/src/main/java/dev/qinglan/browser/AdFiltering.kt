package dev.qinglan.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale

class FilterSession {
    @Volatile var page = ""
    private val hits = ArrayDeque<Pair<String, String>>()
    private var total = 0
    @Synchronized fun start(url: String) { page = url; hits.clear(); total = 0 }
    @Synchronized fun record(url: String, rule: String) { total++; hits.addFirst(url to rule); while (hits.size > 100) hits.removeLast() }
    @Synchronized fun count() = total
    @Synchronized fun logs() = hits.toList()
}

class AdFiltering(private val a: BrowserActivity) {
    val subscriptions = FilterSubscriptions(a, a.store)
    private var pickerWeb: WebView? = null
    fun enabled(url: String) = a.prefs.getBoolean("adblockEnabled", true) && a.store.siteBool(url, "adblock", true)
    fun intercept(session: FilterSession, request: WebResourceRequest): WebResourceResponse? {
        if (!enabled(session.page)) return null
        val url = request.url.toString()
        val rule = subscriptions.engine.blocked(FilterEngine.Request(url, session.page, type(request), request.isForMainFrame)) ?: return null
        session.record(url, rule)
        return WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }
    private fun type(r: WebResourceRequest): String? {
        val dest = r.requestHeaders.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) }?.value?.lowercase(Locale.ROOT)
        return when (dest) {
            "script", "image", "font" -> dest
            "style" -> "stylesheet"
            "audio", "video", "track" -> "media"
            "iframe", "frame" -> "subdocument"
            else -> null // WebView cannot always distinguish fetch/XHR/beacon; don't guess.
        }
    }
    fun attach(web: WebView, session: FilterSession) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "qinglanFilter", setOf("*")) { _, message, origin, main, reply ->
                if (FilterEngine.host(origin.toString()).isBlank()) return@addWebMessageListener
                val data = message.data.orEmpty()
                if (data == "styles") {
                    val selectors = if (enabled(session.page) && enabled(origin.toString())) subscriptions.engine.selectors(origin.toString()) else emptyList()
                    reply.postMessage(JSONArray(selectors).toString())
                } else if (main && pickerWeb === web && a.current?.web === web && data.startsWith("pick:") && data.length <= 1600 && FilterEngine.host(origin.toString()) == FilterEngine.host(session.page)) {
                    pickerWeb = null
                    val selector = data.removePrefix("pick:")
                    if (FilterEngine.validSelector(selector)) a.panels.filter.picked(session.page, selector)
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, a.assets.open("filter-page.js").bufferedReader().use { it.readText() }, setOf("*"))
            }
        }
    }
    fun finished(web: WebView, session: FilterSession) {
        web.evaluateJavascript(a.assets.open("filter-page.js").bufferedReader().use { it.readText() }, null)
        val selectors = if (enabled(session.page)) subscriptions.engine.selectors(session.page) else emptyList()
        web.evaluateJavascript("window.__qinglanApplyFilters && window.__qinglanApplyFilters(${JSONArray(selectors)});", null)
    }
    fun picker() {
        val web = a.current?.web
        if (web == null || !a.isHttp(a.currentUrl)) { a.toast(tr("请先打开网页")); return }
        if (!web.settings.javaScriptEnabled || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) { a.toast(tr("此功能需要 JavaScript 和较新的系统 WebView")); return }
        a.panels.pages.close()
        pickerWeb = web
        web.evaluateJavascript(a.assets.open("filter-picker.js").bufferedReader().use { it.readText() }.replace("__QINGLAN_PICKER_TIP__",org.json.JSONObject.quote(tr("点选要屏蔽的元素 · 点此取消"))), null)
    }
}
