package com.twisham.attendance.domain

import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.ui.today.TodayViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When Present/Absent become available: from the class's start, on its own date,
 * and never closing again afterwards.
 */
class AttendanceEligibilityTest {

    private val today = LocalDate.of(2026, 9, 28)

    private var nextId = 1L

    /** Algorithms, 09:00–09:55, today unless told otherwise. */
    private fun algorithms(
        date: LocalDate = today,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(9, 55),
        status: AttendanceStatus = AttendanceStatus.NOT_MARKED,
    ) = ClassSession(
        id = nextId++,
        semesterId = 1,
        subjectId = 1,
        date = date,
        startTime = start,
        endTime = end,
        status = status,
    )

    private fun at(hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalDateTime =
        today.atTime(hour, minute, second, nano)

    @Test
    fun `before the class starts it cannot be marked`() {
        assertFalse(algorithms().canMarkAttendance(at(7, 44)))
        assertFalse(algorithms().canMarkAttendance(at(8, 59)))
    }

    @Test
    fun `a class starting one minute from now cannot be marked`() {
        val now = at(8, 59)
        assertFalse(algorithms(start = now.toLocalTime().plusMinutes(1)).canMarkAttendance(now))
    }

    @Test
    fun `the last instant before the start is still too early`() {
        assertFalse(algorithms().canMarkAttendance(at(8, 59, 59, 999_999_999)))
    }

    @Test
    fun `exactly at the start it can be marked`() {
        assertTrue(algorithms().canMarkAttendance(at(9, 0)))
    }

    @Test
    fun `during the class it can be marked`() {
        assertTrue(algorithms().canMarkAttendance(at(9, 30)))
    }

    @Test
    fun `at and after the end it can still be marked, with no closing time`() {
        val session = algorithms()
        assertTrue(session.canMarkAttendance(at(9, 55)))
        assertTrue(session.canMarkAttendance(at(14, 0)))
        // Left the phone at home, got back in the evening.
        assertTrue(session.canMarkAttendance(at(18, 0)))
        assertTrue(session.canMarkAttendance(at(23, 59, 59)))
    }

    @Test
    fun `a class tomorrow cannot be marked today, even after its time of day`() {
        val tomorrow = algorithms(date = today.plusDays(1))
        assertFalse(tomorrow.canMarkAttendance(at(7, 44)))
        // 18:00 today is later than 09:00 as a time of day, but not as a date-time.
        assertFalse(tomorrow.canMarkAttendance(at(18, 0)))
        assertTrue(tomorrow.canMarkAttendance(today.plusDays(1).atTime(9, 0)))
    }

    @Test
    fun `a class yesterday can be marked, even before its time of day today`() {
        val yesterday = algorithms(date = today.minusDays(1))
        assertTrue(yesterday.canMarkAttendance(at(7, 44)))
        assertTrue(yesterday.canMarkAttendance(today.plusWeeks(3).atTime(12, 0)))
    }

    @Test
    fun `each class uses its own start, so one starting does not unlock the next`() {
        val first = algorithms(start = LocalTime.of(9, 0), end = LocalTime.of(9, 55))
        val second = algorithms(start = LocalTime.of(10, 0), end = LocalTime.of(10, 55))
        val now = at(9, 30)

        assertTrue(first.canMarkAttendance(now))
        assertFalse(second.canMarkAttendance(now))

        val state = TodayViewModel.UiState(loading = false, sessions = listOf(first, second))
        assertEquals(listOf(first), state.markable(now))
        assertEquals(listOf(first, second), state.markable(at(10, 0)))
    }

    @Test
    fun `eligibility depends only on time, so an existing mark stays correctable`() {
        listOf(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT).forEach { status ->
            assertTrue(algorithms(status = status).canMarkAttendance(at(18, 0)))
        }
    }

    @Test
    fun `already marked classes are never offered to mark all present`() {
        val marked = algorithms(status = AttendanceStatus.ABSENT)
        val unmarked = algorithms(start = LocalTime.of(10, 0), end = LocalTime.of(10, 55))
        val state = TodayViewModel.UiState(loading = false, sessions = listOf(marked, unmarked))

        // The recorded ABSENT must never be swept up and overwritten with PRESENT.
        assertEquals(listOf(unmarked), state.markable(at(18, 0)))
    }
}
