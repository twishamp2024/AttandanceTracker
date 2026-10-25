package com.twisham.attendance.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.twisham.attendance.ui.MainViewModel
import com.twisham.attendance.ui.onboarding.OnboardingScreen

/**
 * Root layout.
 *
 * The navigation affordance adapts to width rather than to a hard-coded device
 * class: below 600dp a bottom bar (thumb-reachable on a phone), at or above it a
 * navigation rail, which is what a tablet or an unfolded foldable wants and also
 * what a phone in landscape wants, since a bottom bar there eats scarce height.
 */
@Composable
fun AttendanceAppRoot(state: MainViewModel.UiState) {
    val navController = rememberNavController()

    if (state.loading) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { }
        return
    }

    // Nothing set up yet: send the user straight into creating a semester instead of
    // showing four empty tabs.
    if (!state.hasAnySemester) {
        OnboardingScreen()
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            // The rail is inset away from the display cutout, which leaves the strip
            // beside it unpainted — on a notched phone in landscape that shows up as
            // a bright band behind the status bar. Painting the row fixes it.
            Row(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppNavigationRail(navController)
                Scaffold(
                    modifier = Modifier
                        .weight(1f)
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                ) { padding ->
                    AttendanceNavHost(
                        navController = navController,
                        state = state,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = { AppBottomBar(navController) },
            ) { padding ->
                AttendanceNavHost(
                    navController = navController,
                    state = state,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val destinations = remember { TopLevelDestination.entries }

    // The bar is hidden on detail screens so they get the full height.
    val onTopLevel = destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
    if (!onTopLevel) return

    NavigationBar {
        destinations.forEach { destination ->
            val selected =
                currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun AppNavigationRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
            ),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected =
                currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}

/**
 * Standard tab switching: single instance per tab, state preserved, and the back
 * stack never grows by repeatedly tapping between tabs.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
