package com.twisham.attendance.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.attendance.OverallAttendance
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.ui.components.AttendanceRing
import com.twisham.attendance.ui.components.EmptyState
import com.twisham.attendance.ui.components.SectionHeader
import com.twisham.attendance.util.Formatters

/**
 * The overview: one honest number at the top, then the per-subject detail that the
 * single number necessarily hides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSubject: (Long) -> Unit,
    onAddSubject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Overview")
                        state.semester?.let {
                            Text(
                                text = "${it.name} · ${it.academicYear}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, contentDescription = "Attendance history")
                    }
                    IconButton(onClick = onAddSubject) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add subject")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Spacer(Modifier.fillMaxSize())

            state.subjects.isEmpty() -> EmptyState(
                icon = Icons.Outlined.MenuBook,
                title = "No subjects yet",
                message = "Add the subjects you are taking this semester, then build your " +
                    "weekly timetable. Attendance is tracked from there automatically.",
                actionLabel = "Add a subject",
                onAction = onAddSubject,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.overall?.let { overall ->
                    item(key = "overall") { OverallCard(overall) }
                }

                item(key = "subjects-header") {
                    SectionHeader(
                        title = "Subjects",
                        subtitle = "Sorted by what needs attention first",
                        trailing = {
                            TextButton(onClick = onOpenHistory) { Text("History") }
                        },
                    )
                }

                items(state.subjects, key = { it.subject.id }) { attendance ->
                    SubjectAttendanceCard(
                        attendance = attendance,
                        onClick = { onOpenSubject(attendance.subject.id) },
                    )
                }

                item(key = "explainer") { CalculationExplainer() }
            }
        }
    }
}

/**
 * The headline figure.
 *
 * It is explicitly labelled as pooled across all classes, and the per-subject
 * breakdown sits right beneath it, because a single percentage across subjects with
 * different requirements is genuinely capable of misleading — 84% overall can still
 * hide one subject sitting at 60%.
 */
@Composable
private fun OverallCard(overall: OverallAttendance) {
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
                percent = overall.percent,
                requirement = Percent.ofWhole(75),
                health = when {
                    overall.criticalCount > 0 -> AttendanceHealth.CRITICAL
                    overall.cautionCount > 0 -> AttendanceHealth.CAUTION
                    overall.counts.conducted == 0 -> AttendanceHealth.NO_DATA
                    else -> AttendanceHealth.SAFE
                },
                caption = "overall",
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "${overall.counts.attended} of ${overall.counts.conducted} classes attended",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Pooled across every subject this semester",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CountPill("Safe", overall.safeCount)
                CountPill("Caution", overall.cautionCount)
                CountPill("Critical", overall.criticalCount)
            }

            if (overall.unmarkedSessions > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${Formatters.classes(overall.unmarkedSessions)} still unmarked — " +
                        "they are not counted until you answer them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CountPill(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Says out loud how the numbers are produced, so they can be trusted or checked. */
@Composable
private fun CalculationExplainer() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("How this is worked out", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Attendance is attended ÷ conducted. Cancelled classes and " +
                    "classes you have not marked yet are left out of both sides. Extra " +
                    "classes count like any other. Excused absences follow the setting " +
                    "you chose in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
