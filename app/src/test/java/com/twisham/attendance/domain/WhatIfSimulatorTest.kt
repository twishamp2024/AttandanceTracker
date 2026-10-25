package com.twisham.attendance.domain

import com.twisham.attendance.domain.attendance.WhatIfSimulator
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatIfSimulatorTest {

    private val required75 = Percent.ofWhole(75)

    @Test
    fun `missing the next classes lowers the projection step by step`() {
        // Starting point 40/52 = 76.92%.
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
    fun `attending the next classes raises the projection`() {
        // 35/50 = 70%; five more attended gives 40/55 = 72.73%.
        val outcomes = WhatIfSimulator.attendStreak(35, 50, required75, upTo = 5)
        assertEquals(Percent.of(72.73), outcomes.last().percent)
        assertFalse(outcomes.last().meetsRequirement)

        // Ten more is exactly enough to reach the line.
        val ten = WhatIfSimulator.project(35, 50, required75, attend = 10)
        assertEquals(Percent.ofWhole(75), ten.percent)
        assertTrue(ten.meetsRequirement)
    }

    @Test
    fun `mixed attending and missing is order independent`() {
        val a = WhatIfSimulator.project(40, 52, required75, attend = 3, miss = 2)
        assertEquals(43, a.attended)
        assertEquals(57, a.conducted)
        assertEquals(Percent.of(75.44), a.percent)
    }

    @Test
    fun `simulation reports the margin left afterwards`() {
        val outcome = WhatIfSimulator.project(48, 52, required75, miss = 5)
        assertEquals(48, outcome.attended)
        assertEquals(57, outcome.conducted)
        // Twelve were affordable, five are spent, so seven remain.
        assertEquals(7, outcome.safeMissesAfter)
    }

    @Test
    fun `negative input is treated as zero rather than rewriting history`() {
        val outcome = WhatIfSimulator.project(40, 52, required75, attend = -5, miss = -3)
        assertEquals(40, outcome.attended)
        assertEquals(52, outcome.conducted)
    }
}
