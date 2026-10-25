package com.twisham.attendance.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.ThemeMode

/** Status colours reach the UI through this rather than being passed down by hand. */
val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

/** Subject accent colours, resolved for the active theme. */
val LocalSubjectColors = staticCompositionLocalOf { SubjectColorsLight }

private val LightColors = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer, onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary, onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer, onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError, onError = LightOnError,
    errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer,
    background = LightBackground, onBackground = LightOnBackground,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline, outlineVariant = LightOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary, onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer, onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary, onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer, onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError, onError = DarkOnError,
    errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground, onBackground = DarkOnBackground,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline, outlineVariant = DarkOutlineVariant,
)

@Composable
fun AttendanceTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You. On by default where available; the brand palette is the fallback. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge is set up in MainActivity; here we only keep the system
            // icons legible against whatever surface ends up behind them.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalStatusPalette provides if (dark) DarkStatusPalette else LightStatusPalette,
        LocalSubjectColors provides if (dark) SubjectColorsDark else SubjectColorsLight,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Resolves a subject's stored palette index to an actual colour. */
@Composable
fun subjectColor(colorIndex: Int): Color {
    val colors = LocalSubjectColors.current
    return colors[((colorIndex % colors.size) + colors.size) % colors.size]
}

/** Foreground/background pair for a health state, from the semantic palette. */
@Composable
fun healthColors(health: AttendanceHealth): Pair<Color, Color> {
    val palette = LocalStatusPalette.current
    return when (health) {
        AttendanceHealth.SAFE -> palette.safeContainer to palette.onSafeContainer
        AttendanceHealth.CAUTION -> palette.cautionContainer to palette.onCautionContainer
        AttendanceHealth.CRITICAL -> palette.criticalContainer to palette.onCriticalContainer
        AttendanceHealth.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** The solid accent colour for a health state (rings, bars, indicators). */
@Composable
fun healthAccent(health: AttendanceHealth): Color {
    val palette = LocalStatusPalette.current
    return when (health) {
        AttendanceHealth.SAFE -> palette.safe
        AttendanceHealth.CAUTION -> palette.caution
        AttendanceHealth.CRITICAL -> palette.critical
        AttendanceHealth.NO_DATA -> palette.neutral
    }
}
