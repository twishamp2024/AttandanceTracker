package com.twisham.attendance.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Rules governing when attendance marking is eligible.
 *
 * Present/Absent marks become available from the exact moment the class starts on its
 * own date, with no closing time cutoff.
 */
object AttendanceEligibility {

    /**
     * Whether attendance can be marked at [now] for a session starting at [startDateTime].
     */
    fun canMarkAttendance(startDateTime: LocalDateTime, now: LocalDateTime): Boolean =
        !now.isBefore(startDateTime)

    /**
     * Whether attendance can be marked at [now] for a session on [date] at [startTime].
     */
    fun canMarkAttendance(date: LocalDate, startTime: LocalTime, now: LocalDateTime): Boolean =
        canMarkAttendance(date.atTime(startTime), now)
}
