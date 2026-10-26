package com.twisham.attendance.core

/** One hypothetical simulation projection. Pure projection: does not modify state. */
data class WhatIfOutcome(
    val attend: Int,
    val miss: Int,
    val attended: Int,
    val conducted: Int,
    val percent: Percent?,
    val health: AttendanceHealth,
    val meetsRequirement: Boolean,
    /** Safe misses remaining after the hypothetical classes have taken place. */
    val safeMissesAfter: Int,
)

/**
 * Pure simulation engine for hypothetical future attendance scenarios.
 */
object WhatIfSimulator {

    /**
     * Projects the effect of attending [attend] and missing [miss] further classes.
     */
    fun project(
        attended: Int,
        conducted: Int,
        requirement: Percent,
        attend: Int = 0,
        miss: Int = 0,
        cautionThreshold: Int = 1,
    ): WhatIfOutcome {
        val a = attended + attend.coerceAtLeast(0)
        val c = conducted + attend.coerceAtLeast(0) + miss.coerceAtLeast(0)
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
