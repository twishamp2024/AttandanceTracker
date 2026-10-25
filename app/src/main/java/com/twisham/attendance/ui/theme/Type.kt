package com.twisham.attendance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * The system font is used deliberately: it already matches the user's chosen font
 * size and weight settings, and bundling a webfont would add a megabyte to an app
 * whose entire job is to show numbers quickly. All sizes are in `sp`, so text scales
 * with the accessibility font-size setting rather than ignoring it.
 */
val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * The big percentage readouts. Tabular figures would be ideal; the system font gives
 * near-enough consistent digit widths, and centring keeps the number from jittering
 * as it changes.
 */
val PercentDisplayStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 46.sp,
    textAlign = TextAlign.Center,
)
