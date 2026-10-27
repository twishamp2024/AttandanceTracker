package com.twisham.attendance.domain.model

import com.twisham.attendance.core.AttendanceEligibility
import com.twisham.attendance.core.AttendanceSession
import com.twisham.attendance.core.AttendanceStatus
import com.twisham.attendance.core.SessionKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single real class on a single real date — the atomic unit of attendance in the app.
 *
 * Implements [AttendanceSession] from `attendance-core` so all attendance tallies,
 * calculators, and simulators can operate directly on application sessions.
 */
data class ClassSession(
    val id: Long = 0L,
    val semesterId: Long,
    val subjectId: Long,
    override val date: LocalDate,
    override val startTime: LocalTime,
    val endTime: LocalTime,
    override val status: AttendanceStatus,
    override val kind: SessionKind = SessionKind.SCHEDULED,
    val note: String? = null,
    val slotId: Long? = null,
    val room: String? = null,
) : AttendanceSession {
    val isMarked: Boolean get() = status != AttendanceStatus.NOT_MARKED

    override val startDateTime: LocalDateTime get() = date.atTime(startTime)

    override fun canMarkAttendance(now: LocalDateTime): Boolean =
        AttendanceEligibility.canMarkAttendance(startDateTime, now)
}
