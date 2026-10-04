package dev.qinglan.browser

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Native per-app locales on Android 13+, a local preference on Android 8–12. */
object AppLanguage {
    private const val PREFS="app_language"
    fun choice(context:Context):String {
        if(Build.VERSION.SDK_INT>=33){
            val locales=context.getSystemService(LocaleManager::class.java).applicationLocales
            return if(locales.isEmpty)"system"else LanguageChoice.match(locales[0].toLanguageTag())?:"en"
        }
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("choice","system")
            ?.takeIf{it in LanguageChoice.supported}?:"system"
    }
    fun effective(context:Context):String {
        val selected=choice(context)
        if(selected!="system")return selected
        val locales=if(Build.VERSION.SDK_INT>=33)context.getSystemService(LocaleManager::class.java).systemLocales else Resources.getSystem().configuration.locales
        return LanguageChoice.resolve((0 until locales.size()).map{locales[it].toLanguageTag()})
    }
    fun configuration(context:Context)=Configuration(context.resources.configuration).apply{
        val locale=Locale.forLanguageTag(effective(context))
        setLocales(LocaleList(locale));setLayoutDirection(locale)
    }
    fun wrap(context:Context):Context=context.createConfigurationContext(configuration(context))
    fun bind(context:Context){
        val resources=wrap(context.applicationContext).resources
        LocalText.lookup={source->TextResources.ids[source]?.let{resources.getString(it)}?:source}
    }
    fun set(activity:BrowserActivity,selected:String){
        require(selected in LanguageChoice.supported)
        if(selected==choice(activity))return
        if(Build.VERSION.SDK_INT>=33){
            activity.getSystemService(LocaleManager::class.java).applicationLocales=
                if(selected=="system")LocaleList.getEmptyLocaleList()else LocaleList.forLanguageTags(selected)
        }else{
            activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("choice",selected).apply()
        }
        activity.refreshLanguage()
    }
    fun names()=listOf(tr("跟随系统"),"简体中文","繁體中文","English")
}

class BrowserApplication:Application(){
    override fun onCreate(){super.onCreate();AppLanguage.bind(this)}
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);AppLanguage.bind(this)}
}
