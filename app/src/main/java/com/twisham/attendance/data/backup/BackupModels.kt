package com.twisham.attendance.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk export format.
 *
 * Design goals, in order:
 *  1. **Human readable** – it is the user's own data; they should be able to open it
 *     in any text editor and understand it without this app.
 *  2. **Complete** – the entire attendance history can be reconstructed from it.
 *  3. **Stable** – [FORMAT] and [VERSION] are checked on import, so a future change
 *     produces a clear message instead of a corrupted database.
 *
 * Dates are ISO-8601 strings and times are `HH:mm` rather than the integers used
 * internally, because readability beats compactness for a file a person may inspect.
 * Enums are serialised by name for the same reason.
 *
 * The structure is nested (semester -> subject -> slots/sessions) so that a subject
 * can never end up orphaned from its semester, which a flat list of foreign keys
 * would allow.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    val settings: BackupSettings,
    val semesters: List<BackupSemester>,
) {
    companion object {
        const val FORMAT = "college-attendance-tracker"
        const val VERSION = 1
    }
}

@Serializable
data class BackupSettings(
    @SerialName("theme_mode") val themeMode: String,
    @SerialName("excused_policy") val excusedPolicy: String,
    @SerialName("caution_safe_miss_threshold") val cautionSafeMissThreshold: Int,
    @SerialName("trend_window_size") val trendWindowSize: Int,
    @SerialName("reminders_enabled") val remindersEnabled: Boolean,
    @SerialName("reminder_time") val reminderTime: String,
    @SerialName("week_starts_on") val weekStartsOn: String,
    @SerialName("stretch_targets_percent") val stretchTargets: List<Double>,
)

@Serializable
data class BackupSemester(
    val name: String,
    @SerialName("academic_year") val academicYear: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("default_requirement_percent") val defaultRequirement: Double,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    val subjects: List<BackupSubject>,
)

@Serializable
data class BackupSubject(
    val name: String,
    val code: String,
    val teacher: String? = null,
    @SerialName("requirement_percent") val requirement: Double,
    val type: String,
    @SerialName("color_index") val colorIndex: Int,
    @SerialName("is_active") val isActive: Boolean,
    val timetable: List<BackupSlot> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
)

@Serializable
data class BackupSlot(
    @SerialName("day_of_week") val dayOfWeek: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val room: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class BackupSession(
    val date: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val status: String,
    val kind: String = "SCHEDULED",
    val note: String? = null,
    val room: String? = null,
)
