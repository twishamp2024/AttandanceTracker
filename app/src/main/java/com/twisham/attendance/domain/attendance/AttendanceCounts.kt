package com.twisham.attendance.domain.attendance

import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.ExcusedPolicy
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.SessionKind

/**
 * Raw tallies for a set of sessions, before any requirement is applied.
 *
 * ## The rules, stated once
 *
 * | status      | counts towards attended | counts towards conducted |
 * |-------------|-------------------------|--------------------------|
 * | PRESENT     | yes                     | yes                      |
 * | ABSENT      | no                      | yes                      |
 * | EXCUSED     | only if policy = COUNT_AS_PRESENT | only if policy = COUNT_AS_PRESENT |
 * | CANCELLED   | no                      | **no**                   |
 * | NOT_MARKED  | no                      | no                       |
 *
 * A cancelled class never happened, so counting it as conducted would punish the
 * student for the teacher's absence — this is the single most common bug in
 * attendance apps, and the table above is the reason it cannot happen here.
 *
 * An unmarked session is deliberately excluded too. It represents "I have not
 * answered yet", not "I was absent"; treating silence as absence would make the
 * dashboard lie every afternoon.
 *
 * Extra classes are ordinary sessions that happen to have [SessionKind.EXTRA]. They
 * raise conducted (and attended, if present) exactly like a scheduled class does.
 */
data class AttendanceCounts(
    val present: Int = 0,
    val absent: Int = 0,
    val excused: Int = 0,
    val cancelled: Int = 0,
    val notMarked: Int = 0,
    /** Attendance-bearing sessions that were extra classes. Informational only. */
    val extra: Int = 0,
    /** Every session row seen, whatever its status. */
    val total: Int = 0,
    val policy: ExcusedPolicy = ExcusedPolicy.IGNORE,
) {
    private val excusedCounts: Boolean get() = policy == ExcusedPolicy.COUNT_AS_PRESENT

    /** Classes that actually took place and count towards the denominator. */
    val conducted: Int get() = present + absent + if (excusedCounts) excused else 0

    /** Classes credited to the student — the numerator. */
    val attended: Int get() = present + if (excusedCounts) excused else 0

    /** Attendance-bearing classes missed. */
    val missed: Int get() = absent

    /** Null when nothing has been conducted yet; 0/0 is not 0%, it is unknown. */
    val percent: Percent? get() = Percent.ofRatio(attended, conducted)

    /** Sessions that exist but still need an answer. */
    val pending: Int get() = notMarked

    operator fun plus(other: AttendanceCounts) = AttendanceCounts(
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
        fun from(sessions: List<ClassSession>, policy: ExcusedPolicy): AttendanceCounts {
            var present = 0; var absent = 0; var excused = 0
            var cancelled = 0; var notMarked = 0; var extra = 0
            for (s in sessions) {
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
                present = present, absent = absent, excused = excused,
                cancelled = cancelled, notMarked = notMarked, extra = extra,
                total = sessions.size, policy = policy,
            )
        }
    }
}
