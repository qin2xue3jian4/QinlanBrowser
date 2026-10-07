package dev.qinglan.browser

object MenuLayout {
    val all=listOf("bookmarks","history","downloads","find","collect","share","site","refresh","desktop","ua","source","screenshot","exit","theme","images","fullscreen","filter","resources","speech","reader","readingList","qr","pageTop","pageBottom","print","cookies","undo","tabSearch","closeOtherTabs","accounts","incognito","tools","menuEditor","settings")
    const val defaultColumns=5
    val defaults=listOf("bookmarks","history","downloads","find","collect","share","site","tools","menuEditor","incognito","accounts","cookies","qr","reader","speech","filter","fullscreen","images","theme","desktop","ua","source","screenshot","exit","settings")
    fun parse(raw:String?):List<String> = if(raw==null)defaults else (raw.split(',').filter{it in all&&it!="settings"}.distinct()+"settings")
    fun move(items:List<String>,source:String,target:String):List<String> {
        if(source !in items||target !in items||source==target)return items
        val result=items.toMutableList();result.remove(source);result.add(result.indexOf(target),source);return result
    }
}
