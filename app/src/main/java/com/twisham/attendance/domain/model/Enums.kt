package com.twisham.attendance.domain.model

/**
 * Re-exported from [com.twisham.attendance.core] for backward compatibility.
 */
typealias AttendanceStatus = com.twisham.attendance.core.AttendanceStatus
typealias ExcusedPolicy = com.twisham.attendance.core.ExcusedPolicy
typealias AttendanceHealth = com.twisham.attendance.core.AttendanceHealth
typealias AttendanceTrend = com.twisham.attendance.core.AttendanceTrend
typealias SessionKind = com.twisham.attendance.core.SessionKind

/**
 * What kind of teaching a subject is.
 */
enum class SubjectType { THEORY, LAB, TUTORIAL, OTHER }

/** Light/dark/system theme selection. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Lifecycle of a semester. Derived from dates, but overridable by archiving. */
enum class SemesterState { ACTIVE, ARCHIVED, UPCOMING, FINISHED }
