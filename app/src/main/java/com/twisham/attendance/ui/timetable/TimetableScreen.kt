package com.twisham.attendance.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.ui.components.ConfirmDialog
import com.twisham.attendance.ui.components.EmptyState
import com.twisham.attendance.ui.components.SubjectSwatch
import com.twisham.attendance.ui.components.TimeField
import com.twisham.attendance.ui.theme.subjectColor
import com.twisham.attendance.util.Formatters
import com.twisham.attendance.util.label
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The weekly plan.
 *
 * Presented as a day-by-day list rather than a grid. A true grid looks impressive in
 * a screenshot but is unreadable on a 360dp phone once lab blocks of two or three
 * hours are involved, and editing inside grid cells is fiddly. The list scales from
 * a small phone to a tablet without any of that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onAddSubject: () -> Unit,
    onOpenSubject: (Long) -> Unit,
    viewModel: TimetableViewModel = viewModel(factory = TimetableViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<TimetableSlot?>(null) }
    var deleting by remember { mutableStateOf<TimetableSlot?>(null) }

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
                        Text("Timetable")
                        if (state.weeklyClassCount > 0) {
                            Text(
                                text = "${Formatters.classes(state.weeklyClassCount)} a week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.subjects.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = TimetableSlot(
                            subjectId = state.subjects.first().id,
                            dayOfWeek = DayOfWeek.MONDAY,
                            startTime = TimetableViewModel.DEFAULT_START,
                            endTime = TimetableViewModel.DEFAULT_END,
                        )
                    },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add class") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Spacer(Modifier.fillMaxSize())

            state.subjects.isEmpty() -> EmptyState(
                icon = Icons.Outlined.GridView,
                title = "Add subjects first",
                message = "A timetable entry points at a subject, so add the subjects " +
                    "you are taking before building the weekly plan.",
                actionLabel = "Add a subject",
                onAction = onAddSubject,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.GridView,
                title = "No classes scheduled",
                message = "Add your weekly classes here. Each day's classes are created " +
                    "automatically from this plan, ready to mark.",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    val slots = state.slotsByDay[day].orEmpty()
                    item(key = "day-$day") { DayHeader(day, slots.size) }

                    if (slots.isEmpty()) {
                        item(key = "empty-$day") {
                            Text(
                                text = "No classes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                        }
                    } else {
                        items(slots, key = { "slot-${it.id}" }) { slot ->
                            SlotCard(
                                slot = slot,
                                subject = state.subjectsById[slot.subjectId],
                                onEdit = { editing = slot },
                                onDelete = { deleting = slot },
                                onOpenSubject = { onOpenSubject(slot.subjectId) },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { slot ->
        SlotEditorSheet(
            slot = slot,
            subjects = state.subjects,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
        )
    }

    deleting?.let { slot ->
        val subject = state.subjectsById[slot.subjectId]
        ConfirmDialog(
            title = "Remove this class from the timetable?",
            message = "${subject?.name ?: "This class"} on " +
                "${slot.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                "will stop being scheduled from today onwards. Attendance you have " +
                "already recorded is kept.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.delete(slot) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DayHeader(day: DayOfWeek, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        if (count > 0) {
            Text(
                text = "· ${Formatters.classes(count)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SlotCard(
    slot: TimetableSlot,
    subject: Subject?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenSubject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectSwatch(subjectColor(subject?.colorIndex ?: 0))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = Formatters.timeRange(slot.startTime, slot.endTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = subject?.name ?: "Unknown subject",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        subject?.type?.label,
                        Formatters.duration(slot.durationMinutes),
                        slot.room?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${subject?.name ?: "class"} from the timetable",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Add/edit a weekly slot. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SlotEditorSheet(
    slot: TimetableSlot,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (TimetableSlot) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var subjectId by remember(slot.id) { mutableLongStateOf(slot.subjectId) }
    var day by remember(slot.id) { mutableStateOf(slot.dayOfWeek) }
    var start by remember(slot.id) { mutableStateOf(slot.startTime) }
    var end by remember(slot.id) { mutableStateOf(slot.endTime) }
    var room by remember(slot.id) { mutableStateOf(slot.room.orEmpty()) }

    val timeInvalid = !end.isAfter(start)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = if (slot.id == 0L) "Add a class" else "Edit class",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(16.dp))
            Text("Subject", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                subjects.forEach { subject ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .androidxSelectableRow(subjectId == subject.id) { subjectId = subject.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = subjectId == subject.id, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        SubjectSwatch(subjectColor(subject.colorIndex), Modifier.height(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(subject.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Day", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val locale = LocalConfiguration.current.locales[0]
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { candidate ->
                    FilterChip(
                        selected = day == candidate,
                        onClick = { day = candidate },
                        label = {
                            Text(
                                candidate.getDisplayName(
                                    java.time.format.TextStyle.SHORT,
                                    locale,
                                ),
                            )
                        },
                        modifier = Modifier.semantics {
                            contentDescription = candidate.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    label = "Starts",
                    value = start,
                    onValueChange = { start = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = "Ends",
                    value = end,
                    onValueChange = { end = it },
                    modifier = Modifier.weight(1f),
                    isError = timeInvalid,
                    supportingText = if (timeInvalid) "Must be after the start" else null,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = room,
                onValueChange = { room = it },
                label = { Text("Room (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !timeInvalid && subjectId > 0,
                    onClick = {
                        onSave(
                            slot.copy(
                                subjectId = subjectId,
                                dayOfWeek = day,
                                startTime = start,
                                endTime = end,
                                room = room.trim().takeIf { it.isNotBlank() },
                            ),
                        )
                    },
                ) { Text("Save") }
            }
        }
    }
}

/** Makes the whole row a single radio target rather than just the dot. */
private fun Modifier.androidxSelectableRow(selected: Boolean, onClick: () -> Unit): Modifier =
    this.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
