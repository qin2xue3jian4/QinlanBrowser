package dev.qinglan.browser

object MenuLayout {
    val all=listOf("bookmarks","history","downloads","find","collect","share","site","refresh","desktop","theme","images","fullscreen","filter","resources","speech","reader","readingList","qr","pageTop","pageBottom","print","cookies","undo","tabSearch","closeOtherTabs","accounts","incognito","tools","menuEditor","settings")
    val defaults=listOf("bookmarks","history","downloads","find","collect","share","site","incognito","tools","menuEditor","settings")
    fun parse(raw:String?):List<String> = if(raw==null)defaults else (raw.split(',').filter{it in all&&it!="settings"}.distinct()+"settings")
    fun move(items:List<String>,source:String,target:String):List<String> {
        if(source !in items||target !in items||source==target)return items
        val result=items.toMutableList();result.remove(source);result.add(result.indexOf(target),source);return result
    }
}
