package com.twisham.attendance.core

/**
 * Attendance mathematics and eligibility algorithms.
 *
 * ## Integer Arithmetic
 *
 * Comparisons between `attended / conducted` and `requirement` are performed using
 * cross-multiplication in [Long] basis points:
 *
 * ```
 * attended / conducted >= bp / 10000  <=>  attended * 10000 >= bp * conducted
 * ```
 *
 * This completely prevents floating-point inaccuracies when a student is exactly at
 * the requirement threshold (e.g., 75%).
 */
object AttendanceCalculator {

    private const val SCALE = Percent.MAX_BP.toLong() // 10_000

    /** True when `attended / conducted` is at or above [requirement]. Exact integer comparison. */
    fun meetsRequirement(attended: Int, conducted: Int, requirement: Percent): Boolean {
        if (conducted <= 0) return true
        return attended.toLong() * SCALE >= requirement.basisPoints.toLong() * conducted
    }

    /**
     * How many further classes can be missed consecutively while staying at or above [requirement].
     *
     * Returns 0 when already below the requirement, and [Int.MAX_VALUE] when requirement is 0%.
     */
    fun safeMisses(attended: Int, conducted: Int, requirement: Percent): Int {
        val bp = requirement.basisPoints.toLong()
        if (bp <= 0L) return Int.MAX_VALUE
        val slack = attended.toLong() * SCALE - bp * conducted
        if (slack < 0L) return 0
        return (slack / bp).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * How many further classes must be attended consecutively to reach [target].
     *
     * Returns `0` when target is already met, and `null` when unreachable (e.g. 100% after an absence).
     */
    fun classesNeededToReach(attended: Int, conducted: Int, target: Percent): Int? {
        if (meetsRequirement(attended, conducted, target)) return 0
        val bp = target.basisPoints.toLong()
        if (bp >= SCALE) return null // 100% with an existing absence is impossible
        val deficit = bp * conducted - attended.toLong() * SCALE
        val perClassGain = SCALE - bp
        val n = (deficit + perClassGain - 1) / perClassGain // ceiling division
        return n.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Evaluates attendance health against [requirement] and [cautionThreshold].
     */
    fun health(
        attended: Int,
        conducted: Int,
        requirement: Percent,
        cautionThreshold: Int = 1,
    ): AttendanceHealth = when {
        conducted <= 0 -> AttendanceHealth.NO_DATA
        !meetsRequirement(attended, conducted, requirement) -> AttendanceHealth.CRITICAL
        safeMisses(attended, conducted, requirement) <= cautionThreshold -> AttendanceHealth.CAUTION
        else -> AttendanceHealth.SAFE
    }

    /**
     * Direction of recent attendance compared to lifetime average over [windowSize] sessions.
     *
     * [sessions] must be ordered oldest-first.
     */
    fun trend(
        sessions: List<AttendanceSession>,
        windowSize: Int,
        counts: AttendanceCounts,
    ): AttendanceTrend {
        val bearing = sessions.filter {
            it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.ABSENT
        }
        if (bearing.size < windowSize || counts.conducted < windowSize) return AttendanceTrend.UNKNOWN
        val window = bearing.takeLast(windowSize)
        val windowPresent = window.count { it.status == AttendanceStatus.PRESENT }
        val recent = Percent.ofRatio(windowPresent, window.size) ?: return AttendanceTrend.UNKNOWN
        val overall = counts.percent ?: return AttendanceTrend.UNKNOWN
        val delta = recent.basisPoints - overall.basisPoints
        return when {
            delta > STEADY_BAND_BP -> AttendanceTrend.IMPROVING
            delta < -STEADY_BAND_BP -> AttendanceTrend.DECLINING
            else -> AttendanceTrend.STEADY
        }
    }

    /** Difference within +/- 5 percentage points (500 bp) around lifetime average reads as steady. */
    const val STEADY_BAND_BP = 500
}
