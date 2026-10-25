package com.twisham.attendance.domain.model

/**
 * A percentage held as **basis points** (1/100th of a percent), so 75% is 7500.
 *
 * Attendance requirements are compared against ratios of small integers many times
 * per screen. Doing that in [Double] invites the classic failure where a student who
 * is exactly at 75% is told they are below it because 0.75 is not representable.
 * Keeping the requirement as an integer lets every comparison in
 * `AttendanceCalculator` be done in exact [Long] arithmetic, with rounding applied
 * only at the display boundary.
 *
 * Basis points also give us half-percent requirements (e.g. 77.5%) for free.
 */
@JvmInline
value class Percent(val basisPoints: Int) : Comparable<Percent> {

    init {
        require(basisPoints in 0..MAX_BP) { "Percent out of range: $basisPoints bp" }
    }

    /** Lossy; for display and charts only, never for comparisons. */
    val asDouble: Double get() = basisPoints / 100.0

    /** e.g. "75%" or "77.5%" — trailing ".0" is dropped. */
    fun format(): String {
        val whole = basisPoints / 100
        val frac = basisPoints % 100
        return when {
            frac == 0 -> "$whole%"
            frac % 10 == 0 -> "$whole.${frac / 10}%"
            else -> "$whole.${frac.toString().padStart(2, '0')}%"
        }
    }

    /** Fixed number of decimals, half-up, e.g. `formatFixed(1)` -> "76.9%". */
    fun formatFixed(decimals: Int): String {
        require(decimals in 0..2)
        val divisor = when (decimals) { 0 -> 100; 1 -> 10; else -> 1 }
        val rounded = (basisPoints + divisor / 2) / divisor
        return when (decimals) {
            0 -> "$rounded%"
            1 -> "${rounded / 10}.${rounded % 10}%"
            else -> "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')}%"
        }
    }

    override fun compareTo(other: Percent): Int = basisPoints.compareTo(other.basisPoints)

    override fun toString(): String = format()

    companion object {
        const val MAX_BP = 10_000

        val ZERO = Percent(0)
        val FULL = Percent(MAX_BP)

        /** Builds from a whole percentage, e.g. `Percent.ofWhole(75)`. */
        fun ofWhole(percent: Int): Percent = Percent(percent.coerceIn(0, 100) * 100)

        /** Builds from a possibly fractional percentage, rounding half-up to 2 dp. */
        fun of(percent: Double): Percent =
            Percent(Math.round(percent.coerceIn(0.0, 100.0) * 100).toInt())

        /**
         * Exact ratio -> percentage, rounded half-up to two decimal places without
         * ever touching floating point.
         */
        fun ofRatio(numerator: Int, denominator: Int): Percent? {
            if (denominator <= 0) return null
            val n = numerator.toLong() * MAX_BP * 2
            val d = denominator.toLong() * 2
            return Percent(((n + denominator) / d).toInt().coerceIn(0, MAX_BP))
        }
    }
}
