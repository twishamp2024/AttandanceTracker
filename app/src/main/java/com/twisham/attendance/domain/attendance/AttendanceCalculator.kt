package com.twisham.attendance.domain.attendance

import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.AttendanceTrend
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent

/**
 * All attendance mathematics lives here.
 *
 * ## Why integers
 *
 * Every question this app answers reduces to comparing `attended / conducted`
 * against `requirement`. Done in [Double] that comparison is unreliable at exactly
 * the point where it matters most — a student sitting precisely on 75% must be told
 * they are safe, not that they are 0.0000001 short. So the requirement is carried as
 * basis points ([Percent]) and every comparison is cross-multiplied into [Long]:
 *
 * ```
 * attended / conducted >= bp / 10000     <=>     attended * 10000 >= bp * conducted
 * ```
 *
 * The left side never loses precision, and `attended * 10000` cannot overflow a Long
 * for any realistic number of classes.
 */
object AttendanceCalculator {

    private const val SCALE = Percent.MAX_BP.toLong() // 10_000

    /** True when `attended/conducted` is at or above [requirement]. Exact. */
    fun meetsRequirement(attended: Int, conducted: Int, requirement: Percent): Boolean {
        if (conducted <= 0) return true // nothing conducted: not yet failing
        return attended.toLong() * SCALE >= requirement.basisPoints.toLong() * conducted
    }

    /**
     * How many further attendance-bearing classes can be missed **in a row** while
     * staying at or above [requirement].
     *
     * Solves for the largest `k >= 0` satisfying
     * `attended * 10000 >= requirement * (conducted + k)`, which rearranges to
     * `k <= (attended * 10000 - requirement * conducted) / requirement`.
     *
     * Returns 0 when already below the line (you cannot afford to miss anything) and
     * [Int.MAX_VALUE] when the requirement is 0% (nothing can ever break it).
     */
    fun safeMisses(attended: Int, conducted: Int, requirement: Percent): Int {
        val bp = requirement.basisPoints.toLong()
        if (bp <= 0L) return Int.MAX_VALUE
        val slack = attended.toLong() * SCALE - bp * conducted
        if (slack < 0L) return 0
        return (slack / bp).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * How many further classes must be attended **consecutively** to reach [target].
     *
     * Solves for the smallest `n >= 0` satisfying
     * `(attended + n) * 10000 >= target * (conducted + n)`, i.e.
     * `n >= (target * conducted - attended * 10000) / (10000 - target)`.
     *
     * Returns `0` when the target is already met, and `null` when it is unreachable.
     * The only unreachable case is a target of 100% once a single class has been
     * missed: attending every remaining class leaves the past absences in the
     * denominator forever, so the ratio approaches but never reaches 1.
     */
    fun classesNeededToReach(attended: Int, conducted: Int, target: Percent): Int? {
        if (meetsRequirement(attended, conducted, target)) return 0
        val bp = target.basisPoints.toLong()
        if (bp >= SCALE) return null // 100% with an existing absence: impossible
        val deficit = bp * conducted - attended.toLong() * SCALE
        val perClassGain = SCALE - bp
        val n = (deficit + perClassGain - 1) / perClassGain // ceiling division
        return n.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Classification shown on cards. Colour is never the only signal in the UI; this
     * value also drives the icon and the sentence underneath.
     *
     * - [AttendanceHealth.NO_DATA] – nothing conducted yet.
     * - [AttendanceHealth.CRITICAL] – below the requirement.
     * - [AttendanceHealth.CAUTION] – meets it, but with `safeMisses <= threshold`.
     * - [AttendanceHealth.SAFE] – meets it with room to spare.
     *
     * [cautionThreshold] is a user setting rather than a magic number, because how
     * much margin feels "comfortable" is a personal judgement.
     */
    fun health(
        attended: Int,
        conducted: Int,
        requirement: Percent,
        cautionThreshold: Int,
    ): AttendanceHealth = when {
        conducted <= 0 -> AttendanceHealth.NO_DATA
        !meetsRequirement(attended, conducted, requirement) -> AttendanceHealth.CRITICAL
        safeMisses(attended, conducted, requirement) <= cautionThreshold -> AttendanceHealth.CAUTION
        else -> AttendanceHealth.SAFE
    }

    /**
     * Compares the most recent [windowSize] attendance-bearing sessions against the
     * lifetime ratio. A difference smaller than [STEADY_BAND_BP] is reported as
     * steady so the arrow does not flicker on every single mark.
     *
     * [sessions] must be ordered oldest-first.
     */
    fun trend(
        sessions: List<ClassSession>,
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

    /** +/- 5 percentage points around the lifetime average still reads as "steady". */
    const val STEADY_BAND_BP = 500
}
