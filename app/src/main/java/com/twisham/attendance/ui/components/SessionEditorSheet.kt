package com.twisham.attendance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.util.Formatters
import com.twisham.attendance.util.effectDescription
import com.twisham.attendance.util.label

/**
 * The full editor for one session: every status, a note, and deletion.
 *
 * Each status option states what it does to the percentage ("Does not count at
 * all"), because "Excused" versus "Cancelled" is genuinely ambiguous to a student
 * and guessing wrong quietly corrupts the number they rely on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorSheet(
    session: ClassSession,
    subject: Subject?,
    today: java.time.LocalDate,
    onDismiss: () -> Unit,
    onStatusChange: (AttendanceStatus) -> Unit,
    onNoteChange: (String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    /**
     * False before the class has started, which disables only Present and Absent.
     * Cancelled, excused and the note stay available, since those are often known
     * ahead of time.
     */
    canMarkAttendance: Boolean = session.canMarkAttendance(java.time.LocalDateTime.now()),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by rememberSaveable(session.id) { mutableStateOf(session.note.orEmpty()) }
    var confirmingDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = subject?.name ?: "Class",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${Formatters.relativeDate(session.date, today)} · " +
                    Formatters.timeRange(session.startTime, session.endTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text("Attendance", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            Column(Modifier.selectableGroup()) {
                AttendanceStatus.entries.forEach { status ->
                    StatusOption(
                        status = status,
                        selected = session.status == status,
                        onSelect = { onStatusChange(status) },
                        enabled = canMarkAttendance || !status.isPrimaryAnswer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. Class shifted to room 204") },
                minLines = 2,
                maxLines = 4,
                supportingText = { Text("Saved when you close this sheet.") },
            )

            if (onDelete != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Delete this class", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    onNoteChange(note.takeIf { it.isNotBlank() })
                    onDismiss()
                }) { Text("Done") }
            }
        }
    }

    if (confirmingDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete this class?",
            message = "The class and its attendance record will be removed. " +
                "It will not come back when the timetable is refreshed.",
            confirmLabel = "Delete",
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun StatusOption(
    status: AttendanceStatus,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onSelect, enabled = enabled, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        // 0.38 is Material's standard disabled-content alpha, matching the radio.
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f)) {
            Text(status.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = status.effectDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
