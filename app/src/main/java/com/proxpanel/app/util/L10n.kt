package com.proxpanel.app.util

import android.content.Context
import androidx.annotation.StringRes
import com.proxpanel.app.R
import com.proxpanel.app.data.AccentChoice
import com.proxpanel.app.data.RefreshInterval

/** Thin wrapper so ViewModels can resolve localized strings with the app locale. */
class L10n(context: Context) {
    private val app = context.applicationContext

    fun get(@StringRes id: Int): String = app.getString(id)

    fun get(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)
}

@StringRes
fun RefreshInterval.labelRes(): Int = when (this) {
    RefreshInterval.Fast -> R.string.refresh_interval_fast_label
    RefreshInterval.Default -> R.string.refresh_interval_default_label
    RefreshInterval.Relaxed -> R.string.refresh_interval_relaxed_label
    RefreshInterval.Calm -> R.string.refresh_interval_calm_label
    RefreshInterval.Slow -> R.string.refresh_interval_slow_label
    RefreshInterval.Manual -> R.string.refresh_interval_manual_label
}

@StringRes
fun RefreshInterval.hintRes(): Int = when (this) {
    RefreshInterval.Fast -> R.string.refresh_interval_fast_hint
    RefreshInterval.Default -> R.string.refresh_interval_default_hint
    RefreshInterval.Relaxed -> R.string.refresh_interval_relaxed_hint
    RefreshInterval.Calm -> R.string.refresh_interval_calm_hint
    RefreshInterval.Slow -> R.string.refresh_interval_slow_hint
    RefreshInterval.Manual -> R.string.refresh_interval_manual_hint
}

@StringRes
fun AccentChoice.labelRes(): Int = when (this) {
    AccentChoice.Orange -> R.string.accent_orange
    AccentChoice.Blue -> R.string.accent_blue
    AccentChoice.Teal -> R.string.accent_teal
    AccentChoice.Magenta -> R.string.accent_magenta
}
