package com.pulsecoach.repository

import android.content.Context
import com.pulsecoach.ui.theme.AppTheme

/**
 * Persists the user's selected [AppTheme] in SharedPreferences.
 * Reads and writes are synchronous — the preference is a single string enum name.
 */
class ThemeRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the saved theme, defaulting to [AppTheme.DEFAULT] if none is set. */
    fun getTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.DEFAULT.name)
        // entries.find with a fallback guards against stale enum names across app versions
        return AppTheme.entries.find { it.name == name } ?: AppTheme.DEFAULT
    }

    /** Persists the selected theme. Change is applied immediately on next recomposition. */
    fun saveTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "pulse_coach_theme"
        private const val KEY_THEME  = "selected_theme"
    }
}
