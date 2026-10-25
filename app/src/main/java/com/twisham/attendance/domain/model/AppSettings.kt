package com.twisham.attendance.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Everything the user can tune. Persisted as a single row so it can be observed as
 * a [kotlinx.coroutines.flow.Flow] and exported alongside the attendance data.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val excusedPolicy: ExcusedPolicy = ExcusedPolicy.IGNORE,
    /**
     * A subject at or above its requirement is shown as CAUTION rather than SAFE
     * when it has this many safe misses left or fewer. Default 1: being one missed
     * class away from dropping below the line is worth a warning colour.
     */
    val cautionSafeMissThreshold: Int = 1,
    /** Size of the recent window used for the improving/declining indicator. */
    val trendWindowSize: Int = 6,
    val remindersEnabled: Boolean = false,
    /** Time of the daily "you have unmarked classes" nudge. */
    val reminderTime: LocalTime = LocalTime.of(18, 30),
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    /** Extra targets offered on the subject screen, above the requirement. */
    val stretchTargets: List<Percent> = listOf(
        Percent.ofWhole(80), Percent.ofWhole(85), Percent.ofWhole(90),
    ),
)
