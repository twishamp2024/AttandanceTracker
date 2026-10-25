package com.twisham.attendance.domain

import com.twisham.attendance.domain.attendance.AttendanceCalculator
import com.twisham.attendance.domain.attendance.AttendanceCounts
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The maths the whole app rests on.
 *
 * Each "how many can I miss / must I attend" answer is verified twice: once against
 * the expected number, and once by replaying the hypothetical to prove the boundary
 * really is where the calculator claims it is.
 */
class AttendanceCalculatorTest {

    private val required75 = Percent.ofWhole(75)

    // ---------------------------------------------------------- basic ratios

    @Test
    fun `perfect attendance is 100 percent and always meets the requirement`() {
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
    fun `exactly at the requirement counts as meeting it`() {
        // 39/52 is exactly 75%. A student on the line is safe, not failing.
        assertTrue(AttendanceCalculator.meetsRequirement(39, 52, required75))
        assertEquals(Percent.ofWhole(75), AttendanceCounts(present = 39, absent = 13).percent)
        assertEquals(
            AttendanceHealth.CAUTION,
            AttendanceCalculator.health(39, 52, required75, cautionThreshold = 1),
        )
        // Exactly on the line means there is no margin at all.
        assertEquals(0, AttendanceCalculator.safeMisses(39, 52, required75))
    }

    @Test
    fun `just below the requirement is critical`() {
        // 38/52 = 73.08%.
        assertFalse(AttendanceCalculator.meetsRequirement(38, 52, required75))
        assertEquals(
            AttendanceHealth.CRITICAL,
            AttendanceCalculator.health(38, 52, required75, cautionThreshold = 1),
        )
        // One single class short of the line must still read as critical.
        assertFalse(AttendanceCalculator.meetsRequirement(74, 100, required75))
        assertTrue(AttendanceCalculator.meetsRequirement(75, 100, required75))
    }

    @Test
    fun `nothing conducted is unknown rather than zero`() {
        val counts = AttendanceCounts()
        assertNull(counts.percent)
        assertEquals(
            AttendanceHealth.NO_DATA,
            AttendanceCalculator.health(0, 0, required75, cautionThreshold = 1),
        )
        // Not yet failing: you cannot be below a line you have not reached.
        assertTrue(AttendanceCalculator.meetsRequirement(0, 0, required75))
    }

    // ------------------------------------------------------------ safe misses

    @Test
    fun `safe misses matches a brute force search`() {
        val cases = listOf(38 to 50, 48 to 52, 40 to 52, 35 to 49, 100 to 100, 0 to 10, 7 to 9)
        for ((attended, conducted) in cases) {
            val reported = AttendanceCalculator.safeMisses(attended, conducted, required75)
            if (!AttendanceCalculator.meetsRequirement(attended, conducted, required75)) {
                // Already below the line: nothing at all can be afforded.
                assertEquals("$attended/$conducted is already failing", 0, reported)
                continue
            }
            // Everything up to `reported` must still meet the requirement...
            for (k in 0..reported) {
                assertTrue(
                    "$attended/$conducted should survive missing $k",
                    AttendanceCalculator.meetsRequirement(attended, conducted + k, required75),
                )
            }
            // ...and one more must break it.
            assertFalse(
                "$attended/$conducted should fail after missing ${reported + 1}",
                AttendanceCalculator.meetsRequirement(attended, conducted + reported + 1, required75),
            )
        }
    }

    @Test
    fun `safe misses for the worked examples`() {
        // 38/50 = 76%, but a single miss drops it to 74.5%.
        assertEquals(0, AttendanceCalculator.safeMisses(38, 50, required75))
        // 40/52 = 76.9%; one miss lands on exactly 75.47%, two breaks it.
        assertEquals(1, AttendanceCalculator.safeMisses(40, 52, required75))
        // 48/52 = 92.3%; 48/64 is exactly 75%, so twelve misses are affordable.
        assertEquals(12, AttendanceCalculator.safeMisses(48, 52, required75))
    }

    @Test
    fun `below the line means zero safe misses, never negative`() {
        assertEquals(0, AttendanceCalculator.safeMisses(35, 50, required75))
        assertEquals(0, AttendanceCalculator.safeMisses(1, 100, required75))
    }

    @Test
    fun `a zero percent requirement can never be broken`() {
        assertEquals(Int.MAX_VALUE, AttendanceCalculator.safeMisses(0, 10, Percent.ZERO))
    }

    // --------------------------------------------------- classes still needed

    @Test
    fun `classes needed matches a brute force search`() {
        val cases = listOf(35 to 50, 35 to 49, 0 to 10, 20 to 80, 74 to 100)
        for ((attended, conducted) in cases) {
            val n = AttendanceCalculator.classesNeededToReach(attended, conducted, required75)!!
            assertTrue(
                "$attended/$conducted after attending $n should meet 75%",
                AttendanceCalculator.meetsRequirement(attended + n, conducted + n, required75),
            )
            if (n > 0) {
                assertFalse(
                    "$attended/$conducted after attending ${n - 1} should not yet meet 75%",
                    AttendanceCalculator.meetsRequirement(attended + n - 1, conducted + n - 1, required75),
                )
            }
        }
    }

    @Test
    fun `classes needed for the worked examples`() {
        // 35/50 -> attend 10 -> 45/60 = exactly 75%.
        assertEquals(10, AttendanceCalculator.classesNeededToReach(35, 50, required75))
        // 35/49 -> attend 7 -> 42/56 = exactly 75%.
        assertEquals(7, AttendanceCalculator.classesNeededToReach(35, 49, required75))
    }

    @Test
    fun `already meeting the target needs zero further classes`() {
        assertEquals(0, AttendanceCalculator.classesNeededToReach(48, 52, required75))
        assertEquals(0, AttendanceCalculator.classesNeededToReach(39, 52, required75))
    }

    @Test
    fun `stretch targets need progressively more classes`() {
        val a = 35
        val c = 50
        val needed = listOf(75, 80, 85, 90).map {
            AttendanceCalculator.classesNeededToReach(a, c, Percent.ofWhole(it))!!
        }
        assertEquals(listOf(10, 25, 50, 100), needed)
        needed.forEachIndexed { index, n ->
            val target = Percent.ofWhole(listOf(75, 80, 85, 90)[index])
            assertTrue(AttendanceCalculator.meetsRequirement(a + n, c + n, target))
        }
    }

    @Test
    fun `100 percent is impossible once a class has been missed`() {
        assertNull(AttendanceCalculator.classesNeededToReach(9, 10, Percent.FULL))
        // ...but is trivially already met with a clean record.
        assertEquals(0, AttendanceCalculator.classesNeededToReach(10, 10, Percent.FULL))
    }

    // ------------------------------------------------------------ health band

    @Test
    fun `caution threshold is configurable and drives the band`() {
        // 40/52 has exactly one safe miss.
        assertEquals(
            AttendanceHealth.CAUTION,
            AttendanceCalculator.health(40, 52, required75, cautionThreshold = 1),
        )
        // With no tolerance configured, one spare class is enough to read as safe.
        assertEquals(
            AttendanceHealth.SAFE,
            AttendanceCalculator.health(40, 52, required75, cautionThreshold = 0),
        )
        // A cautious student can ask for a wider warning band.
        assertEquals(
            AttendanceHealth.CAUTION,
            AttendanceCalculator.health(48, 52, required75, cautionThreshold = 12),
        )
    }

    @Test
    fun `different subjects can carry different requirements`() {
        val lab = Percent.ofWhole(80)
        // 39/52 is 75%: fine for a theory paper, not for an 80% lab.
        assertTrue(AttendanceCalculator.meetsRequirement(39, 52, required75))
        assertFalse(AttendanceCalculator.meetsRequirement(39, 52, lab))
        assertEquals(13, AttendanceCalculator.classesNeededToReach(39, 52, lab))
    }
}
