package com.twisham.attendance.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AttendanceEligibilityTest {

    private val today = LocalDate.of(2026, 9, 28)

    private fun session(
        date: LocalDate = today,
        start: LocalTime = LocalTime.of(9, 0),
        status: AttendanceStatus = AttendanceStatus.NOT_MARKED,
    ) = SimpleAttendanceSession(
        date = date,
        startTime = start,
        status = status,
    )

    private fun at(hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalDateTime =
        today.atTime(hour, minute, second, nano)

    @Test
    fun `session cannot be marked before its start time`() {
        assertFalse(session().canMarkAttendance(at(7, 44)))
        assertFalse(session().canMarkAttendance(at(8, 59)))
        assertFalse(session().canMarkAttendance(at(8, 59, 59, 999_999_999)))
    }

    @Test
    fun `session can be marked at exactly the start time`() {
        assertTrue(session().canMarkAttendance(at(9, 0)))
    }

    @Test
    fun `session can be marked during and long after without closing cutoff`() {
        val s = session()
        assertTrue(s.canMarkAttendance(at(9, 30)))
        assertTrue(s.canMarkAttendance(at(9, 55)))
        assertTrue(s.canMarkAttendance(at(18, 0)))
        assertTrue(s.canMarkAttendance(at(23, 59, 59)))
    }

    @Test
    fun `future session cannot be marked today even after its time of day`() {
        val tomorrow = session(date = today.plusDays(1))
        assertFalse(tomorrow.canMarkAttendance(at(7, 44)))
        assertFalse(tomorrow.canMarkAttendance(at(18, 0)))
        assertTrue(tomorrow.canMarkAttendance(today.plusDays(1).atTime(9, 0)))
    }

    @Test
    fun `past session can be marked at any time`() {
        val yesterday = session(date = today.minusDays(1))
        assertTrue(yesterday.canMarkAttendance(at(7, 44)))
        assertTrue(yesterday.canMarkAttendance(today.plusWeeks(2).atTime(12, 0)))
    }

    @Test
    fun `standalone eligibility helper functions match session behavior`() {
        val start = today.atTime(9, 0)
        assertFalse(AttendanceEligibility.canMarkAttendance(start, at(8, 59)))
        assertTrue(AttendanceEligibility.canMarkAttendance(start, at(9, 0)))
        assertTrue(AttendanceEligibility.canMarkAttendance(start, at(10, 0)))

        assertFalse(AttendanceEligibility.canMarkAttendance(today, LocalTime.of(9, 0), at(8, 59)))
        assertTrue(AttendanceEligibility.canMarkAttendance(today, LocalTime.of(9, 0), at(9, 0)))
    }
}
