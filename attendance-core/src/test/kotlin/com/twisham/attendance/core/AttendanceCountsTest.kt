package com.twisham.attendance.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AttendanceCountsTest {

    private val baseDate = LocalDate.of(2026, 9, 28)
    private val baseTime = LocalTime.of(9, 0)

    private fun session(status: AttendanceStatus, kind: SessionKind = SessionKind.SCHEDULED) =
        SimpleAttendanceSession(
            date = baseDate,
            startTime = baseTime,
            status = status,
            kind = kind,
        )

    @Test
    fun `cancelled sessions never enter the denominator`() {
        val sessions = listOf(
            session(AttendanceStatus.PRESENT),
            session(AttendanceStatus.CANCELLED),
            session(AttendanceStatus.CANCELLED),
        )
        val counts = AttendanceCounts.from(sessions)
        assertEquals(1, counts.conducted)
        assertEquals(1, counts.attended)
        assertEquals(Percent.FULL, counts.percent)
    }

    @Test
    fun `unmarked sessions are excluded from conducted and attended`() {
        val sessions = listOf(
            session(AttendanceStatus.PRESENT),
            session(AttendanceStatus.NOT_MARKED),
        )
        val counts = AttendanceCounts.from(sessions)
        assertEquals(1, counts.conducted)
        assertEquals(1, counts.attended)
        assertEquals(1, counts.pending)
        assertEquals(Percent.FULL, counts.percent)
    }

    @Test
    fun `excused sessions behavior under IGNORE policy`() {
        val sessions = listOf(
            session(AttendanceStatus.PRESENT),
            session(AttendanceStatus.EXCUSED),
            session(AttendanceStatus.ABSENT),
        )
        val counts = AttendanceCounts.from(sessions, ExcusedPolicy.IGNORE)
        // Ignored: 1 present + 1 absent = 2 conducted, 1 attended -> 50%
        assertEquals(2, counts.conducted)
        assertEquals(1, counts.attended)
        assertEquals(Percent.ofWhole(50), counts.percent)
    }

    @Test
    fun `excused sessions behavior under COUNT_AS_PRESENT policy`() {
        val sessions = listOf(
            session(AttendanceStatus.PRESENT),
            session(AttendanceStatus.EXCUSED),
            session(AttendanceStatus.ABSENT),
        )
        val counts = AttendanceCounts.from(sessions, ExcusedPolicy.COUNT_AS_PRESENT)
        // Counted: 1 present + 1 excused + 1 absent = 3 conducted, 2 attended -> 66.67%
        assertEquals(3, counts.conducted)
        assertEquals(2, counts.attended)
        assertEquals(Percent.of(66.67), counts.percent)
    }

    @Test
    fun `extra classes increment conducted and extra count`() {
        val sessions = listOf(
            session(AttendanceStatus.PRESENT, SessionKind.EXTRA),
            session(AttendanceStatus.ABSENT, SessionKind.EXTRA),
            session(AttendanceStatus.CANCELLED, SessionKind.EXTRA),
        )
        val counts = AttendanceCounts.from(sessions)
        assertEquals(2, counts.conducted)
        assertEquals(1, counts.attended)
        assertEquals(2, counts.extra) // Cancelled extra is not attendance-bearing
    }

    @Test
    fun `plus operator aggregates tallies correctly`() {
        val a = AttendanceCounts(present = 5, absent = 2, total = 7)
        val b = AttendanceCounts(present = 3, absent = 1, total = 4)
        val combined = a + b
        assertEquals(8, combined.present)
        assertEquals(3, combined.absent)
        assertEquals(11, combined.total)
        assertEquals(11, combined.conducted)
        assertEquals(8, combined.attended)
    }
}
