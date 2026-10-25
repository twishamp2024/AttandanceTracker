package com.twisham.attendance.domain

import com.twisham.attendance.domain.model.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exact-arithmetic foundation everything else stands on. */
class PercentTest {

    @Test
    fun `whole percentages convert to basis points`() {
        assertEquals(7500, Percent.ofWhole(75).basisPoints)
        assertEquals(0, Percent.ofWhole(0).basisPoints)
        assertEquals(10_000, Percent.ofWhole(100).basisPoints)
    }

    @Test
    fun `fractional percentages round half up to two decimals`() {
        assertEquals(7750, Percent.of(77.5).basisPoints)
        assertEquals(6667, Percent.of(66.666).basisPoints)
        assertEquals(6666, Percent.of(66.664).basisPoints)
    }

    @Test
    fun `out of range input is clamped rather than crashing`() {
        assertEquals(0, Percent.of(-10.0).basisPoints)
        assertEquals(10_000, Percent.of(140.0).basisPoints)
    }

    @Test
    fun `ratio conversion is exact for the classic repeating case`() {
        // 40/52 = 76.923076...%, which must round to 76.92 and never to 76.93.
        assertEquals(7692, Percent.ofRatio(40, 52)!!.basisPoints)
        // 2/3 = 66.666...%, rounds to 66.67.
        assertEquals(6667, Percent.ofRatio(2, 3)!!.basisPoints)
    }

    @Test
    fun `exact three quarters is exactly 7500 basis points`() {
        // The case that floating point gets wrong: 0.75 compared against 75%.
        assertEquals(7500, Percent.ofRatio(3, 4)!!.basisPoints)
        assertEquals(7500, Percent.ofRatio(39, 52)!!.basisPoints)
        assertEquals(7500, Percent.ofRatio(750, 1000)!!.basisPoints)
    }

    @Test
    fun `zero denominator is unknown, not zero percent`() {
        assertNull(Percent.ofRatio(0, 0))
        assertNull(Percent.ofRatio(5, -1))
    }

    @Test
    fun `formatting drops noise but keeps meaningful decimals`() {
        assertEquals("75%", Percent.ofWhole(75).format())
        assertEquals("77.5%", Percent.of(77.5).format())
        assertEquals("76.92%", Percent(7692).format())
        assertEquals("76.9%", Percent(7692).formatFixed(1))
        assertEquals("77%", Percent(7692).formatFixed(0))
        assertEquals("100.0%", Percent.FULL.formatFixed(1))
    }

    @Test
    fun `comparison is ordered by basis points`() {
        assertTrue(Percent.ofWhole(80) > Percent.ofWhole(75))
        assertTrue(Percent(7500) >= Percent.ofWhole(75))
    }
}
