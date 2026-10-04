package dev.qinglan.browser

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class FilterSubscription(val id: String, val name: String, val url: String, val enabled: Boolean = true) {
    fun json() = JSONObject().put("id", id).put("name", name).put("url", url).put("enabled", enabled)
}

class FilterSubscriptions(context: Context, private val store: BrowserStore) {
    private val directory = File(context.filesDir, "filters").apply { mkdirs() }
    private val executor = Executors.newSingleThreadExecutor()
    private val suffixes = PublicSuffixes(context.assets.open("public_suffix_list.dat").bufferedReader().use { it.readText() })
    private val busy = AtomicBoolean(false)
    @Volatile var engine = FilterEngine(emptyList(), suffixes); private set
    @Volatile var status = "正在读取本地规则"; private set
    @Volatile var details: Map<String, String> = emptyMap(); private set
    val updating get() = busy.get()
    init { rebuild() }
    fun entries() = parse(store.prefs.getString("filterSubscriptions", null) ?: encode(defaults))
    fun save(entries: List<FilterSubscription>) {
        val encoded = encode(entries); parse(encoded)
        store.prefs.edit().putString("filterSubscriptions", encoded).apply(); rebuild()
    }
    fun custom() = store.prefs.getString("filterRules", "").orEmpty()
    fun saveCustom(text: String) { require(text.length <= 65_536) { "自定义规则最多 64K 字符" }; store.prefs.edit().putString("filterRules", text).apply(); rebuild() }
    fun rebuild(done: (() -> Unit)? = null) { executor.execute { compile(); done?.invoke() } }
    private fun cacheKey(entry: FilterSubscription) = entry.id + "-" + java.security.MessageDigest.getInstance("SHA-256").digest(entry.url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    private fun file(entry: FilterSubscription) = AtomicFile(File(directory, "${cacheKey(entry)}.txt"))
    private fun compile() {
        val activeFiles = entries().map { file(it).baseFile.name }.toSet()
        directory.listFiles().orEmpty().filter { it.extension == "txt" && it.name !in activeFiles }.forEach { it.delete() }
        val texts = mutableListOf(custom()); val info = linkedMapOf<String, String>()
        entries().forEach { entry ->
            val text = runCatching { file(entry).openRead().bufferedReader().use { it.readText() } }.getOrDefault("")
            if (text.isEmpty()) info[entry.id] = "尚未下载"
            else {
                val parsed = FilterEngine(listOf(text), suffixes)
                val time = store.prefs.getLong("filterUpdated.${cacheKey(entry)}", 0)
                info[entry.id] = "可用 ${parsed.accepted} 条 · 跳过 ${parsed.skipped} 条" + if (time > 0) "\n更新于 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))}" else ""
                if (entry.enabled) texts.add(text)
            }
        }
        engine = FilterEngine(texts, suffixes); details = info
        status = "已加载 ${engine.accepted} 条规则 · 跳过 ${engine.skipped} 条不支持的规则"
    }
    fun update(automatic: Boolean = false, done: (() -> Unit)? = null) {
        if (automatic && (!store.prefs.getBoolean("adblockEnabled", true) || !store.prefs.getBoolean("adblockAutoUpdate", true))) return
        val now = System.currentTimeMillis()
        if (automatic && now - store.prefs.getLong("filterAttempt", 0) < 24 * 60 * 60 * 1000L) return
        if (!busy.compareAndSet(false, true)) return
        store.prefs.edit().putLong("filterAttempt", now).apply(); status = "正在更新规则…"
        executor.execute {
            val errors = mutableMapOf<String, String>()
            try {
                entries().filter { it.enabled }.forEach { entry ->
                    if (automatic && now - store.prefs.getLong("filterUpdated.${cacheKey(entry)}", 0) < 4 * 24 * 60 * 60 * 1000L) return@forEach
                    runCatching {
                        val text = fetch(entry.url)
                        require(!text.trimStart().startsWith('<'))
                        require(FilterEngine(listOf(text), suffixes).accepted > 0)
                        val atomic = file(entry)
                        val others = directory.listFiles().orEmpty().filter { it.extension == "txt" && it != atomic.baseFile }.sumOf { it.length() }
                        require(others + text.toByteArray().size <= 16_777_216)
                        val out = atomic.startWrite()
                        try { out.write(text.toByteArray()); atomic.finishWrite(out) } catch (e: Exception) { atomic.failWrite(out); throw e }
                        store.prefs.edit().putLong("filterUpdated.${cacheKey(entry)}", System.currentTimeMillis()).apply()
                    }.onFailure { errors[entry.id] = "更新失败，保留上次规则" }
                }
                compile()
                details = details.mapValues { (id, value) -> errors[id]?.let { "$it\n$value" } ?: value }
                if (errors.isNotEmpty()) status += " · ${errors.size} 个订阅更新失败"
            } finally { busy.set(false); done?.invoke() }
        }
    }
    fun close() { executor.shutdown() }
    companion object {
        val defaults = listOf(
            FilterSubscription("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt"),
            FilterSubscription("easylistchina", "EasyList China", "https://easylist-downloads.adblockplus.org/easylistchina.txt")
        )
        fun validUrl(url: String) = url.length <= 2048 && runCatching { val u = URI(url); u.scheme == "https" && !u.host.isNullOrBlank() && u.userInfo == null && u.fragment == null }.getOrDefault(false)
        fun encode(entries: List<FilterSubscription>) = JSONArray().apply { entries.forEach { put(it.json()) } }.toString()
        fun parse(text: String): List<FilterSubscription> {
            require(text.length <= 32_768); val a = JSONArray(text); require(a.length() <= 8) { "最多添加 8 个订阅" }
            return List(a.length()) { i -> val o = a.getJSONObject(i)
                FilterSubscription(o.getString("id"), o.getString("name"), o.getString("url"), o.getBoolean("enabled")).also {
                    require(it.id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && it.name.isNotBlank() && it.name.length <= 100 && validUrl(it.url)) { "订阅名称或 HTTPS 地址无效" }
                }
            }.also { require(it.map { s -> s.id }.distinct().size == it.size && it.map { s -> s.url }.distinct().size == it.size) { "订阅重复" } }
        }
        fun newEntry(name: String, url: String) = FilterSubscription(UUID.randomUUID().toString(), name.trim(), url.trim())
        /** Separate from WebView: no cookies, credentials or browsing headers are sent. */
        private fun fetch(address: String): String {
            var url = address
            repeat(5) {
                require(validUrl(url))
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false; connection.connectTimeout = 12_000; connection.readTimeout = 20_000
                    connection.setRequestProperty("User-Agent", "Qinglan-Filter-Updater/1.0")
                    val code = connection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) { url = URL(URL(url), connection.getHeaderField("Location") ?: error("No location")).toString(); return@repeat }
                    require(code == 200 && connection.contentLengthLong <= 8_388_608)
                    val bytes = connection.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 8_388_608); out.write(buffer, 0, n) }
                        out.toByteArray()
                    }
                    return bytes.toString(Charsets.UTF_8)
                } finally { connection.disconnect() }
            }
            error("Too many redirects")
        }
    }
}
