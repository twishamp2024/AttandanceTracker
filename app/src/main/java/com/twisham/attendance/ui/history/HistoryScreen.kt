package com.twisham.attendance.ui.history

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.ui.components.CompactSessionRow
import com.twisham.attendance.ui.components.EmptyState
import com.twisham.attendance.ui.components.SessionEditorSheet
import com.twisham.attendance.util.Formatters
import com.twisham.attendance.util.label

/**
 * Attendance history with filters.
 *
 * Correcting a past mark is a first-class action here rather than something hidden:
 * mis-tapping Absent on the way out of a lecture is the single most likely data
 * error in this app, so fixing it is two taps from the history list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSubject: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ClassSession?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("History")
                        Text(
                            text = "${Formatters.classes(state.sessions.size)} shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.filters.isFiltered) {
                        IconButton(onClick = viewModel::clearFilters) {
                            Icon(Icons.Outlined.FilterAltOff, contentDescription = "Clear filters")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterBar(state = state, viewModel = viewModel)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.sessions.isEmpty() && !state.loading) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = if (state.totalBeforeFilter == 0) "Nothing recorded yet" else "No matches",
                    message = if (state.totalBeforeFilter == 0) {
                        "Once classes start being marked, they will all be listed here."
                    } else {
                        "No classes match these filters. Try widening the date range."
                    },
                    actionLabel = if (state.filters.isFiltered) "Clear filters" else null,
                    onAction = if (state.filters.isFiltered) viewModel::clearFilters else null,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Formatters.dayMonth(session.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(60.dp),
                            )
                            CompactSessionRow(
                                session = session,
                                subject = state.subjectsById[session.subjectId],
                                onClick = { editing = session },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    editing?.let { session ->
        SessionEditorSheet(
            session = session,
            subject = state.subjectsById[session.subjectId],
            today = state.today,
            onDismiss = { editing = null },
            onStatusChange = { viewModel.setStatus(session, it) },
            onNoteChange = { viewModel.setNote(session, it) },
            onDelete = { viewModel.delete(session) },
        )
    }
}

@Composable
private fun FilterBar(state: HistoryViewModel.UiState, viewModel: HistoryViewModel) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRange.entries.forEach { range ->
                FilterChip(
                    selected = state.filters.range == range,
                    onClick = { viewModel.setRange(range) },
                    label = { Text(range.label) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filters.subjectId == null,
                onClick = { viewModel.setSubject(null) },
                label = { Text("All subjects") },
            )
            state.subjects.forEach { subject ->
                FilterChip(
                    selected = state.filters.subjectId == subject.id,
                    onClick = { viewModel.setSubject(subject.id) },
                    label = { Text(subject.displayCode) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttendanceStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in state.filters.statuses,
                    onClick = { viewModel.toggleStatus(status) },
                    label = { Text(status.label) },
                )
            }
        }
    }
}
