package dev.qinglan.browser

object Palette {
    val names get()=linkedMapOf("green" to tr("青绿"),"blue" to tr("海蓝"),"purple" to tr("紫藤"),"amber" to tr("暖金"),"rose" to tr("玫瑰"),"teal" to tr("湖水"),"orange" to tr("日落"),"slate" to tr("石墨"),"custom" to tr("自定义"))
    val colors=mapOf("green" to "#286658","blue" to "#285B98","purple" to "#70489A","amber" to "#855C20","rose" to "#A33860","teal" to "#087E8B","orange" to "#B65220","slate" to "#526477")
    fun valid(s:String)=Regex("#[0-9a-fA-F]{6}").matches(s)
}
