package com.twisham.attendance.core

/**
 * Raw tallies for a set of sessions before a requirement threshold is applied.
 *
 * ## Rules
 *
 * | Status      | Attended (numerator) | Conducted (denominator) |
 * |-------------|----------------------|-------------------------|
 * | PRESENT     | Yes                  | Yes                     |
 * | ABSENT      | No                   | Yes                     |
 * | EXCUSED     | If policy = COUNT_AS_PRESENT | If policy = COUNT_AS_PRESENT |
 * | CANCELLED   | No                   | No                      |
 * | NOT_MARKED  | No                   | No                      |
 */
data class AttendanceCounts(
    val present: Int = 0,
    val absent: Int = 0,
    val excused: Int = 0,
    val cancelled: Int = 0,
    val notMarked: Int = 0,
    /** Attendance-bearing sessions that were extra classes. */
    val extra: Int = 0,
    /** Total session count across all statuses. */
    val total: Int = 0,
    val policy: ExcusedPolicy = ExcusedPolicy.IGNORE,
) {
    private val excusedCounts: Boolean get() = policy == ExcusedPolicy.COUNT_AS_PRESENT

    /** Classes that took place and count towards the denominator. */
    val conducted: Int get() = present + absent + if (excusedCounts) excused else 0

    /** Classes credited to the student (numerator). */
    val attended: Int get() = present + if (excusedCounts) excused else 0

    /** Attendance-bearing classes missed. */
    val missed: Int get() = absent

    /** Null when nothing has been conducted yet (0/0 is undefined). */
    val percent: Percent? get() = Percent.ofRatio(attended, conducted)

    /** Sessions that exist but are not yet marked. */
    val pending: Int get() = notMarked

    operator fun plus(other: AttendanceCounts): AttendanceCounts = AttendanceCounts(
        present = present + other.present,
        absent = absent + other.absent,
        excused = excused + other.excused,
        cancelled = cancelled + other.cancelled,
        notMarked = notMarked + other.notMarked,
        extra = extra + other.extra,
        total = total + other.total,
        policy = policy,
    )

    companion object {
        fun from(sessions: Iterable<AttendanceSession>, policy: ExcusedPolicy = ExcusedPolicy.IGNORE): AttendanceCounts {
            var present = 0
            var absent = 0
            var excused = 0
            var cancelled = 0
            var notMarked = 0
            var extra = 0
            var total = 0

            for (s in sessions) {
                total++
                when (s.status) {
                    AttendanceStatus.PRESENT -> present++
                    AttendanceStatus.ABSENT -> absent++
                    AttendanceStatus.EXCUSED -> excused++
                    AttendanceStatus.CANCELLED -> cancelled++
                    AttendanceStatus.NOT_MARKED -> notMarked++
                }
                val bearing = s.status == AttendanceStatus.PRESENT ||
                    s.status == AttendanceStatus.ABSENT ||
                    (s.status == AttendanceStatus.EXCUSED && policy == ExcusedPolicy.COUNT_AS_PRESENT)
                if (bearing && s.kind == SessionKind.EXTRA) extra++
            }

            return AttendanceCounts(
                present = present,
                absent = absent,
                excused = excused,
                cancelled = cancelled,
                notMarked = notMarked,
                extra = extra,
                total = total,
                policy = policy,
            )
        }
    }
}
