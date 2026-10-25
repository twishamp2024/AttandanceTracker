package com.twisham.attendance.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.ui.components.ConfirmDialog
import com.twisham.attendance.ui.theme.LocalSubjectColors
import com.twisham.attendance.util.label

/** Create or edit a subject. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectEditScreen(
    subjectId: Long?,
    onDone: () -> Unit,
    viewModel: SubjectEditViewModel = viewModel(factory = SubjectEditViewModel.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(subjectId) { viewModel.load(subjectId) }
    LaunchedEffect(form.finished) { if (form.finished) onDone() }
    LaunchedEffect(form.message) {
        form.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (subjectId == null) "New subject" else "Edit subject") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (subjectId != null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete subject",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = { Text("Subject name") },
                placeholder = { Text("Database Management Systems") },
                singleLine = true,
                isError = form.name.isNotEmpty() && form.nameError != null,
                supportingText = form.nameError
                    ?.takeIf { form.name.isNotEmpty() }
                    ?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.code,
                onValueChange = viewModel::onCode,
                label = { Text("Short code") },
                placeholder = { Text("DBMS") },
                singleLine = true,
                supportingText = { Text("Shown where space is tight. Left blank, initials are used.") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.teacher,
                onValueChange = viewModel::onTeacher,
                label = { Text("Teacher (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Type", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectType.entries.forEach { type ->
                        FilterChip(
                            selected = form.type == type,
                            onClick = { viewModel.onType(type) },
                            label = { Text(type.label) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.requirementPercent,
                onValueChange = viewModel::onRequirement,
                label = { Text("Attendance requirement (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = form.requirementError != null,
                supportingText = {
                    Text(
                        form.requirementError
                            ?: "Labs often require more than theory papers — set each one separately.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Colour", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Used to recognise the subject at a glance. The name and code are " +
                        "always shown too, so the colour is never the only clue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ColorPicker(
                    selectedIndex = form.colorIndex,
                    onSelect = viewModel::onColor,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Inactive subjects stay in your history but leave the dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.isActive, onCheckedChange = viewModel::onActive)
            }

            Button(
                onClick = viewModel::save,
                enabled = form.isValid && !form.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(if (subjectId == null) "Add subject" else "Save changes") }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete ${form.name.ifBlank { "this subject" }}?",
            message = "Its ${form.sessionCount} attendance records and timetable entries " +
                "will be deleted too. This cannot be undone. To keep the history but hide " +
                "the subject, switch it to inactive instead.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete() },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun ColorPicker(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalSubjectColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { index, color ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) }
                    .semantics {
                        contentDescription = "Colour ${index + 1}${if (selected) ", selected" else ""}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
