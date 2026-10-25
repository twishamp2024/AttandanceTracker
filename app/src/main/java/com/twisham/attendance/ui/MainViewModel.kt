package com.twisham.attendance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.Semester
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-level state: the chosen theme, the active semester, and whether the user has
 * set anything up yet.
 *
 * It also owns the one piece of background work that must happen regardless of which
 * screen is open — expanding the timetable into sessions around today, so that
 * opening the app on a Monday morning already shows Monday's classes.
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val activeSemester: Semester? = null,
        val hasAnySemester: Boolean = true,
        val loading: Boolean = true,
    )

    val uiState: StateFlow<UiState> = combine(
        container.settingsRepository.observe(),
        container.semesterRepository.observeActive(),
        container.semesterRepository.observeAll(),
    ) { settings, active, all ->
        UiState(
            settings = settings,
            // Falling back to any semester keeps the app usable if the active flag is
            // ever lost, rather than showing onboarding over real data.
            activeSemester = active ?: all.firstOrNull { !it.isArchived } ?: all.firstOrNull(),
            hasAnySemester = all.isNotEmpty(),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        // Keep a rolling window of sessions materialised around today. A window
        // rather than the whole semester keeps first launch fast, and re-running it
        // is cheap because already-expanded days are skipped.
        viewModelScope.launch {
            container.semesterRepository.observeActive()
                .filterNotNull()
                .map { it.id }
                .distinctUntilChanged()
                .collect { _ ->
                    val semester = container.semesterRepository.getActive() ?: return@collect
                    val today = container.today()
                    runCatching {
                        container.sessionRepository.ensureMaterialised(
                            semester,
                            today.minusWeeks(2),
                            today.plusWeeks(2),
                        )
                    }
                }
        }

        // Keep the reminder alarm in step with the setting.
        viewModelScope.launch {
            container.settingsRepository.observe()
                .map { it.remindersEnabled to it.reminderTime }
                .distinctUntilChanged()
                .collect {
                    runCatching {
                        container.reminderScheduler.apply(container.settingsRepository.get())
                    }
                }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { MainViewModel(it) }
    }
}
