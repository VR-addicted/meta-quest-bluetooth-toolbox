package com.quest.systemsettings.localization

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val flag: String, val label: String) {
    EN("en", "🇬🇧", "EN"),
    DE("de", "🇩🇪", "DE")
}

object LanguageManager {
    private const val PREFS_NAME = "quest_settings_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    var currentLanguage by mutableStateOf(AppLanguage.EN)
        private set

    val isGerman: Boolean
        get() = currentLanguage == AppLanguage.DE

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.EN.code)
        currentLanguage = if (saved == AppLanguage.DE.code) AppLanguage.DE else AppLanguage.EN
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        currentLanguage = language
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }
}
