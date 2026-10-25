package com.twisham.attendance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.twisham.attendance.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Read-only text field that opens the Material date picker.
 *
 * Dates are converted through UTC deliberately: the picker works in epoch millis,
 * and going via the device timezone is how "25 September" becomes "24 September" for
 * anyone east or west of UTC. A class date has no time zone, so neither does this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = Formatters.fullDate(value),
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        colors = disabledLooksEnabled(),
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .semantics { contentDescription = "$label: ${Formatters.fullDate(value)}" },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onValueChange(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    showPicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** Read-only text field that opens the Material time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = Formatters.time(value),
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        colors = disabledLooksEnabled(),
        modifier = modifier
            .clickable { showPicker = true }
            .semantics { contentDescription = "$label: ${Formatters.time(value)}" },
    )

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(LocalTime.of(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            title = { Text(label) },
            text = { TimePicker(state = pickerState) },
        )
    }
}

/**
 * The fields above are `enabled = false` so that taps reach the click handler rather
 * than the text cursor, but a greyed-out field would wrongly read as unavailable.
 * This restores the enabled colours while keeping the non-editable behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun disabledLooksEnabled() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
    disabledLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSupportingTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
)
