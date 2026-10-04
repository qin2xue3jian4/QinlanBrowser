package dev.qinglan.browser

object Palette {
    val names=linkedMapOf("green" to "青绿","blue" to "海蓝","purple" to "紫藤","amber" to "暖金","rose" to "玫瑰","teal" to "湖水","orange" to "日落","slate" to "石墨","custom" to "自定义")
    val colors=mapOf("green" to "#286658","blue" to "#285B98","purple" to "#70489A","amber" to "#855C20","rose" to "#A33860","teal" to "#087E8B","orange" to "#B65220","slate" to "#526477")
    fun valid(s:String)=Regex("#[0-9a-fA-F]{6}").matches(s)
}
