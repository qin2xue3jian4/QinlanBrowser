package dev.qinglan.browser
object BookmarkHtml {
    fun parse(html:String):List<Visit>{
        require(html.toByteArray().size<=1_048_576){tr("文件超过 1 MB")};val out=mutableListOf<Visit>();val parents=mutableListOf("");var pending=""
        fun text(s:String)=android.text.Html.fromHtml(s,0).toString().trim().take(1000)
        val tokens=Regex("<H3\\b[^>]*>([\\s\\S]*?)</H3>|<A\\b([^>]*)>([\\s\\S]*?)</A>|<(/?)DL\\b[^>]*>",RegexOption.IGNORE_CASE)
        for(m in tokens.findAll(html)){val token=m.value.lowercase();when{token.startsWith("<h3")-> {val f=Visit(text(m.groupValues[1]),"",folder=true);out.add(f);pending=f.id};token.startsWith("<a")-> {val url=Regex("href\\s*=\\s*[\"']([^\"']+)[\"']",RegexOption.IGNORE_CASE).find(m.groupValues[2])?.groupValues?.get(1)?.let(::text).orEmpty();if(runCatching{val u=java.net.URI(url);u.scheme in listOf("http","https")&&u.host!=null&&u.userInfo==null}.getOrDefault(false))out.add(Visit(text(m.groupValues[3]),url,parent=parents.last()))};token.startsWith("</dl")->if(parents.size>1)parents.removeAt(parents.lastIndex);else->{parents.add(pending.ifBlank{parents.last()});pending=""}};require(out.size<=10000){tr("书签过多")}}
        return out
    }
}
