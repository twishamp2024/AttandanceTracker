package com.twisham.attendance.domain.model

import java.time.LocalDate

/**
 * A block of teaching. Attendance never crosses a semester boundary: every subject
 * and every session belongs to exactly one semester, and all statistics are scoped
 * to the semester currently being viewed.
 */
data class Semester(
    val id: Long = 0L,
    val name: String,
    val academicYear: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Default requirement inherited by new subjects; each subject may override it. */
    val defaultRequirement: Percent,
    /** Exactly one semester is the "current" one, used when the app opens. */
    val isActive: Boolean,
    val isArchived: Boolean,
) {
    /** Archiving wins over dates: an archived semester is never shown as running. */
    fun stateOn(today: LocalDate): SemesterState = when {
        isArchived -> SemesterState.ARCHIVED
        today.isBefore(startDate) -> SemesterState.UPCOMING
        today.isAfter(endDate) -> SemesterState.FINISHED
        else -> SemesterState.ACTIVE
    }

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDate)
}
