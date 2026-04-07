package com.example.toplutasima.ui

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage { TR, DE, EN }

object LocaleManager {
    var currentLanguage by mutableStateOf(AppLanguage.TR)
        private set

    fun init(prefs: SharedPreferences) {
        val saved = prefs.getString("app_language", "TR") ?: "TR"
        currentLanguage = try { AppLanguage.valueOf(saved) } catch (_: Exception) { AppLanguage.TR }
    }

    fun setLanguage(lang: AppLanguage, prefs: SharedPreferences) {
        currentLanguage = lang
        prefs.edit().putString("app_language", lang.name).apply()
    }
}
