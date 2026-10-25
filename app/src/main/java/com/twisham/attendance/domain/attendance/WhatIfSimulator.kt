package com.twisham.attendance.domain.attendance

import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.Percent

/** One hypothetical outcome. Purely a projection: nothing is ever written. */
data class WhatIfOutcome(
    val attend: Int,
    val miss: Int,
    val attended: Int,
    val conducted: Int,
    val percent: Percent?,
    val health: AttendanceHealth,
    val meetsRequirement: Boolean,
    /** Safe misses remaining *after* the hypothetical classes have happened. */
    val safeMissesAfter: Int,
)

/**
 * The "What if?" feature.
 *
 * Every function here is pure and takes the current tallies by value, so there is no
 * path by which a simulation can touch the database. That is enforced structurally
 * rather than by convention — the simulator has no repository and no coroutine
 * scope, it is arithmetic and nothing else.
 */
object WhatIfSimulator {

    /**
     * Projects the effect of attending [attend] and missing [miss] further classes.
     * Order does not matter: both land in the denominator and only attended lands in
     * the numerator, so the final ratio is independent of the sequence.
     */
    fun project(
        attended: Int,
        conducted: Int,
        requirement: Percent,
        attend: Int = 0,
        miss: Int = 0,
        cautionThreshold: Int = 1,
    ): WhatIfOutcome {
        val a = (attended + attend.coerceAtLeast(0))
        val c = (conducted + attend.coerceAtLeast(0) + miss.coerceAtLeast(0))
        return WhatIfOutcome(
            attend = attend,
            miss = miss,
            attended = a,
            conducted = c,
            percent = Percent.ofRatio(a, c),
            health = AttendanceCalculator.health(a, c, requirement, cautionThreshold),
            meetsRequirement = AttendanceCalculator.meetsRequirement(a, c, requirement),
            safeMissesAfter = AttendanceCalculator.safeMisses(a, c, requirement),
        )
    }

    /** "What if I miss the next 1, 2, 3… classes?" */
    fun missStreak(
        attended: Int,
        conducted: Int,
        requirement: Percent,
        upTo: Int,
        cautionThreshold: Int = 1,
    ): List<WhatIfOutcome> = (1..upTo).map {
        project(attended, conducted, requirement, miss = it, cautionThreshold = cautionThreshold)
    }

    /** "What if I attend the next 1, 2, 3… classes?" */
    fun attendStreak(
        attended: Int,
        conducted: Int,
        requirement: Percent,
        upTo: Int,
        cautionThreshold: Int = 1,
    ): List<WhatIfOutcome> = (1..upTo).map {
        project(attended, conducted, requirement, attend = it, cautionThreshold = cautionThreshold)
    }
}
