package dev.qinglan.browser

import java.net.IDN
import java.net.URI
import java.util.Locale

/** Pure matcher. Unsupported options reject the whole rule rather than broadening it. */
class FilterEngine(texts: List<String>, private val suffixes: PublicSuffixes = PublicSuffixes("")) {
    data class Request(val url: String, val page: String, val type: String? = null, val mainFrame: Boolean = false)
    data class Cosmetic(val domains: List<String>, val selector: String, val exception: Boolean)
    data class Rule(val raw: String, val pattern: String, val exception: Boolean, val domains: List<String>,
                    val types: Set<String>, val excludedTypes: Set<String>, val thirdParty: Boolean?, val matchCase: Boolean) {
        fun matches(request: Request, suffixes: PublicSuffixes): Boolean {
            if (!domainMatches(domains, host(request.page))) return false
            if ((types.isNotEmpty() || excludedTypes.isNotEmpty()) && request.type == null) return false
            if (types.isNotEmpty() && request.type !in types || request.type in excludedTypes) return false
            if (thirdParty != null) {
                val page = host(request.page); val target = host(request.url)
                if (page.isBlank() || target.isBlank() || (suffixes.site(page) != suffixes.site(target)) != thirdParty) return false
            }
            return matchesPattern(pattern, request.url, matchCase)
        }
    }
    val rules: List<Rule>
    val cosmetic: List<Cosmetic>
    val skipped: Int
    val accepted get() = rules.size + cosmetic.size
    private val index: Map<String, List<Rule>>
    private val fallback: List<Rule>
    init {
        val network = mutableListOf<Rule>(); val hides = mutableListOf<Cosmetic>(); var ignored = 0
        val seen = HashSet<String>()
        texts.forEach { text -> text.lineSequence().take(200_000).forEach line@{ line ->
            val raw = line.trim().removePrefix("\uFEFF")
            if (raw.isBlank() || raw.startsWith('!') || raw.startsWith('[')) return@line
            if (network.size + hides.size >= 150_000) { ignored++; return@line }
            if (!seen.add(raw)) return@line
            if (raw.length > 2048) { ignored++; return@line }
            val delimiter = if (raw.contains("#@#")) "#@#" else if (raw.contains("##")) "##" else ""
            if (delimiter.isNotEmpty()) {
                val domains = raw.substringBefore(delimiter).split(',').filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
                val selector = raw.substringAfter(delimiter)
                if (domains.all(::validDomain) && validSelector(selector)) hides.add(Cosmetic(domains, selector, delimiter == "#@#")) else ignored++
            } else {
                val rule = parseRule(raw)
                if (rule == null) ignored++ else network.add(rule)
            }
        } }
        rules = network; cosmetic = hides; skipped = ignored
        index = network.filter { key(it.pattern).isNotEmpty() }.groupBy { key(it.pattern) }
        fallback = network.filter { key(it.pattern).isEmpty() }
    }
    fun blocked(request: Request): String? {
        // Never replace the document itself; page navigation belongs to WebView.
        if (request.mainFrame || request.url.length > 8192 || host(request.url).isBlank()) return null
        val url = request.url.lowercase(Locale.ROOT)
        val keys = HashSet<String>()
        for (i in 0..url.length - 5) keys.add(url.substring(i, i + 5))
        var blocked: String? = null
        for (rule in sequence { yieldAll(fallback); keys.forEach { yieldAll(index[it].orEmpty()) } }) {
            if (rule.matches(request, suffixes)) { if (rule.exception) return null; blocked = rule.raw }
        }
        return blocked
    }
    fun selectors(url: String): List<String> {
        val host = host(url)
        if (host.isBlank()) return emptyList()
        val matches = cosmetic.filter { domainMatches(it.domains, host) }
        val exceptions = matches.filter { it.exception }.map { it.selector }.toSet()
        return matches.filter { !it.exception && it.selector !in exceptions }.map { it.selector }.distinct()
    }
    companion object {
        private val types = setOf("script", "image", "stylesheet", "font", "media", "subdocument", "xmlhttprequest", "other")
        fun host(url: String): String = runCatching { URI(url).takeIf { it.scheme in listOf("http", "https") && it.rawUserInfo == null }?.host?.lowercase(Locale.ROOT)?.trimEnd('.').orEmpty() }.getOrDefault("")
        private val keyPattern = Regex("[a-z0-9%]{5,}")
        private fun key(pattern: String) = keyPattern.findAll(pattern.lowercase(Locale.ROOT)).maxByOrNull { it.value.length }?.value?.take(5).orEmpty()
        private fun validDomain(value: String) = value.removePrefix("~").matches(Regex("[a-z0-9_\\-]+(?:\\.[a-z0-9_\\-]+)*"))
        fun domainMatches(domains: List<String>, host: String): Boolean {
            if (host.isBlank()) return domains.isEmpty()
            fun within(d: String) = host == d || host.endsWith(".$d")
            if (domains.any { it.startsWith('~') && within(it.drop(1)) }) return false
            return domains.none { !it.startsWith('~') } || domains.any { !it.startsWith('~') && within(it) }
        }
        fun validSelector(s: String): Boolean = s.isNotBlank() && s.length <= 1500 &&
            s.none { it in "{}\\\n\r\u0000" } && !s.contains("/*") && !s.contains("+js(") &&
            !s.contains(Regex(":(?:has-text|matches|xpath|upward|remove|style|contains|not-if|if)(?:\\(|:)")) && !s.contains(":-abp-")
        private fun parseRule(raw: String): Rule? {
            if (raw.any { it.isWhitespace() } || raw.contains('#')) return null
            val exception = raw.startsWith("@@"); val body = raw.removePrefix("@@")
            val pattern = body.substringBefore('$'); if (pattern.isBlank() || pattern.startsWith('/') && pattern.endsWith('/') || pattern.count { it == '*' } > 16) return null
            if ('|' in pattern.removePrefix("||").removePrefix("|").removeSuffix("|")) return null
            val domains = mutableListOf<String>(); val include = mutableSetOf<String>(); val exclude = mutableSetOf<String>()
            var party: Boolean? = null; var matchCase = false
            val options = if ('$' in body) body.substringAfter('$').split(',') else emptyList()
            for (option in options) when {
                option == "third-party" || option == "~third-party" -> party = option == "third-party"
                option == "match-case" -> matchCase = true
                option.startsWith("domain=") -> { val ds = option.substringAfter('=').lowercase(Locale.ROOT).split('|'); if (ds.any { !validDomain(it) }) return null; domains.addAll(ds) }
                option in types -> include.add(option)
                option.startsWith('~') && option.drop(1) in types -> exclude.add(option.drop(1))
                else -> return null
            }
            return Rule(raw, pattern, exception, domains, include, exclude, party, matchCase)
        }
        fun matchesPattern(pattern: String, url: String, matchCase: Boolean = false): Boolean {
            val p = if (matchCase) pattern else pattern.lowercase(Locale.ROOT)
            val value = if (matchCase) url else url.lowercase(Locale.ROOT)
            val end = p.endsWith('|'); val tail = if (end) "" else "*"
            if (p.startsWith("||")) {
                val start = value.indexOf("://").takeIf { it > 0 }?.plus(3) ?: return false
                val host = host(url); if (host.isBlank()) return false
                val body = p.drop(2).removeSuffix("|") + tail
                // Only host-label boundaries, never a domain-looking string in a path/query.
                return (listOf(start) + host.indices.filter { host[it] == '.' }.map { start + it + 1 }).any { glob(body, value.substring(it)) }
            }
            val body = (if (p.startsWith('|')) "" else "*") + p.removePrefix("|").removeSuffix("|") + tail
            return glob(body, value)
        }
        private fun glob(pattern: String, value: String): Boolean {
            var p = 0; var v = 0; var star = -1; var retry = 0
            fun separator(c: Char) = !(c.isLetterOrDigit() || c in "_%.-")
            while (v < value.length) {
                when {
                    p < pattern.length && pattern[p] == '*' -> { star = p++; retry = v }
                    p < pattern.length && (pattern[p] == value[v] || pattern[p] == '^' && separator(value[v])) -> { p++; v++ }
                    star >= 0 -> { p = star + 1; v = ++retry }
                    else -> return false
                }
            }
            while (p < pattern.length && pattern[p] in "*^") p++
            return p == pattern.length
        }
    }
}

/** PSL wildcard and exception rules, including private suffixes (e.g. github.io). */
class PublicSuffixes(text: String) {
    private val rules = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
        .mapNotNull { line -> runCatching { line.split('.').joinToString(".") { if (it == "*") it else (if (it.startsWith('!')) "!" else "") + IDN.toASCII(it.removePrefix("!"), IDN.ALLOW_UNASSIGNED) }.lowercase(Locale.ROOT) }.getOrNull() }.toSet()
    fun site(host: String): String {
        if (host.contains(':') || host.matches(Regex("[0-9.]+"))) return host
        val labels = host.split('.'); var suffix = 1
        for (i in labels.indices) {
            val candidate = labels.drop(i).joinToString(".")
            if ("!$candidate" in rules) return labels.takeLast(labels.size - i).joinToString(".")
            if (candidate in rules || i > 0 && "*.$candidate" in rules) suffix = maxOf(suffix, labels.size - i + if (i > 0 && "*.$candidate" in rules) 1 else 0)
        }
        return labels.takeLast((suffix + 1).coerceAtMost(labels.size)).joinToString(".")
    }
}
