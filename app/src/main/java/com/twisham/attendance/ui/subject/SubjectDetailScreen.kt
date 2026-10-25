package com.twisham.attendance.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.attendance.SubjectAttendance
import com.twisham.attendance.domain.attendance.TargetProjection
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.ui.components.AttendanceRing
import com.twisham.attendance.ui.components.AttendanceTrendChart
import com.twisham.attendance.ui.components.CompactSessionRow
import com.twisham.attendance.ui.components.HealthChip
import com.twisham.attendance.ui.components.SectionHeader
import com.twisham.attendance.ui.components.SessionEditorSheet
import com.twisham.attendance.ui.components.StatCell
import com.twisham.attendance.ui.components.TrendIndicator
import com.twisham.attendance.ui.dashboard.headline
import com.twisham.attendance.util.Formatters
import com.twisham.attendance.util.label

/**
 * Everything about one subject: where it stands, how it got there, and what happens
 * next under various choices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    subjectId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: SubjectDetailViewModel = viewModel(factory = SubjectDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ClassSession?>(null) }

    LaunchedEffect(subjectId) { viewModel.load(subjectId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    val attendance = state.attendance

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = attendance?.subject?.name ?: "Subject",
                            maxLines = 1,
                        )
                        attendance?.let {
                            Text(
                                text = "${it.subject.displayCode} · ${it.subject.type.label}" +
                                    (it.subject.teacher?.let { t -> " · $t" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(subjectId) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit subject")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (attendance == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { SummaryCard(attendance) }
            item(key = "stats") { StatsCard(attendance) }

            if (attendance.stretchTargets.isNotEmpty()) {
                item(key = "targets") { TargetsCard(attendance.stretchTargets) }
            }

            if (state.trend.size >= 2) {
                item(key = "trend") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = "Attendance over time",
                                subtitle = "Running percentage after each class, " +
                                    "against the ${attendance.requirement.format()} line",
                            )
                            Spacer(Modifier.height(12.dp))
                            AttendanceTrendChart(
                                points = state.trend,
                                requirement = attendance.requirement,
                            )
                        }
                    }
                }
            }

            item(key = "whatif") {
                WhatIfCard(
                    state = state,
                    onAttendChange = viewModel::setWhatIfAttend,
                    onMissChange = viewModel::setWhatIfMiss,
                    onReset = viewModel::resetWhatIf,
                )
            }

            item(key = "history-header") {
                SectionHeader(
                    title = "History",
                    subtitle = "${Formatters.classes(state.sessions.size)} · " +
                        "tap any class to correct it",
                )
            }

            items(state.sessions, key = { it.id }) { session ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Formatters.dayMonth(session.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        CompactSessionRow(
                            session = session,
                            subject = attendance.subject,
                            onClick = { editing = session },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    editing?.let { session ->
        SessionEditorSheet(
            session = session,
            subject = attendance?.subject,
            today = state.today,
            onDismiss = { editing = null },
            onStatusChange = { viewModel.setStatus(session, it) },
            onNoteChange = { viewModel.setNote(session, it) },
            onDelete = { viewModel.delete(session) },
        )
    }
}

@Composable
private fun SummaryCard(attendance: SubjectAttendance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AttendanceRing(
                percent = attendance.percent,
                requirement = attendance.requirement,
                health = attendance.health,
                caption = "needs ${attendance.requirement.format()}",
                size = 150.dp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HealthChip(attendance.health)
                TrendIndicator(attendance.trend)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = attendance.headline(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatsCard(attendance: SubjectAttendance) {
    val counts = attendance.counts
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCell("Attended", counts.attended.toString(), emphasis = true)
                StatCell("Conducted", counts.conducted.toString(), emphasis = true)
                StatCell("Absent", counts.missed.toString(), emphasis = true)
                StatCell(
                    label = "Can miss",
                    value = if (attendance.hasUnlimitedMisses) "∞" else attendance.safeMisses.toString(),
                    emphasis = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCell("Excused", counts.excused.toString())
                StatCell("Cancelled", counts.cancelled.toString())
                StatCell("Extra", counts.extra.toString())
                StatCell("Unmarked", counts.pending.toString())
            }
            if (counts.cancelled > 0 || counts.pending > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Cancelled and unmarked classes are not counted in the percentage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TargetsCard(targets: List<TargetProjection>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                title = "Reaching higher",
                subtitle = "Classes to attend in a row, starting from now",
            )
            Spacer(Modifier.height(10.dp))
            targets.forEach { projection ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = projection.target.format(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        text = when {
                            projection.alreadyMet -> "Already there."
                            projection.isImpossible ->
                                "Not reachable — past absences stay in the total."
                            projection.classesNeeded == 0 -> "Already there."
                            else -> "Attend the next ${Formatters.classes(projection.classesNeeded!!)}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (projection.isImpossible) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

/**
 * "What if?".
 *
 * Presented as steppers rather than a free-text field: the question is always "one
 * more, two more", and a keyboard would be slower and invite nonsense input. The
 * card states plainly that it changes nothing, because a tool that looks like it is
 * editing your attendance is a tool people are afraid to touch.
 */
@Composable
private fun WhatIfCard(
    state: SubjectDetailViewModel.UiState,
    onAttendChange: (Int) -> Unit,
    onMissChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val outcome = state.whatIf ?: return
    val attendance = state.attendance ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                title = "What if?",
                subtitle = "A preview only — nothing here changes your records",
                trailing = {
                    if (state.whatIfChanged) {
                        TextButton(onClick = onReset) { Text("Reset") }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            Stepper(
                label = "Attend the next",
                value = state.whatIfAttend,
                onChange = onAttendChange,
            )
            Spacer(Modifier.height(8.dp))
            Stepper(
                label = "Miss the next",
                value = state.whatIfMiss,
                onChange = onMissChange,
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.25f))
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.whatIfChanged) "Would become" else "Right now",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = outcome.percent?.formatFixed(1) ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "${outcome.attended} / ${outcome.conducted} classes",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HealthChip(outcome.health)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    !outcome.meetsRequirement ->
                        "That drops you below the required ${attendance.requirement.format()}."
                    outcome.safeMissesAfter == 0 ->
                        "Still above the line, with no room left to miss anything else."
                    else ->
                        "Still above the line, with ${Formatters.classes(outcome.safeMissesAfter)} " +
                            "left to spare."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = { onChange(value - 1) },
            enabled = value > 0,
            modifier = Modifier.width(56.dp),
        ) { Text("−") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        FilledTonalButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.width(56.dp),
        ) { Text("+") }
    }
}
