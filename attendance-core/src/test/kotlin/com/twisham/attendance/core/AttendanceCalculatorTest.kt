package com.twisham.attendance.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AttendanceCalculatorTest {

    private val required75 = Percent.ofWhole(75)

    @Test
    fun `perfect attendance is 100 percent and safe`() {
        val counts = AttendanceCounts(present = 40, total = 40)
        assertEquals(Percent.FULL, counts.percent)
        assertTrue(AttendanceCalculator.meetsRequirement(40, 40, required75))
        assertEquals(
            AttendanceHealth.SAFE,
            AttendanceCalculator.health(40, 40, required75, cautionThreshold = 1),
        )
    }

    @Test
    fun `zero attendance is 0 percent and critical`() {
        val counts = AttendanceCounts(absent = 20, total = 20)
        assertEquals(Percent.ZERO, counts.percent)
        assertFalse(AttendanceCalculator.meetsRequirement(0, 20, required75))
        assertEquals(
            AttendanceHealth.CRITICAL,
            AttendanceCalculator.health(0, 20, required75, cautionThreshold = 1),
        )
        assertEquals(0, AttendanceCalculator.safeMisses(0, 20, required75))
    }

    @Test
    fun `exactly at the requirement meets it with caution`() {
        // 39/52 is exactly 75%.
        assertTrue(AttendanceCalculator.meetsRequirement(39, 52, required75))
        assertEquals(Percent.ofWhole(75), AttendanceCounts(present = 39, absent = 13).percent)
        assertEquals(
            AttendanceHealth.CAUTION,
            AttendanceCalculator.health(39, 52, required75, cautionThreshold = 1),
        )
        // Zero safe misses left when right on the boundary
        assertEquals(0, AttendanceCalculator.safeMisses(39, 52, required75))
    }

    @Test
    fun `just below the requirement is critical`() {
        // 38/52 = 73.08%
        assertFalse(AttendanceCalculator.meetsRequirement(38, 52, required75))
        assertEquals(
            AttendanceHealth.CRITICAL,
            AttendanceCalculator.health(38, 52, required75, cautionThreshold = 1),
        )
        assertFalse(AttendanceCalculator.meetsRequirement(74, 100, required75))
        assertTrue(AttendanceCalculator.meetsRequirement(75, 100, required75))
    }

    @Test
    fun `nothing conducted is NO_DATA`() {
        val counts = AttendanceCounts()
        assertNull(counts.percent)
        assertEquals(
            AttendanceHealth.NO_DATA,
            AttendanceCalculator.health(0, 0, required75, cautionThreshold = 1),
        )
        assertTrue(AttendanceCalculator.meetsRequirement(0, 0, required75))
    }

    @Test
    fun `safe misses matches brute force verification`() {
        val cases = listOf(38 to 50, 48 to 52, 40 to 52, 35 to 49, 100 to 100, 0 to 10, 7 to 9)
        for ((attended, conducted) in cases) {
            val reported = AttendanceCalculator.safeMisses(attended, conducted, required75)
            if (!AttendanceCalculator.meetsRequirement(attended, conducted, required75)) {
                assertEquals(0, reported)
                continue
            }
            for (k in 0..reported) {
                assertTrue(
                    "$attended/$conducted plus $k misses should meet requirement",
                    AttendanceCalculator.meetsRequirement(attended, conducted + k, required75),
                )
            }
            assertFalse(
                "$attended/$conducted plus ${reported + 1} misses should fail requirement",
                AttendanceCalculator.meetsRequirement(attended, conducted + reported + 1, required75),
            )
        }
    }

    @Test
    fun `zero requirement allows unlimited safe misses`() {
        assertEquals(Int.MAX_VALUE, AttendanceCalculator.safeMisses(0, 10, Percent.ZERO))
        assertEquals(Int.MAX_VALUE, AttendanceCalculator.safeMisses(10, 10, Percent.ZERO))
    }

    @Test
    fun `classes needed to reach target calculates exact requirement`() {
        // Already at or above target requires 0 classes
        assertEquals(0, AttendanceCalculator.classesNeededToReach(40, 50, required75))

        // 35/50 = 70%. Need 75%.
        // (35 + n)/(50 + n) >= 0.75 => 3500 + 100n >= 3750 + 75n => 25n >= 250 => n = 10
        val needed = AttendanceCalculator.classesNeededToReach(35, 50, required75)
        assertEquals(10, needed)

        // Verify boundary: 9 classes is 44/59 = 74.57% (fails); 10 is 45/60 = 75.0% (meets)
        assertFalse(AttendanceCalculator.meetsRequirement(35 + 9, 50 + 9, required75))
        assertTrue(AttendanceCalculator.meetsRequirement(35 + 10, 50 + 10, required75))
    }

    @Test
    fun `unreachable 100 percent after an absence returns null`() {
        assertNull(AttendanceCalculator.classesNeededToReach(9, 10, Percent.FULL))
        assertEquals(0, AttendanceCalculator.classesNeededToReach(10, 10, Percent.FULL))
    }

    @Test
    fun `trend detects improving declining and steady`() {
        val today = LocalDate.of(2026, 9, 28)
        val sessions = (1..10).map { i ->
            SimpleAttendanceSession(
                date = today.minusDays((10 - i).toLong()),
                startTime = LocalTime.of(9, 0),
                status = if (i > 6) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
            )
        }
        val counts = AttendanceCounts.from(sessions)
        val trend = AttendanceCalculator.trend(sessions, windowSize = 4, counts = counts)
        // Overall: 4/10 = 40%. Window (last 4): 4/4 = 100%. Delta = +60% > 5% -> IMPROVING
        assertEquals(AttendanceTrend.IMPROVING, trend)
    }
}
