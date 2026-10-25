package com.twisham.attendance.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.BuildConfig
import com.twisham.attendance.data.backup.ImportMode
import com.twisham.attendance.domain.model.ExcusedPolicy
import com.twisham.attendance.domain.model.ThemeMode
import com.twisham.attendance.ui.components.ConfirmDialog
import com.twisham.attendance.ui.components.TimeField

/**
 * Settings.
 *
 * Grouped by what the user is trying to do rather than by which table the value
 * lives in, and every option that changes the *maths* explains its effect inline —
 * a silently applied excused-absence rule would be a great way to make the headline
 * percentage untrustworthy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSemesters: () -> Unit,
    onOpenTimetable: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmingReset by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportJson(context, it) } }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.stageImport(context, it) } }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.update { it.copy(remindersEnabled = granted) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // ---------------------------------------------------- appearance
            SettingsSection("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.update { s -> s.copy(themeMode = mode) } },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Follow system"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            // ------------------------------------------------- how it counts
            SettingsSection("How attendance is counted") {
                Text("Excused absences", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Medical or official leave marked as Excused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExcusedPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = state.settings.excusedPolicy == policy,
                            onClick = { viewModel.update { s -> s.copy(excusedPolicy = policy) } },
                            label = {
                                Text(
                                    when (policy) {
                                        ExcusedPolicy.IGNORE -> "Don't count at all"
                                        ExcusedPolicy.COUNT_AS_PRESENT -> "Count as present"
                                    },
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (state.settings.excusedPolicy) {
                        ExcusedPolicy.IGNORE ->
                            "Excused classes are removed from both sides of the fraction, " +
                                "as though they never happened for you."
                        ExcusedPolicy.COUNT_AS_PRESENT ->
                            "Excused classes count towards both attended and conducted."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))
                Text("Caution warning", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Show a subject as Caution when this many spare classes are left.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3, 5).forEach { threshold ->
                        FilterChip(
                            selected = state.settings.cautionSafeMissThreshold == threshold,
                            onClick = {
                                viewModel.update { s -> s.copy(cautionSafeMissThreshold = threshold) }
                            },
                            label = { Text(if (threshold == 0) "Only when at the line" else "$threshold or fewer") },
                        )
                    }
                }
            }

            // ------------------------------------------------- notifications
            SettingsSection("Reminders") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Only arrives if classes are still unmarked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.remindersEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.update { it.copy(remindersEnabled = enabled) }
                            }
                        },
                    )
                }
                if (state.settings.remindersEnabled) {
                    Spacer(Modifier.height(12.dp))
                    TimeField(
                        label = "Remind me at",
                        value = state.settings.reminderTime,
                        onValueChange = { time -> viewModel.update { it.copy(reminderTime = time) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ----------------------------------------------------- structure
            SettingsSection("Your semester") {
                SettingsRow(
                    title = "Semesters",
                    subtitle = "${state.semesterCount} saved · create, archive or switch",
                    onClick = onOpenSemesters,
                )
                SettingsRow(
                    title = "Timetable",
                    subtitle = "Edit your weekly classes",
                    onClick = onOpenTimetable,
                )
            }

            // ---------------------------------------------------------- data
            SettingsSection("Your data") {
                Text(
                    text = "Everything stays on this device. The app has no network " +
                        "permission, so nothing can leave it except through these buttons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SettingsRow(
                    title = "Export backup (JSON)",
                    subtitle = "Complete and restorable",
                    onClick = { exportJsonLauncher.launch(SettingsViewModel.suggestedJsonName()) },
                )
                SettingsRow(
                    title = "Export spreadsheet (CSV)",
                    subtitle = "One row per class, for spreadsheets",
                    onClick = { exportCsvLauncher.launch(SettingsViewModel.suggestedCsvName()) },
                )
                SettingsRow(
                    title = "Import backup",
                    subtitle = "Checked completely before anything is changed",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
                SettingsRow(
                    title = "Add demo data",
                    subtitle = "A fictional semester to try the app with. Delete it any time.",
                    onClick = viewModel::installSampleData,
                )
                SettingsRow(
                    title = "Delete all data",
                    subtitle = "Every semester, subject and attendance record",
                    onClick = { confirmingReset = true },
                    destructive = true,
                )
            }

            // --------------------------------------------------------- about
            SettingsSection("About") {
                Text("Attendance", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "A personal, offline attendance tracker. No account, no network, " +
                        "no analytics. Attendance is attended ÷ conducted; cancelled and " +
                        "unmarked classes are excluded from both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    state.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Import this backup?") },
            text = {
                Text(
                    "The file is valid and contains ${pending.semesters} semesters, " +
                        "${pending.subjects} subjects and ${pending.sessions} classes.\n\n" +
                        "Add keeps everything you already have and adds these alongside it. " +
                        "Replace deletes all current data first.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport(ImportMode.MERGE) }) {
                    Text("Add alongside")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
                    TextButton(onClick = { viewModel.confirmImport(ImportMode.REPLACE) }) {
                        Text("Replace all", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    if (confirmingReset) {
        ConfirmDialog(
            title = "Delete everything?",
            message = "This removes every semester, subject, timetable entry and " +
                "attendance record permanently. Export a backup first if you might " +
                "want any of it back.",
            confirmLabel = "Delete everything",
            onConfirm = viewModel::resetEverything,
            onDismiss = { confirmingReset = false },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScopeAlias.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
