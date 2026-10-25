package com.twisham.attendance.domain.model

/**
 * What kind of teaching a subject is. Affects nothing in the maths, but colleges
 * frequently set different attendance requirements for labs vs theory, and the
 * timetable reads much better when the two are visually distinguishable.
 */
enum class SubjectType { THEORY, LAB, TUTORIAL, OTHER }

/**
 * How a session came to exist.
 *
 * This is deliberately kept *separate* from [AttendanceStatus]. The original
 * specification folded "EXTRA_CLASS" and "RESCHEDULED" into the status enum, but
 * those describe the provenance of a session, not whether the student turned up.
 * With a single enum it is impossible to express "an extra class that I attended",
 * which is exactly the case that matters for the maths. Keeping the two axes
 * orthogonal removes a whole family of data bugs.
 */
enum class SessionKind {
    /** Generated from the weekly timetable. */
    SCHEDULED,

    /** An additional class the teacher added on top of the timetable. */
    EXTRA,

    /** A scheduled class that was moved to a different date/time. */
    RESCHEDULED,
}

/**
 * Whether the student attended a session.
 *
 * See `AttendanceCalculator` for exactly how each value feeds the percentage.
 */
enum class AttendanceStatus {
    /** The session exists but has not been answered yet. Never counted. */
    NOT_MARKED,

    /** Attended. Counts towards attended and conducted. */
    PRESENT,

    /** Did not attend. Counts towards conducted only. */
    ABSENT,

    /** Official/medical leave. Behaviour controlled by [ExcusedPolicy]. */
    EXCUSED,

    /** The class did not happen. Never counted, in either numerator or denominator. */
    CANCELLED;

    /** True for the two statuses a one-tap action produces. */
    val isPrimaryAnswer: Boolean get() = this == PRESENT || this == ABSENT
}

/**
 * How excused absences affect the percentage. Institutions genuinely differ here,
 * so this is a setting rather than a hard-coded rule.
 */
enum class ExcusedPolicy {
    /**
     * Default. An excused session is removed from both numerator and denominator,
     * i.e. it is as if the class never happened for this student.
     */
    IGNORE,

    /** An excused session counts as if the student were present. */
    COUNT_AS_PRESENT,
}

/** Traffic-light health of a subject. Always paired with text and an icon in the UI. */
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

/** Direction of recent attendance compared to the subject's lifetime average. */
enum class AttendanceTrend { IMPROVING, STEADY, DECLINING, UNKNOWN }

/** Light/dark/system theme selection. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Lifecycle of a semester. Derived from dates, but overridable by archiving. */
enum class SemesterState { ACTIVE, ARCHIVED, UPCOMING, FINISHED }
