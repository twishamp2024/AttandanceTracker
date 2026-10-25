package com.twisham.attendance.domain

import com.twisham.attendance.domain.attendance.AttendanceCounts
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.ExcusedPolicy
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.SessionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** How each status feeds the numerator and denominator. */
class AttendanceCountsTest {

    private var nextId = 1L

    private fun session(
        status: AttendanceStatus,
        kind: SessionKind = SessionKind.SCHEDULED,
        day: Long = nextId,
    ) = ClassSession(
        id = nextId++,
        semesterId = 1,
        subjectId = 1,
        date = LocalDate.of(2026, 1, 1).plusDays(day),
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        status = status,
        kind = kind,
    )

    @Test
    fun `cancelled classes do not count as conducted`() {
        val sessions = List(8) { session(AttendanceStatus.PRESENT) } +
            List(2) { session(AttendanceStatus.ABSENT) } +
            List(5) { session(AttendanceStatus.CANCELLED) }

        val counts = AttendanceCounts.from(sessions, ExcusedPolicy.IGNORE)

        assertEquals(15, counts.total)
        assertEquals(5, counts.cancelled)
        // The five cancelled classes must be invisible to the maths: 8/10, not 8/15.
        assertEquals(10, counts.conducted)
        assertEquals(8, counts.attended)
        assertEquals(Percent.ofWhole(80), counts.percent)
    }

    @Test
    fun `unmarked classes are excluded from the maths entirely`() {
        val sessions = List(6) { session(AttendanceStatus.PRESENT) } +
            List(4) { session(AttendanceStatus.NOT_MARKED) }

        val counts = AttendanceCounts.from(sessions, ExcusedPolicy.IGNORE)

        // Silence is not absence: 6/6, not 6/10.
        assertEquals(6, counts.conducted)
        assertEquals(Percent.FULL, counts.percent)
        assertEquals(4, counts.pending)
    }

    @Test
    fun `extra classes raise conducted and attended like any other class`() {
        val scheduled = List(9) { session(AttendanceStatus.PRESENT) } +
            List(1) { session(AttendanceStatus.ABSENT) }
        val extras = listOf(
            session(AttendanceStatus.PRESENT, SessionKind.EXTRA),
            session(AttendanceStatus.ABSENT, SessionKind.EXTRA),
        )

        val counts = AttendanceCounts.from(scheduled + extras, ExcusedPolicy.IGNORE)

        assertEquals(12, counts.conducted)
        assertEquals(10, counts.attended)
        assertEquals(2, counts.extra)
        assertEquals(Percent.of(83.33), counts.percent)
    }

    @Test
    fun `excused classes are ignored by default`() {
        val sessions = List(7) { session(AttendanceStatus.PRESENT) } +
            List(3) { session(AttendanceStatus.ABSENT) } +
            List(5) { session(AttendanceStatus.EXCUSED) }

        val counts = AttendanceCounts.from(sessions, ExcusedPolicy.IGNORE)

        // 7/10 — the excused classes leave both sides of the ratio untouched.
        assertEquals(10, counts.conducted)
        assertEquals(7, counts.attended)
        assertEquals(5, counts.excused)
        assertEquals(Percent.ofWhole(70), counts.percent)
    }

    @Test
    fun `excused classes can be configured to count as present`() {
        val sessions = List(7) { session(AttendanceStatus.PRESENT) } +
            List(3) { session(AttendanceStatus.ABSENT) } +
            List(5) { session(AttendanceStatus.EXCUSED) }

        val counts = AttendanceCounts.from(sessions, ExcusedPolicy.COUNT_AS_PRESENT)

        // 12/15 = 80%.
        assertEquals(15, counts.conducted)
        assertEquals(12, counts.attended)
        assertEquals(Percent.ofWhole(80), counts.percent)
    }

    @Test
    fun `an empty subject has an unknown percentage`() {
        val counts = AttendanceCounts.from(emptyList(), ExcusedPolicy.IGNORE)
        assertEquals(0, counts.conducted)
        assertEquals(null, counts.percent)
    }

    @Test
    fun `counts add up across subjects for the pooled overall figure`() {
        val a = AttendanceCounts(present = 40, absent = 10, total = 50)
        val b = AttendanceCounts(present = 20, absent = 30, cancelled = 5, total = 55)
        val pooled = a + b
        assertEquals(60, pooled.attended)
        assertEquals(100, pooled.conducted)
        assertEquals(Percent.ofWhole(60), pooled.percent)
        assertEquals(5, pooled.cancelled)
    }
}
