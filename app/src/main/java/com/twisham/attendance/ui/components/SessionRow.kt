package com.twisham.attendance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.ui.theme.LocalStatusPalette
import com.twisham.attendance.ui.theme.subjectColor
import com.twisham.attendance.util.Formatters
import com.twisham.attendance.util.label

/**
 * One class in a list, with the fast path built in.
 *
 * An unmarked session shows **Present** and **Absent** as real buttons right in the
 * row. That is the whole product thesis: the common case is two taps from cold start
 * (open app, tap Present) and never passes through a form. Anything rarer —
 * cancelled, excused, a note, a correction — lives behind tapping the row, so the
 * uncommon cases stay possible without slowing the common one down.
 */
@Composable
fun SessionListItem(
    session: ClassSession,
    subject: Subject?,
    modifier: Modifier = Modifier,
    onMark: (AttendanceStatus) -> Unit,
    onOpenDetails: () -> Unit,
    showDate: Boolean = false,
    today: java.time.LocalDate? = null,
    /** False before the class has started: Present/Absent show but cannot be tapped. */
    canMark: Boolean = true,
) {
    val accent = subjectColor(subject?.colorIndex ?: 0)
    val marked = session.isMarked

    val spokenSummary = buildString {
        append(subject?.name ?: "Unknown subject")
        append(", ${Formatters.timeRange(session.startTime, session.endTime)}")
        if (showDate && today != null) append(", ${Formatters.relativeDate(session.date, today)}")
        append(", ${session.status.label}")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) { contentDescription = spokenSummary },
        onClick = onOpenDetails,
        colors = CardDefaults.cardColors(
            containerColor = if (marked) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectSwatch(accent)
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Formatters.timeRange(session.startTime, session.endTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showDate && today != null) {
                            Text(
                                text = " · ${Formatters.relativeDate(session.date, today)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (session.kind != SessionKind.SCHEDULED) {
                            Spacer(Modifier.width(6.dp))
                            KindTag(session.kind)
                        }
                    }
                    Text(
                        text = subject?.name ?: "Unknown subject",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        session.room?.takeIf { it.isNotBlank() },
                        subject?.teacher?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (marked) {
                    Spacer(Modifier.width(8.dp))
                    StatusTag(session.status)
                }
            }

            if (!session.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = session.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(
                visible = !marked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    QuickMarkRow(
                        onPresent = { onMark(AttendanceStatus.PRESENT) },
                        onAbsent = { onMark(AttendanceStatus.ABSENT) },
                        subjectName = subject?.name.orEmpty(),
                        enabled = canMark,
                    )
                }
            }
        }
    }
}

/**
 * The two primary actions. Sized well past the 48dp minimum target because they are
 * routinely tapped one-handed while walking out of a lecture hall.
 *
 * [enabled] is false until the class starts; the buttons then take the standard
 * Material disabled styling rather than disappearing, so the layout does not jump.
 */
@Composable
fun QuickMarkRow(
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    subjectName: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalStatusPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPresent,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Mark present for $subjectName" },
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.safe,
                contentColor = palette.onSafe,
            ),
        ) {
            Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Present")
        }
        Button(
            onClick = onAbsent,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Mark absent for $subjectName" },
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.criticalContainer,
                contentColor = palette.onCriticalContainer,
            ),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Absent")
        }
    }
}

/** Status pill with an explicit word — colour is never the only signal. */
@Composable
fun StatusTag(status: AttendanceStatus, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    val (container, content) = when (status) {
        AttendanceStatus.PRESENT -> palette.safeContainer to palette.onSafeContainer
        AttendanceStatus.ABSENT -> palette.criticalContainer to palette.onCriticalContainer
        AttendanceStatus.EXCUSED -> palette.cautionContainer to palette.onCautionContainer
        AttendanceStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        AttendanceStatus.NOT_MARKED -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Marks a session as an extra or rescheduled class. */
@Composable
fun KindTag(kind: SessionKind, modifier: Modifier = Modifier) {
    if (kind == SessionKind.SCHEDULED) return
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = kind.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** Compact variant for the calendar's day sheet and dense lists. */
@Composable
fun CompactSessionRow(
    session: ClassSession,
    subject: Subject?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectSwatch(subjectColor(subject?.colorIndex ?: 0), Modifier.height(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = subject?.name ?: "Unknown subject",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Formatters.timeRange(session.startTime, session.endTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusTag(session.status, Modifier.widthIn(min = 0.dp))
    }
}
