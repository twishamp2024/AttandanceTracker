package com.twisham.attendance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twisham.attendance.domain.model.Percent

/** One point on the running-attendance line. */
data class TrendPoint(val label: String, val percent: Percent)

/**
 * Running attendance over time, drawn as a line against the requirement.
 *
 * This is a *cumulative* series — each point is the percentage as it stood after
 * that class — not a per-class 0/100 spike. Cumulative is the only version that
 * answers the question a student actually has ("was I above the line back then?").
 *
 * No charting library is used: one path, one dashed rule and a fill is less code
 * than configuring a library, and it inherits theme colours automatically so it
 * stays readable in dark mode.
 */
@Composable
fun AttendanceTrendChart(
    points: List<TrendPoint>,
    requirement: Percent,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    if (points.size < 2) return

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val requirementColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    // Zoom to the data, but always keep the requirement line in frame and never let
    // the band collapse so far that normal variation looks like a cliff.
    val values = points.map { it.percent.basisPoints }
    val lowest = minOf(values.min(), requirement.basisPoints)
    val highest = maxOf(values.max(), requirement.basisPoints)
    val padding = maxOf(500, (highest - lowest) / 5)
    val minBp = (lowest - padding).coerceAtLeast(0)
    val maxBp = (highest + padding).coerceAtMost(Percent.MAX_BP)
    val span = (maxBp - minBp).coerceAtLeast(1)

    val spoken = "Attendance trend from ${points.first().percent.formatFixed(1)} " +
        "to ${points.last().percent.formatFixed(1)}, against a requirement of ${requirement.format()}."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            fun yFor(bp: Int): Float = h - ((bp - minBp).toFloat() / span) * h
            fun xFor(index: Int): Float =
                if (points.size == 1) w / 2f else w * index / (points.size - 1).toFloat()

            // Baseline and top rules give the eye something to measure against.
            drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())

            val path = Path()
            val fill = Path()
            points.forEachIndexed { index, point ->
                val x = xFor(index)
                val y = yFor(point.percent.basisPoints)
                if (index == 0) {
                    path.moveTo(x, y)
                    fill.moveTo(x, h)
                    fill.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(xFor(points.lastIndex), h)
            fill.close()

            drawPath(fill, color = fillColor)
            drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

            val requirementY = yFor(requirement.basisPoints)
            if (requirementY in 0f..h) {
                drawLine(
                    color = requirementColor,
                    start = Offset(0f, requirementY),
                    end = Offset(w, requirementY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                    ),
                )
            }

            // Emphasise where the student stands right now.
            val lastX = xFor(points.lastIndex)
            val lastY = yFor(points.last().percent.basisPoints)
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
