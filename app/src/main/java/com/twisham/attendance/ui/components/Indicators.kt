package com.twisham.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.AttendanceTrend
import com.twisham.attendance.ui.theme.healthColors

/**
 * Status chip.
 *
 * Colour is never the sole carrier of meaning here: the chip always shows an icon
 * *and* the word, and the surrounding card repeats the information as a sentence.
 * That is what keeps the app usable for colour-blind users and under a screen
 * reader, and it is why the icons differ in shape (tick / triangle / octagon-ish)
 * rather than being three coloured circles.
 */
@Composable
fun HealthChip(
    health: AttendanceHealth,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (container, content) = healthColors(health)
    val (icon, text) = health.iconAndLabel()

    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(
                if (compact) PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                else PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            )
            .semantics { contentDescription = "Status: $text" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp),
        )
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun AttendanceHealth.iconAndLabel(): Pair<ImageVector, String> = when (this) {
    AttendanceHealth.SAFE -> Icons.Filled.CheckCircle to "Safe"
    AttendanceHealth.CAUTION -> Icons.Filled.Warning to "Caution"
    AttendanceHealth.CRITICAL -> Icons.Filled.Error to "Critical"
    AttendanceHealth.NO_DATA -> Icons.Filled.HelpOutline to "No data"
}

/** Up/down/flat arrow with a spoken label. Hidden entirely when the trend is unknown. */
@Composable
fun TrendIndicator(trend: AttendanceTrend, modifier: Modifier = Modifier) {
    if (trend == AttendanceTrend.UNKNOWN) return
    val (icon, label) = when (trend) {
        AttendanceTrend.IMPROVING -> Icons.Rounded.ArrowUpward to "Improving recently"
        AttendanceTrend.DECLINING -> Icons.Rounded.ArrowDownward to "Declining recently"
        else -> Icons.Rounded.Remove to "Holding steady"
    }
    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = when (trend) {
                AttendanceTrend.IMPROVING -> "Improving"
                AttendanceTrend.DECLINING -> "Declining"
                else -> "Steady"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A labelled number, used in the stat rows on the subject screen. */
@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            style = if (emphasis) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Section heading with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Decorative colour swatch for a subject; never the only identifier. */
@Composable
fun SubjectSwatch(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .size(width = 4.dp, height = 36.dp)
            .background(color, RoundedCornerShape(2.dp))
            .clearAndSetSemantics { },
    )
}
