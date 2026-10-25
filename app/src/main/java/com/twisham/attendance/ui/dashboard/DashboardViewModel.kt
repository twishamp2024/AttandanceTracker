package com.twisham.attendance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.attendance.OverallAttendance
import com.twisham.attendance.domain.attendance.SubjectAttendance
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.ui.containerViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val semester: Semester? = null,
        val overall: OverallAttendance? = null,
        val subjects: List<SubjectAttendance> = emptyList(),
        val settings: AppSettings = AppSettings(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = container.semesterRepository.observeActive()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(UiState(loading = false))
            } else {
                container.observeSemesterAttendance(semester.id).map { result ->
                    UiState(
                        loading = false,
                        semester = semester,
                        overall = result.overall,
                        subjects = result.subjects,
                        settings = result.settings,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    companion object {
        val Factory = containerViewModelFactory { DashboardViewModel(it) }
    }
}
