package com.twisham.attendance.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.ui.components.EmptyState
import com.twisham.attendance.ui.components.QuickMarkRow
import com.twisham.attendance.ui.components.SessionEditorSheet
import com.twisham.attendance.ui.components.SessionListItem
import com.twisham.attendance.util.Formatters
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Today.
 *
 * The screen is ordered by urgency rather than by time: whatever still needs an
 * answer is at the top, with the class happening right now promoted into a hero card
 * so the most common action is the largest target on screen. Already-marked classes
 * sit underneath as a record, still tappable to correct a mistake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenSubject: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ClassSession?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.lastUndo) {
        val undo = state.lastUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = undo.label,
            actionLabel = "Undo",
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.consumeUndo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today")
                        Text(
                            text = Formatters.fullDate(state.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.markable(now).size > 1) {
                        IconButton(
                            onClick = viewModel::markAllPresent,
                            modifier = Modifier.semantics {
                                contentDescription = "Mark all remaining classes present"
                            },
                        ) { Icon(Icons.Filled.DoneAll, contentDescription = null) }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Spacer(Modifier.fillMaxSize())

            state.sessions.isEmpty() -> EmptyState(
                icon = Icons.Outlined.EventAvailable,
                title = "No classes today",
                message = if (state.semester == null) {
                    "Create a semester and add your timetable to start tracking attendance."
                } else {
                    "Nothing is scheduled for ${Formatters.dayName(state.date)}. " +
                        "If a class did happen, you can add it from the timetable."
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            else -> TodayList(
                state = state,
                now = now,
                contentPadding = padding,
                onMark = viewModel::mark,
                onOpenDetails = { editing = it },
            )
        }
    }

    editing?.let { session ->
        SessionEditorSheet(
            session = session,
            subject = state.subjectsById[session.subjectId],
            today = state.date,
            onDismiss = { editing = null },
            onStatusChange = { viewModel.setStatus(session, it) },
            onNoteChange = { viewModel.setNote(session, it) },
            onDelete = { viewModel.delete(session) },
            canMarkAttendance = session.canMarkAttendance(now),
        )
    }
}

@Composable
private fun TodayList(
    state: TodayViewModel.UiState,
    now: LocalDateTime,
    contentPadding: PaddingValues,
    onMark: (ClassSession, com.twisham.attendance.domain.model.AttendanceStatus) -> Unit,
    onOpenDetails: (ClassSession) -> Unit,
) {
    val focus = state.focus(now.toLocalTime())
    val remaining = state.unmarked.filter { it.id != focus?.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (focus != null) {
            item(key = "focus-${focus.id}") {
                FocusCard(
                    session = focus,
                    subject = state.subjectsById[focus.subjectId],
                    now = now.toLocalTime(),
                    canMark = focus.canMarkAttendance(now),
                    onMark = { onMark(focus, it) },
                    onOpenDetails = { onOpenDetails(focus) },
                )
            }
        }

        if (state.allDone) {
            item(key = "all-done") { AllDoneCard(state.sessions.size) }
        }

        if (remaining.isNotEmpty()) {
            item(key = "header-remaining") {
                SectionLabel(
                    if (focus != null) "Also still to mark" else "Still to mark",
                )
            }
            items(remaining, key = { "pending-${it.id}" }) { session ->
                SessionListItem(
                    session = session,
                    subject = state.subjectsById[session.subjectId],
                    onMark = { onMark(session, it) },
                    onOpenDetails = { onOpenDetails(session) },
                    canMark = session.canMarkAttendance(now),
                )
            }
        }

        if (state.marked.isNotEmpty()) {
            item(key = "header-done") { SectionLabel("Marked") }
            items(state.marked, key = { "done-${it.id}" }) { session ->
                SessionListItem(
                    session = session,
                    subject = state.subjectsById[session.subjectId],
                    onMark = { onMark(session, it) },
                    onOpenDetails = { onOpenDetails(session) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/**
 * The hero card for the class that needs answering right now. It repeats information
 * that is also in the list below, which is a deliberate trade: one oversized,
 * unambiguous target beats a tidier screen that costs an extra moment of aiming.
 */
@Composable
private fun FocusCard(
    session: ClassSession,
    subject: com.twisham.attendance.domain.model.Subject?,
    now: LocalTime,
    canMark: Boolean,
    onMark: (com.twisham.attendance.domain.model.AttendanceStatus) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val happeningNow = now >= session.startTime && now <= session.endTime
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = when {
                    happeningNow -> "Happening now"
                    session.startTime > now -> "Next up"
                    else -> "Earlier today"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subject?.name ?: "Class",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = listOfNotNull(
                    Formatters.timeRange(session.startTime, session.endTime),
                    session.room?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            QuickMarkRow(
                onPresent = { onMark(com.twisham.attendance.domain.model.AttendanceStatus.PRESENT) },
                onAbsent = { onMark(com.twisham.attendance.domain.model.AttendanceStatus.ABSENT) },
                subjectName = subject?.name.orEmpty(),
                enabled = canMark,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenDetails, modifier = Modifier.widthIn(min = 48.dp)) {
                Text("Cancelled, excused or add a note")
            }
        }
    }
}

@Composable
private fun AllDoneCard(total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("All marked for today", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${Formatters.classes(total)} recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
