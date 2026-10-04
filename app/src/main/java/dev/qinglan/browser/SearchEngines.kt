package dev.qinglan.browser

object SearchEngines {
    const val default="https://www.bing.com/search?q=%s"
    val presets=linkedMapOf("必应" to default,"百度" to "https://www.baidu.com/s?wd=%s","Google" to "https://www.google.com/search?q=%s","DuckDuckGo" to "https://duckduckgo.com/?q=%s","搜狗" to "https://www.sogou.com/web?query=%s","360 搜索" to "https://www.so.com/s?q=%s")
    fun valid(s:String)=s.length<=2048&&s.contains("%s")&&runCatching{val uri=java.net.URI(s.replace("%s","test"));uri.scheme in listOf("http","https")&&!uri.host.isNullOrBlank()&&uri.userInfo==null}.getOrDefault(false)
    fun name(s:String)=presets.entries.find{it.value==s}?.key?:"自定义"
}
