package com.twisham.attendance.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatIfSimulatorTest {

    private val required75 = Percent.ofWhole(75)

    @Test
    fun `missing classes lowers projection monotonically`() {
        // Starting point: 40/52 = 76.92%
        val outcomes = WhatIfSimulator.missStreak(40, 52, required75, upTo = 3)
        assertEquals(3, outcomes.size)
        assertEquals(Percent.of(75.47), outcomes[0].percent) // 40/53
        assertEquals(Percent.of(74.07), outcomes[1].percent) // 40/54
        assertEquals(Percent.of(72.73), outcomes[2].percent) // 40/55

        assertTrue(outcomes[0].meetsRequirement)
        assertFalse(outcomes[1].meetsRequirement)
        assertEquals(AttendanceHealth.CRITICAL, outcomes[1].health)
    }

    @Test
    fun `attending classes raises projection`() {
        // 35/50 = 70%
        val outcomes = WhatIfSimulator.attendStreak(35, 50, required75, upTo = 5)
        assertEquals(Percent.of(72.73), outcomes.last().percent) // 40/55
        assertFalse(outcomes.last().meetsRequirement)

        // 10 classes to reach 75%
        val ten = WhatIfSimulator.project(35, 50, required75, attend = 10)
        assertEquals(Percent.ofWhole(75), ten.percent)
        assertTrue(ten.meetsRequirement)
    }

    @Test
    fun `order of attending and missing is independent`() {
        val a = WhatIfSimulator.project(40, 52, required75, attend = 3, miss = 2)
        assertEquals(43, a.attended)
        assertEquals(57, a.conducted)
        assertEquals(Percent.of(75.44), a.percent)
    }

    @Test
    fun `simulation reports safe misses remaining afterwards`() {
        val outcome = WhatIfSimulator.project(48, 52, required75, miss = 5)
        assertEquals(48, outcome.attended)
        assertEquals(57, outcome.conducted)
        assertEquals(7, outcome.safeMissesAfter)
    }

    @Test
    fun `negative inputs are clamped to zero`() {
        val outcome = WhatIfSimulator.project(40, 52, required75, attend = -5, miss = -3)
        assertEquals(40, outcome.attended)
        assertEquals(52, outcome.conducted)
    }
}
