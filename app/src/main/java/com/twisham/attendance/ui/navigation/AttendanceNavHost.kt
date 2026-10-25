package com.twisham.attendance.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.twisham.attendance.ui.MainViewModel
import com.twisham.attendance.ui.calendar.CalendarScreen
import com.twisham.attendance.ui.dashboard.DashboardScreen
import com.twisham.attendance.ui.history.HistoryScreen
import com.twisham.attendance.ui.semester.SemesterEditScreen
import com.twisham.attendance.ui.semester.SemesterListScreen
import com.twisham.attendance.ui.settings.SettingsScreen
import com.twisham.attendance.ui.subject.SubjectDetailScreen
import com.twisham.attendance.ui.subject.SubjectEditScreen
import com.twisham.attendance.ui.timetable.TimetableScreen
import com.twisham.attendance.ui.today.TodayScreen

/**
 * The navigation graph.
 *
 * Transitions are a short horizontal slide for forward/back and a plain fade between
 * tabs. Motion here is a wayfinding cue, not decoration, so the durations are kept
 * under 250ms — long enough to read as a direction, short enough that marking five
 * classes in a row never feels like waiting.
 */
@Composable
fun AttendanceNavHost(
    navController: NavHostController,
    state: MainViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TODAY,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(220),
            ) + fadeIn(tween(220))
        },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(220),
            ) + fadeOut(tween(220))
        },
    ) {
        composable(
            Routes.TODAY,
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            TodayScreen(
                onOpenSubject = { navController.navigate(Routes.subjectDetail(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            Routes.DASHBOARD,
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            DashboardScreen(
                onOpenSubject = { navController.navigate(Routes.subjectDetail(it)) },
                onAddSubject = { navController.navigate(Routes.subjectEdit()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }

        composable(
            Routes.TIMETABLE,
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            TimetableScreen(
                onAddSubject = { navController.navigate(Routes.subjectEdit()) },
                onOpenSubject = { navController.navigate(Routes.subjectDetail(it)) },
            )
        }

        composable(
            Routes.CALENDAR,
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            CalendarScreen(
                onOpenSubject = { navController.navigate(Routes.subjectDetail(it)) },
            )
        }

        composable(
            route = Routes.SUBJECT_DETAIL,
            arguments = listOf(navArgument(Routes.SUBJECT_ID) { type = NavType.LongType }),
        ) { entry ->
            SubjectDetailScreen(
                subjectId = entry.arguments?.getLong(Routes.SUBJECT_ID) ?: -1L,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.subjectEdit(it)) },
            )
        }

        composable(
            route = Routes.SUBJECT_EDIT,
            arguments = listOf(
                navArgument(Routes.SUBJECT_ID) { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            SubjectEditScreen(
                subjectId = entry.arguments?.getLong(Routes.SUBJECT_ID)?.takeIf { it > 0 },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSubject = { navController.navigate(Routes.subjectDetail(it)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSemesters = { navController.navigate(Routes.SEMESTERS) },
                onOpenTimetable = {
                    navController.navigate(Routes.TIMETABLE) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.SEMESTERS) {
            SemesterListScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.semesterEdit(it)) },
                onCreate = { navController.navigate(Routes.semesterEdit()) },
            )
        }

        composable(
            route = Routes.SEMESTER_EDIT,
            arguments = listOf(
                navArgument(Routes.SEMESTER_ID) { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            SemesterEditScreen(
                semesterId = entry.arguments?.getLong(Routes.SEMESTER_ID)?.takeIf { it > 0 },
                onDone = { navController.popBackStack() },
            )
        }
    }
}
