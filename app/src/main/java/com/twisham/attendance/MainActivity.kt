package com.twisham.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.ui.MainViewModel
import com.twisham.attendance.ui.navigation.AttendanceAppRoot
import com.twisham.attendance.ui.theme.AttendanceTheme

/**
 * The only Activity.
 *
 * `configChanges` in the manifest is deliberately *not* used to dodge recreation —
 * every screen keeps its state in a ViewModel and Compose's own saveable state, so
 * rotation and theme changes survive by design rather than by suppression.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()

            AttendanceTheme(themeMode = state.settings.themeMode) {
                AttendanceAppRoot(state = state)
            }
        }
    }
}
