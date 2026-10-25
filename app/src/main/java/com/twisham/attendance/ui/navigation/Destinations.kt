package com.twisham.attendance.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Routes.
 *
 * Plain string routes are used rather than the type-safe variant: the argument list
 * here is two optional Longs, and the generated-serializer approach would cost more
 * ceremony than it saves.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val TODAY = "today"
    const val TIMETABLE = "timetable"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val SEMESTERS = "semesters"
    const val HISTORY = "history"

    const val SUBJECT_ID = "subjectId"
    const val SEMESTER_ID = "semesterId"

    const val SUBJECT_DETAIL = "subject/{$SUBJECT_ID}"
    fun subjectDetail(id: Long) = "subject/$id"

    const val SUBJECT_EDIT = "subjectEdit?$SUBJECT_ID={$SUBJECT_ID}"
    fun subjectEdit(id: Long? = null) = "subjectEdit?$SUBJECT_ID=${id ?: -1L}"

    const val SEMESTER_EDIT = "semesterEdit?$SEMESTER_ID={$SEMESTER_ID}"
    fun semesterEdit(id: Long? = null) = "semesterEdit?$SEMESTER_ID=${id ?: -1L}"
}

/**
 * The four top-level tabs.
 *
 * "Today" sits first because the single most frequent action in the app is marking
 * the class that just finished, and burying that behind the dashboard would cost a
 * tap on every single use. The dashboard is one tap away and also carries its own
 * quick-mark strip, so neither audience loses.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TODAY(Routes.TODAY, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    DASHBOARD(Routes.DASHBOARD, "Overview", Icons.Filled.Insights, Icons.Outlined.Insights),
    TIMETABLE(Routes.TIMETABLE, "Timetable", Icons.Filled.GridView, Icons.Outlined.GridView),
    CALENDAR(Routes.CALENDAR, "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
}
