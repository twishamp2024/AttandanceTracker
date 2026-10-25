package com.twisham.attendance.ui.semester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SemesterState
import com.twisham.attendance.ui.components.ConfirmDialog
import com.twisham.attendance.ui.components.DateField
import com.twisham.attendance.ui.components.EmptyState
import com.twisham.attendance.util.Formatters

/**
 * Semester management.
 *
 * Archiving is offered far more prominently than deleting. A finished semester is
 * still evidence — if a college queries attendance months later, the record needs to
 * exist — so the safe action is the obvious one and deletion is deliberately buried
 * behind a confirmation that states exactly how much data will go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterListScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCreate: () -> Unit,
    viewModel: SemesterListViewModel = viewModel(factory = SemesterListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<Semester?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Semesters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "Add semester")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.rows.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Outlined.School,
                title = "No semesters",
                message = "Create a semester to start tracking attendance.",
                actionLabel = "Create semester",
                onAction = onCreate,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.rows, key = { it.semester.id }) { row ->
                SemesterCard(
                    row = row,
                    onEdit = { onEdit(row.semester.id) },
                    onSetActive = { viewModel.setActive(row.semester.id) },
                    onToggleArchive = {
                        viewModel.setArchived(row.semester, !row.semester.isArchived)
                    },
                    onDelete = { deleting = row.semester },
                )
            }
        }
    }

    deleting?.let { semester ->
        val row = state.rows.firstOrNull { it.semester.id == semester.id }
        ConfirmDialog(
            title = "Delete ${semester.name}?",
            message = "This permanently removes ${row?.subjectCount ?: 0} subjects and " +
                "${row?.sessionCount ?: 0} attendance records. This cannot be undone. " +
                "If you only want it out of the way, archive it instead.",
            confirmLabel = "Delete permanently",
            onConfirm = { viewModel.delete(semester) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SemesterCard(
    row: SemesterRow,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val semester = row.semester
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (semester.isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(semester.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${semester.academicYear} · " +
                            "${Formatters.dayMonth(semester.startDate)} – " +
                            Formatters.dayMonth(semester.endDate),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StateChip(row.state, semester.isActive)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "${if (row.subjectCount == 1) "1 subject" else "${row.subjectCount} subjects"} · " +
                    "${Formatters.classes(row.sessionCount)} recorded · " +
                    "needs ${semester.defaultRequirement.format()} by default",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!semester.isActive && !semester.isArchived) {
                    TextButton(onClick = onSetActive, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Make active")
                    }
                }
                TextButton(onClick = onToggleArchive, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(
                        imageVector = if (semester.isArchived) Icons.Outlined.Unarchive
                        else Icons.Outlined.Archive,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (semester.isArchived) "Unarchive" else "Archive")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${semester.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StateChip(state: SemesterState, isActive: Boolean) {
    val label = when {
        isActive -> "Active"
        state == SemesterState.ARCHIVED -> "Archived"
        state == SemesterState.UPCOMING -> "Upcoming"
        state == SemesterState.FINISHED -> "Finished"
        else -> "Current"
    }
    AssistChip(onClick = {}, label = { Text(label) }, enabled = false)
}

/** Create/edit form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterEditScreen(
    semesterId: Long?,
    onDone: () -> Unit,
    viewModel: SemesterEditViewModel = viewModel(factory = SemesterEditViewModel.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(semesterId) { viewModel.load(semesterId) }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }
    LaunchedEffect(form.message) {
        form.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (semesterId == null) "New semester" else "Edit semester") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SemesterForm(
            form = form,
            viewModel = viewModel,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

@Composable
internal fun SemesterForm(
    form: SemesterEditViewModel.FormState,
    viewModel: SemesterEditViewModel,
    modifier: Modifier = Modifier,
    submitLabel: String = "Save semester",
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::onName,
            label = { Text("Semester name") },
            placeholder = { Text("Semester 5") },
            singleLine = true,
            isError = form.name.isNotEmpty() && form.nameError != null,
            supportingText = form.nameError?.takeIf { form.name.isNotEmpty() }?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.academicYear,
            onValueChange = viewModel::onAcademicYear,
            label = { Text("Academic year") },
            placeholder = { Text("2026–27") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        DateField(
            label = "Starts",
            value = form.startDate,
            onValueChange = viewModel::onStartDate,
        )

        DateField(
            label = "Ends",
            value = form.endDate,
            onValueChange = viewModel::onEndDate,
            isError = form.dateError != null,
            supportingText = form.dateError,
        )

        OutlinedTextField(
            value = form.requirementPercent,
            onValueChange = viewModel::onRequirement,
            label = { Text("Default attendance requirement (%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = form.requirementError != null,
            supportingText = {
                Text(
                    form.requirementError
                        ?: "New subjects start with this. Each subject can override it.",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Make this the active semester", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "The app opens on the active semester.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = form.makeActive, onCheckedChange = viewModel::onMakeActive)
        }

        Button(
            onClick = viewModel::save,
            enabled = form.isValid && !form.saving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) { Text(submitLabel) }

        Spacer(Modifier.height(24.dp))
    }
}
