package com.twisham.attendance.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.ui.theme.healthAccent

/**
 * Circular attendance indicator.
 *
 * The requirement is drawn as a tick mark on the ring, so the reading is "am I past
 * the line?" rather than "is 76 a big number?". The whole thing is collapsed into a
 * single spoken sentence for screen readers, since the individual arcs mean nothing
 * out loud.
 */
@Composable
fun AttendanceRing(
    percent: Percent?,
    requirement: Percent,
    health: AttendanceHealth,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 12.dp,
    caption: String? = null,
) {
    val accent = healthAccent(health)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant

    val target = (percent?.basisPoints ?: 0) / Percent.MAX_BP.toFloat()
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650),
        label = "attendanceSweep",
    )

    val spoken = buildString {
        append(percent?.formatFixed(1) ?: "No attendance recorded yet")
        append(", required ${requirement.format()}")
        caption?.let { append(", $it") }
    }

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            if (percent != null) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }

            // Requirement tick: a short radial notch across the track.
            val requirementFraction = requirement.basisPoints / Percent.MAX_BP.toFloat()
            val angleRad = Math.toRadians((-90f + 360f * requirementFraction).toDouble())
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension - stroke) / 2f
            val inner = radius - stroke / 2f - 2.dp.toPx()
            val outer = radius + stroke / 2f + 2.dp.toPx()
            drawLine(
                color = markColor,
                start = Offset(
                    centre.x + (inner * kotlin.math.cos(angleRad)).toFloat(),
                    centre.y + (inner * kotlin.math.sin(angleRad)).toFloat(),
                ),
                end = Offset(
                    centre.x + (outer * kotlin.math.cos(angleRad)).toFloat(),
                    centre.y + (outer * kotlin.math.sin(angleRad)).toFloat(),
                ),
                strokeWidth = 2.dp.toPx(),
            )
        }

        RingLabel(percent = percent, caption = caption, size = size)
    }
}

@Composable
private fun RingLabel(percent: Percent?, caption: String?, size: Dp) {
    val compact = size < 110.dp
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = percent?.formatFixed(1) ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (compact) 20.sp else 26.sp,
                ),
                textAlign = TextAlign.Center,
            )
            if (caption != null && !compact) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Slim linear variant used inside dense list rows. */
@Composable
fun AttendanceBar(
    percent: Percent?,
    requirement: Percent,
    health: AttendanceHealth,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val accent = healthAccent(health)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fraction by animateFloatAsState(
        targetValue = (percent?.basisPoints ?: 0) / Percent.MAX_BP.toFloat(),
        animationSpec = tween(durationMillis = 650),
        label = "attendanceBar",
    )

    Box(
        modifier = modifier
            .height(height)
            .drawBehind {
                val radius = size.height / 2f
                drawRoundRect(
                    color = track,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                if (percent != null) {
                    drawRoundRect(
                        color = accent,
                        size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    )
                }
                val x = size.width * (requirement.basisPoints / Percent.MAX_BP.toFloat())
                drawLine(
                    color = markColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .clearAndSetSemantics { },
    )
}

/** Small filled dot used where a full ring would be too heavy. */
@Composable
fun HealthDot(health: AttendanceHealth, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val accent = healthAccent(health)
    Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
        drawCircle(color = accent)
    }
}
