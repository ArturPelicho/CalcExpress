package com.arturpelicho.calcexpress

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arturpelicho.calcexpress.core.AngleUnit
import com.arturpelicho.calcexpress.core.UnitSystem
import java.util.Locale

enum class ThemeChoice { CLEAR, DARK, SYSTEM }
enum class FontChoice { LATEX, SANS }

/** App languages; until one is chosen the phone's language is used when supported, else en-GB. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    EN_GB("en-GB", "English (UK)"),
    PT_PT("pt-PT", "Português (Portugal)"),
    FR("fr-FR", "Français"),
    DE("de-DE", "Deutsch"),
    IT("it-IT", "Italiano"),
    ES("es-ES", "Castellano");

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val code: String get() = tag.substring(0, 2)

    companion object {
        /** The supported language matching the phone's language, or en-GB. */
        fun fromSystem(): AppLanguage {
            @Suppress("DEPRECATION")
            val system = android.content.res.Resources.getSystem().configuration.locale?.language
            return entries.firstOrNull { it.code == system } ?: EN_GB
        }
    }
}

/** Preferences backed by SharedPreferences and exposed as Compose state. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme by mutableStateOf(enumPref("theme", ThemeChoice.CLEAR))
        private set
    var font by mutableStateOf(enumPref("font", FontChoice.LATEX))
        private set
    var units by mutableStateOf(enumPref("units", UnitSystem.METRIC))
        private set
    var language by mutableStateOf(enumPref("language", AppLanguage.fromSystem()))
        private set
    var angle by mutableStateOf(enumPref("angle", AngleUnit.DEG))
        private set
    var greek by mutableStateOf(prefs.getBoolean("greek", false))
        private set
    var autoUpdateCheck by mutableStateOf(prefs.getBoolean("auto_update_check", true))
        private set

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun updateTheme(v: ThemeChoice) { theme = v; put("theme", v.name) }
    fun updateFont(v: FontChoice) { font = v; put("font", v.name) }
    fun updateUnits(v: UnitSystem) { units = v; put("units", v.name) }
    fun updateLanguage(v: AppLanguage) { language = v; put("language", v.name) }
    fun updateAngle(v: AngleUnit) { angle = v; put("angle", v.name) }
    fun updateGreek(v: Boolean) { greek = v; prefs.edit().putBoolean("greek", v).apply() }
    fun updateAutoUpdateCheck(v: Boolean) { autoUpdateCheck = v; prefs.edit().putBoolean("auto_update_check", v).apply() }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0)
        set(v) = prefs.edit().putLong("last_update_check", v).apply()

    companion object {
        /** Wraps [base] so resources use the chosen app language. */
        fun localized(base: Context): Context {
            val name = base.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", null)
            val lang = AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.fromSystem()
            Locale.setDefault(lang.locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(lang.locale)
            return base.createConfigurationContext(config)
        }
    }
}
