package com.twisham.attendance.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * One recurring weekly entry, e.g. "Algorithms, Monday 09:00–10:50".
 *
 * The timetable is a *template*. It generates [ClassSession]s for real dates; it is
 * never itself the attendance record. This separation is what allows a one-off
 * cancellation or a room change to be recorded without corrupting the weekly plan.
 */
data class TimetableSlot(
    val id: Long = 0L,
    val subjectId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val room: String? = null,
    val isActive: Boolean = true,
) {
    val durationMinutes: Int
        get() = ((endTime.toSecondOfDay() - startTime.toSecondOfDay()) / 60).coerceAtLeast(0)

    fun overlaps(other: TimetableSlot): Boolean =
        dayOfWeek == other.dayOfWeek &&
            startTime < other.endTime &&
            other.startTime < endTime
}
