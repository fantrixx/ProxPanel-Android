package com.proxpanel.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.proxpanel.app.data.AppLanguage
import java.util.Locale

object LocaleHelper {
    private const val PREFS = "proxpanel_locale"
    private const val KEY = "app_language"

    /** Prefer a non-null context; applicationContext is null during Application.attachBaseContext. */
    private fun prefsContext(context: Context): Context =
        context.applicationContext ?: context

    fun current(context: Context): AppLanguage {
        val id = prefsContext(context)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return AppLanguage.fromId(id)
    }

    /** Persist for sync reads in attachBaseContext, then apply app-wide locales. */
    fun set(context: Context, language: AppLanguage) {
        prefsContext(context)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.id)
            .commit()
        applyDelegate(language)
    }

    fun applyDelegate(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.System -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.English -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.German -> LocaleListCompat.forLanguageTags("de")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun wrap(context: Context): Context {
        val language = current(context)
        if (language == AppLanguage.System) {
            return context
        }
        val locale = Locale.forLanguageTag(language.id)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
