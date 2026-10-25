package com.twisham.attendance.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twisham.attendance.domain.attendance.SubjectAttendance
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.ui.components.AttendanceBar
import com.twisham.attendance.ui.components.HealthChip
import com.twisham.attendance.ui.components.SubjectSwatch
import com.twisham.attendance.ui.components.TrendIndicator
import com.twisham.attendance.ui.theme.subjectColor
import com.twisham.attendance.util.Formatters
import com.twisham.attendance.util.label

/**
 * The card that answers "can I skip this one?" without the user doing any arithmetic.
 *
 * The headline sentence is the point of the whole app. It is written as an
 * instruction ("You can miss 3 more classes", "Attend the next 7 to reach 75%")
 * rather than as a statistic, because a number alone still leaves the student to
 * work out what to do with it.
 */
@Composable
fun SubjectAttendanceCard(
    attendance: SubjectAttendance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = attendance.subject
    val percentText = attendance.percent?.formatFixed(1) ?: "No classes yet"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("${subject.name}, $percentText")
                    append(", ${attendance.counts.attended} of ${attendance.counts.conducted} attended")
                    append(", required ${attendance.requirement.format()}")
                    append(". ${attendance.headline()}")
                }
            },
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectSwatch(subjectColor(subject.colorIndex))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${subject.displayCode} · ${subject.type.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = attendance.percent?.formatFixed(1) ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TrendIndicator(attendance.trend)
                }
            }

            Spacer(Modifier.height(12.dp))
            AttendanceBar(
                percent = attendance.percent,
                requirement = attendance.requirement,
                health = attendance.health,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealthChip(attendance.health, compact = true)
                Text(
                    text = "${attendance.counts.attended} / ${attendance.counts.conducted}" +
                        " · needs ${attendance.requirement.format()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = attendance.headline(),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (attendance.counts.pending > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${Formatters.classes(attendance.counts.pending)} still unmarked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The single most important sentence in the app.
 *
 * Note the asymmetry: when safe it reports a budget, when critical it reports a
 * plan. "You are at 71%" is a fact; "attend the next 7 classes" is something the
 * student can act on this week.
 */
fun SubjectAttendance.headline(): String = when (health) {
    AttendanceHealth.NO_DATA ->
        "No classes recorded yet."

    AttendanceHealth.CRITICAL -> when (val needed = classesToRequirement) {
        null -> "Reaching ${requirement.format()} is no longer possible this semester."
        0 -> "Back at the required level."
        else -> "Attend the next ${Formatters.classes(needed)} to reach ${requirement.format()}."
    }

    AttendanceHealth.CAUTION -> when (safeMisses) {
        0 -> "You cannot miss any more classes."
        1 -> "You can miss 1 more class."
        else -> "You can miss ${safeMisses} more classes."
    }

    AttendanceHealth.SAFE ->
        if (hasUnlimitedMisses) "No attendance requirement set."
        else "You can miss ${Formatters.classes(safeMisses)}."
}
