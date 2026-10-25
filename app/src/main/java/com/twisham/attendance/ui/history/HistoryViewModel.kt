package com.twisham.attendance.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.ui.containerViewModelFactory
import com.twisham.attendance.ui.today.friendly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Time windows offered in the history filter. */
enum class HistoryRange(val label: String) {
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    LAST_30("Last 30 days"),
    ALL("All"),
    ;

    fun startFrom(today: LocalDate, weekStart: java.time.DayOfWeek): LocalDate? = when (this) {
        THIS_WEEK -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
        THIS_MONTH -> today.withDayOfMonth(1)
        LAST_30 -> today.minusDays(29)
        ALL -> null
    }
}

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    data class Filters(
        val subjectId: Long? = null,
        val statuses: Set<AttendanceStatus> = emptySet(),
        val range: HistoryRange = HistoryRange.THIS_MONTH,
    ) {
        val isFiltered: Boolean
            get() = subjectId != null || statuses.isNotEmpty() || range != HistoryRange.THIS_MONTH
    }

    data class UiState(
        val loading: Boolean = true,
        val sessions: List<ClassSession> = emptyList(),
        val subjects: List<Subject> = emptyList(),
        val subjectsById: Map<Long, Subject> = emptyMap(),
        val filters: Filters = Filters(),
        val today: LocalDate = LocalDate.now(),
        val totalBeforeFilter: Int = 0,
        val message: String? = null,
    )

    private val filters = MutableStateFlow(Filters())
    private val messages = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = container.semesterRepository.observeActive()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(UiState(loading = false))
            } else {
                combine(
                    container.sessionRepository.observeForSemester(semester.id),
                    container.subjectRepository.observeForSemester(semester.id),
                    container.settingsRepository.observe(),
                    filters,
                    messages,
                ) { sessions, subjects, settings, activeFilters, message ->
                    val today = container.today()
                    val from = activeFilters.range.startFrom(today, settings.weekStartsOn)

                    val filtered = sessions.asSequence()
                        .filter { from == null || !it.date.isBefore(from) }
                        .filter { activeFilters.subjectId == null || it.subjectId == activeFilters.subjectId }
                        .filter { activeFilters.statuses.isEmpty() || it.status in activeFilters.statuses }
                        .sortedWith(
                            compareByDescending<ClassSession> { it.date }
                                .thenByDescending { it.startTime },
                        )
                        .toList()

                    UiState(
                        loading = false,
                        sessions = filtered,
                        subjects = subjects,
                        subjectsById = subjects.associateBy { it.id },
                        filters = activeFilters,
                        today = today,
                        totalBeforeFilter = sessions.size,
                        message = message,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setSubject(id: Long?) { filters.value = filters.value.copy(subjectId = id) }
    fun setRange(range: HistoryRange) { filters.value = filters.value.copy(range = range) }

    fun toggleStatus(status: AttendanceStatus) {
        val current = filters.value.statuses
        filters.value = filters.value.copy(
            statuses = if (status in current) current - status else current + status,
        )
    }

    fun clearFilters() { filters.value = Filters() }

    fun setStatus(session: ClassSession, status: AttendanceStatus) {
        viewModelScope.launch {
            runCatching { container.sessionRepository.setStatus(session.id, status) }
                .onFailure { messages.value = it.friendly() }
        }
    }

    fun setNote(session: ClassSession, note: String?) {
        viewModelScope.launch {
            runCatching { container.sessionRepository.setNote(session.id, note) }
                .onFailure { messages.value = it.friendly() }
        }
    }

    fun delete(session: ClassSession) {
        viewModelScope.launch {
            runCatching { container.sessionRepository.delete(session) }
                .onFailure { messages.value = it.friendly() }
        }
    }

    fun consumeMessage() { messages.value = null }

    companion object {
        val Factory = containerViewModelFactory { HistoryViewModel(it) }
    }
}
