package com.twisham.attendance.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * A deep green was chosen as the seed rather than the Material default purple: the
 * app's entire vocabulary is green/amber/red status, so a green primary keeps the
 * chrome and the data speaking the same language. Dynamic colour is still preferred
 * on Android 12+ — these are the fallback for everything older, and the source of
 * the status colours, which are *never* taken from the dynamic palette because their
 * meaning must not shift with the wallpaper.
 */

// --- Light -------------------------------------------------------------------
val LightPrimary = Color(0xFF1B6C4B)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFA5F2C8)
val LightOnPrimaryContainer = Color(0xFF002114)
val LightSecondary = Color(0xFF4C6358)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCEE9DA)
val LightOnSecondaryContainer = Color(0xFF092017)
val LightTertiary = Color(0xFF3D6473)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC1E9FB)
val LightOnTertiaryContainer = Color(0xFF001F29)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFFBFDF9)
val LightOnBackground = Color(0xFF191C1A)
val LightSurface = Color(0xFFFBFDF9)
val LightOnSurface = Color(0xFF191C1A)
val LightSurfaceVariant = Color(0xFFDCE5DD)
val LightOnSurfaceVariant = Color(0xFF404943)
val LightOutline = Color(0xFF707973)
val LightOutlineVariant = Color(0xFFC0C9C2)

// --- Dark --------------------------------------------------------------------
val DarkPrimary = Color(0xFF89D6AD)
val DarkOnPrimary = Color(0xFF003824)
val DarkPrimaryContainer = Color(0xFF005236)
val DarkOnPrimaryContainer = Color(0xFFA5F2C8)
val DarkSecondary = Color(0xFFB2CCBE)
val DarkOnSecondary = Color(0xFF1E352B)
val DarkSecondaryContainer = Color(0xFF344C41)
val DarkOnSecondaryContainer = Color(0xFFCEE9DA)
val DarkTertiary = Color(0xFFA5CDDE)
val DarkOnTertiary = Color(0xFF063543)
val DarkTertiaryContainer = Color(0xFF244C5A)
val DarkOnTertiaryContainer = Color(0xFFC1E9FB)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF111412)
val DarkOnBackground = Color(0xFFE1E3DF)
val DarkSurface = Color(0xFF111412)
val DarkOnSurface = Color(0xFFE1E3DF)
val DarkSurfaceVariant = Color(0xFF404943)
val DarkOnSurfaceVariant = Color(0xFFC0C9C2)
val DarkOutline = Color(0xFF8A938C)
val DarkOutlineVariant = Color(0xFF404943)

/**
 * Status colours, tuned per theme so that both the fill and the text on it clear
 * WCAG AA. These are semantic, not decorative, which is why they are held outside
 * the generated scheme.
 */
data class StatusPalette(
    val safe: Color,
    val onSafe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val caution: Color,
    val onCaution: Color,
    val cautionContainer: Color,
    val onCautionContainer: Color,
    val critical: Color,
    val onCritical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
    val neutral: Color,
    val onNeutral: Color,
)

val LightStatusPalette = StatusPalette(
    safe = Color(0xFF1B6C4B), onSafe = Color(0xFFFFFFFF),
    safeContainer = Color(0xFFD3F3E1), onSafeContainer = Color(0xFF00281A),
    caution = Color(0xFF8A5300), onCaution = Color(0xFFFFFFFF),
    cautionContainer = Color(0xFFFFE6C2), onCautionContainer = Color(0xFF2B1700),
    critical = Color(0xFFB3261E), onCritical = Color(0xFFFFFFFF),
    criticalContainer = Color(0xFFFFDAD6), onCriticalContainer = Color(0xFF410002),
    neutral = Color(0xFF5B635E), onNeutral = Color(0xFFFFFFFF),
)

val DarkStatusPalette = StatusPalette(
    safe = Color(0xFF7FD9AC), onSafe = Color(0xFF00281A),
    safeContainer = Color(0xFF14402D), onSafeContainer = Color(0xFFB9F0D2),
    caution = Color(0xFFFFC46B), onCaution = Color(0xFF2B1700),
    cautionContainer = Color(0xFF4A3110), onCautionContainer = Color(0xFFFFE0B2),
    critical = Color(0xFFFFB4AB), onCritical = Color(0xFF410002),
    criticalContainer = Color(0xFF5C1614), onCriticalContainer = Color(0xFFFFDAD6),
    neutral = Color(0xFFA8B0AA), onNeutral = Color(0xFF1E211F),
)

/**
 * Fixed palette for subject accents. Eight hues that stay distinguishable in both
 * themes and remain separable for the most common forms of colour blindness — the
 * subject's code and name are always shown beside the colour, so the colour is an
 * aid to recognition and never the only way to tell two subjects apart.
 */
val SubjectColorsLight = listOf(
    Color(0xFF2E6F4E), Color(0xFF1F5E8C), Color(0xFF8A4B1F), Color(0xFF6A3E8E),
    Color(0xFF1F6F6B), Color(0xFF8C1F4F), Color(0xFF4A5C1F), Color(0xFF3A4B8C),
)

val SubjectColorsDark = listOf(
    Color(0xFF7FD9AC), Color(0xFF8CC6F0), Color(0xFFF0B487), Color(0xFFCBA6E8),
    Color(0xFF7FD6D1), Color(0xFFF09CBE), Color(0xFFB9CC85), Color(0xFFA5B4F0),
)
