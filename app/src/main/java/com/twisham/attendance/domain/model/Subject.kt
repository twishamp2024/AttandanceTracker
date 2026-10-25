package com.twisham.attendance.domain.model

/**
 * A course within a semester.
 *
 * [requirement] is per-subject on purpose: labs and theory papers routinely carry
 * different thresholds, so a single app-wide requirement would silently give wrong
 * answers for at least one of them.
 */
data class Subject(
    val id: Long = 0L,
    val semesterId: Long,
    val name: String,
    /** Short code shown in dense places like the calendar and timetable grid. */
    val code: String,
    val teacher: String? = null,
    val requirement: Percent,
    val type: SubjectType,
    /** Index into the app's fixed subject palette; keeps theming consistent. */
    val colorIndex: Int,
    val isActive: Boolean = true,
) {
    /** Never blank: falls back to initials of the name. */
    val displayCode: String
        get() = code.ifBlank {
            name.split(' ').filter { it.isNotBlank() }.take(3)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "?" }
        }
}
