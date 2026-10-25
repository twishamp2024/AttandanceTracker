package com.twisham.attendance.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single real class on a single real date — the atomic unit of attendance.
 *
 * Sessions are *materialised* from the timetable the first time a day is looked at,
 * rather than generated for the whole semester up front. That keeps the database
 * small, makes timetable edits apply to the future without rewriting the past, and
 * means a session row only ever exists because the day actually arrived.
 */
data class ClassSession(
    val id: Long = 0L,
    val semesterId: Long,
    val subjectId: Long,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: AttendanceStatus,
    val kind: SessionKind = SessionKind.SCHEDULED,
    val note: String? = null,
    /** The timetable slot this was generated from, if any. Null for extra classes. */
    val slotId: Long? = null,
    val room: String? = null,
) {
    val isMarked: Boolean get() = status != AttendanceStatus.NOT_MARKED

    /** The moment this class begins, on the device's local clock. */
    val startDateTime: LocalDateTime get() = date.atTime(startTime)

    /**
     * Whether Present/Absent can be recorded at [now]: from the moment the class
     * starts, with deliberately no closing time. A student who left their phone at
     * home must still be able to mark the class that evening, so [endTime] plays no
     * part. Cancelled, excused and notes are not gated by this at all.
     */
    fun canMarkAttendance(now: LocalDateTime): Boolean = !now.isBefore(startDateTime)
}
