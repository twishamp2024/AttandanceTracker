package com.twisham.attendance.util

import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.SubjectType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Display formatting. Kept out of the composables so the same date never renders two
 * different ways on two different screens.
 */
object Formatters {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun time(value: LocalTime): String = value.format(timeFormatter)

    fun timeRange(from: LocalTime, to: LocalTime): String = "${time(from)}–${time(to)}"

    fun dayMonth(date: LocalDate): String = date.format(dayMonthFormatter)

    fun fullDate(date: LocalDate): String = date.format(fullDateFormatter)

    fun monthYear(date: LocalDate): String = date.format(monthYearFormatter)

    fun dayName(date: LocalDate, short: Boolean = false): String =
        date.dayOfWeek.getDisplayName(
            if (short) TextStyle.SHORT else TextStyle.FULL,
            Locale.getDefault(),
        )

    /** "Today" / "Yesterday" / "Tomorrow" where it helps, otherwise a real date. */
    fun relativeDate(date: LocalDate, today: LocalDate): String =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> "${dayName(date, short = true)}, ${dayMonth(date)}"
        }

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }

    /** Plural-safe class counting, used in a lot of sentences. */
    fun classes(count: Int): String = if (count == 1) "1 class" else "$count classes"
}

val AttendanceStatus.label: String
    get() = when (this) {
        AttendanceStatus.NOT_MARKED -> "Not marked"
        AttendanceStatus.PRESENT -> "Present"
        AttendanceStatus.ABSENT -> "Absent"
        AttendanceStatus.EXCUSED -> "Excused"
        AttendanceStatus.CANCELLED -> "Cancelled"
    }

/** Short explanation of how a status affects the percentage, shown in pickers. */
val AttendanceStatus.effectDescription: String
    get() = when (this) {
        AttendanceStatus.NOT_MARKED -> "Not counted yet"
        AttendanceStatus.PRESENT -> "Counts as attended"
        AttendanceStatus.ABSENT -> "Counts against you"
        AttendanceStatus.EXCUSED -> "Handled by your excused-absence setting"
        AttendanceStatus.CANCELLED -> "Does not count at all"
    }

val SubjectType.label: String
    get() = when (this) {
        SubjectType.THEORY -> "Theory"
        SubjectType.LAB -> "Lab"
        SubjectType.TUTORIAL -> "Tutorial"
        SubjectType.OTHER -> "Other"
    }

val SessionKind.label: String
    get() = when (this) {
        SessionKind.SCHEDULED -> "Scheduled"
        SessionKind.EXTRA -> "Extra class"
        SessionKind.RESCHEDULED -> "Rescheduled"
    }
