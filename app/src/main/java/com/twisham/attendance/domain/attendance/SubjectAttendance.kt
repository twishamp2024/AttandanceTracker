package com.twisham.attendance.domain.attendance

import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.AttendanceTrend
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Subject

/** How many classes are needed to hit a particular target, or why that is hopeless. */
data class TargetProjection(
    val target: Percent,
    /** Null when the target cannot be reached by attending future classes. */
    val classesNeeded: Int?,
    val alreadyMet: Boolean,
) {
    val isImpossible: Boolean get() = classesNeeded == null && !alreadyMet
}

/**
 * Everything the UI needs about one subject, computed once in the domain layer.
 *
 * Compose code reads these fields and formats them; it never does attendance
 * arithmetic itself. That keeps the maths in plain unit tests.
 */
data class SubjectAttendance(
    val subject: Subject,
    val counts: AttendanceCounts,
    val health: AttendanceHealth,
    val safeMisses: Int,
    /** Classes needed to climb back to the subject's own requirement. 0 when met. */
    val classesToRequirement: Int?,
    val stretchTargets: List<TargetProjection>,
    val trend: AttendanceTrend,
) {
    val percent: Percent? get() = counts.percent
    val requirement: Percent get() = subject.requirement
    val hasUnlimitedMisses: Boolean get() = safeMisses == Int.MAX_VALUE
}

/**
 * Semester-wide summary.
 *
 * [percent] is a **pooled** figure: total attended across every active subject over
 * total conducted. The obvious alternative — averaging the per-subject percentages —
 * would weight a two-class seminar the same as a forty-class lecture course, which
 * is not what "my attendance" means to a student or to a college office.
 *
 * Because subjects can carry different requirements, a single overall number cannot
 * say whether the student is safe. That is why the counts of safe/caution/critical
 * subjects travel alongside it and are shown together in the UI.
 */
data class OverallAttendance(
    val counts: AttendanceCounts,
    val subjectCount: Int,
    val safeCount: Int,
    val cautionCount: Int,
    val criticalCount: Int,
    val unmarkedSessions: Int,
) {
    val percent: Percent? get() = counts.percent
    val needsAttention: Boolean get() = criticalCount > 0 || cautionCount > 0
}
