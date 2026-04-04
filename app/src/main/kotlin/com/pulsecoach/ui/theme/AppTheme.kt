package com.pulsecoach.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The three selectable app themes shown in Settings > Appearance. */
enum class AppTheme(val displayName: String) {
    DEFAULT("Default"),
    DARK("Dark"),
    SYNTHWAVE("Synthwave")
}

/**
 * Returns the Material3 [ColorScheme] for this theme.
 * Called in MainActivity to wrap the entire composition tree.
 */
val AppTheme.colorScheme: ColorScheme
    get() = when (this) {
        AppTheme.DEFAULT   -> DefaultColorScheme
        AppTheme.DARK      -> DarkColorScheme
        AppTheme.SYNTHWAVE -> SynthwaveColorScheme
    }

// ---------------------------------------------------------------------------
// Default — clean light theme, blue primary, close to the original app look
// ---------------------------------------------------------------------------
private val DefaultColorScheme = lightColorScheme(
    primary              = Color(0xFF1565C0),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFD0E4FF),
    onPrimaryContainer   = Color(0xFF001D36),
    secondary            = Color(0xFF0277BD),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFCDE5FF),
    onSecondaryContainer = Color(0xFF001D31),
    background           = Color(0xFFF8F9FF),
    onBackground         = Color(0xFF1A1B1E),
    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF1A1B1E),
    surfaceVariant       = Color(0xFFE3E8F0),
    onSurfaceVariant     = Color(0xFF44474F),
    error                = Color(0xFFBA1A1A),
    onError              = Color(0xFFFFFFFF),
    outline              = Color(0xFF74777F),
)

// ---------------------------------------------------------------------------
// Dark — deep neutral backgrounds, soft blue primary, clinical dashboard feel
// ---------------------------------------------------------------------------
private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFF90CAF9),
    onPrimary            = Color(0xFF003258),
    primaryContainer     = Color(0xFF004880),
    onPrimaryContainer   = Color(0xFFD0E4FF),
    secondary            = Color(0xFF80CBC4),
    onSecondary          = Color(0xFF00332F),
    secondaryContainer   = Color(0xFF004A45),
    onSecondaryContainer = Color(0xFF9EF0E8),
    background           = Color(0xFF0F1117),
    onBackground         = Color(0xFFE8EAED),
    surface              = Color(0xFF1A1D23),
    onSurface            = Color(0xFFE8EAED),
    surfaceVariant       = Color(0xFF252830),
    onSurfaceVariant     = Color(0xFF9AA0B0),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    outline              = Color(0xFF555A66),
)

// ---------------------------------------------------------------------------
// Synthwave — deep indigo-black, neon magenta primary, electric cyan secondary
// Late-80s Outrun / Retrowave aesthetic
// ---------------------------------------------------------------------------
private val SynthwaveColorScheme = darkColorScheme(
    primary              = Color(0xFFFF2D78),
    onPrimary            = Color(0xFF5C0021),
    primaryContainer     = Color(0xFF8C0038),
    onPrimaryContainer   = Color(0xFFFFD9E2),
    secondary            = Color(0xFF00E5FF),
    onSecondary          = Color(0xFF003739),
    secondaryContainer   = Color(0xFF005456),
    onSecondaryContainer = Color(0xFF97F8FF),
    tertiary             = Color(0xFFBF00FF),
    onTertiary           = Color(0xFF2D0040),
    tertiaryContainer    = Color(0xFF4A006B),
    onTertiaryContainer  = Color(0xFFEAB8FF),
    background           = Color(0xFF0D0221),
    onBackground         = Color(0xFFF0E6FF),
    surface              = Color(0xFF1A0533),
    onSurface            = Color(0xFFF0E6FF),
    surfaceVariant       = Color(0xFF2D0B4E),
    onSurfaceVariant     = Color(0xFFB8A4D4),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    outline              = Color(0xFF6B3FA0),
)
