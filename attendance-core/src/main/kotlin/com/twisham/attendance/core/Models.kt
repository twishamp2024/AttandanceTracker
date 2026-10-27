package com.twisham.attendance.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Whether the student attended a session.
 *
 * See [AttendanceCalculator] and [AttendanceCounts] for how each status feeds percentages.
 */
enum class AttendanceStatus {
    /** The session exists but has not been answered yet. Never counted in percentages. */
    NOT_MARKED,

    /** Attended. Counts towards attended (numerator) and conducted (denominator). */
    PRESENT,

    /** Did not attend. Counts towards conducted only (denominator). */
    ABSENT,

    /** Official/medical leave. Behaviour controlled by [ExcusedPolicy]. */
    EXCUSED,

    /** The class did not happen. Never counted in either numerator or denominator. */
    CANCELLED;

    /** True for the two statuses a one-tap action produces. */
    val isPrimaryAnswer: Boolean get() = this == PRESENT || this == ABSENT
}

/**
 * How excused absences affect attendance calculations.
 */
enum class ExcusedPolicy {
    /**
     * Default. An excused session is excluded from both numerator and denominator,
     * as if the class never occurred for the student.
     */
    IGNORE,

    /** An excused session counts as attended and conducted. */
    COUNT_AS_PRESENT,
}

/** Traffic-light health classification of attendance against a requirement. */
enum class AttendanceHealth {
    /** No attendance-bearing session has been recorded yet. */
    NO_DATA,

    /** At or above requirement with comfortable margin. */
    SAFE,

    /** At or above requirement, but with little or no margin left. */
    CAUTION,

    /** Below requirement. */
    CRITICAL,
}

/** Direction of recent attendance compared to the lifetime average. */
enum class AttendanceTrend {
    IMPROVING,
    STEADY,
    DECLINING,
    UNKNOWN,
}

/**
 * How a class session came to exist.
 */
enum class SessionKind {
    /** Regular scheduled class. */
    SCHEDULED,

    /** Additional class conducted on top of the regular schedule. */
    EXTRA,

    /** Rescheduled session moved to a different date or time. */
    RESCHEDULED,
}

/**
 * Common representation of an attendance session.
 */
interface AttendanceSession {
    val date: LocalDate
    val startTime: LocalTime
    val status: AttendanceStatus
    val kind: SessionKind get() = SessionKind.SCHEDULED
    val startDateTime: LocalDateTime get() = date.atTime(startTime)
    fun canMarkAttendance(now: LocalDateTime): Boolean = AttendanceEligibility.canMarkAttendance(startDateTime, now)
}

/**
 * Lightweight immutable implementation of [AttendanceSession].
 */
data class SimpleAttendanceSession(
    override val date: LocalDate,
    override val startTime: LocalTime,
    override val status: AttendanceStatus,
    override val kind: SessionKind = SessionKind.SCHEDULED,
) : AttendanceSession

/** Projection of future consecutive attendances required to reach a specific target. */
data class TargetProjection(
    val target: Percent,
    /** Null when the target cannot be reached by attending future classes. */
    val classesNeeded: Int?,
    val alreadyMet: Boolean,
) {
    val isImpossible: Boolean get() = classesNeeded == null && !alreadyMet
}
