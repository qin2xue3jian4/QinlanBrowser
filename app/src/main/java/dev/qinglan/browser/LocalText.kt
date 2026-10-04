package dev.qinglan.browser

/** Only explicit UI templates pass through here; arguments are never translated or reinterpreted. */
object LocalText {
    @Volatile var revision=0; private set
    @Volatile var lookup:((String)->String)?=null
        set(value){field=value;revision++}
    private val placeholder=Regex("""%(\d+)\${'$'}s""")
    fun render(source:String,vararg arguments:Any?):String {
        val template=lookup?.invoke(source)?:source
        return placeholder.replace(template){match->
            val index=match.groupValues[1].toInt()-1
            if(index in arguments.indices)arguments[index].toString()else match.value
        }
    }
}

fun tr(source:String,vararg arguments:Any?):String=LocalText.render(source,*arguments)

/** Pure matching policy, shared by old and new Android versions. */
object LanguageChoice {
    val supported=listOf("system","zh-Hans","zh-Hant","en")
    fun match(tag:String):String? {
        val locale=java.util.Locale.forLanguageTag(tag)
        return when(locale.language){
            "en"->"en"
            "zh"->if(locale.script=="Hant"||(locale.script!="Hans"&&locale.country in listOf("TW","HK","MO")))"zh-Hant"else"zh-Hans"
            else->null
        }
    }
    fun resolve(preferred:List<String>)=preferred.firstNotNullOfOrNull(::match)?:"en"
}
