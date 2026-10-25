package com.twisham.attendance.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Semester
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
import java.time.YearMonth

/** How a single day reads at a glance in the month grid. */
enum class DayMood { NONE, ALL_PRESENT, MIXED, MOSTLY_ABSENT, UNMARKED, CANCELLED_ONLY }

data class DaySummary(
    val date: LocalDate,
    val mood: DayMood,
    val total: Int,
    val present: Int,
    val absent: Int,
    val unmarked: Int,
) {
    /** Spoken description; the grid cells are far too small to carry text. */
    val spoken: String
        get() = when (mood) {
            DayMood.NONE -> "no classes"
            DayMood.CANCELLED_ONLY -> "all classes cancelled"
            DayMood.UNMARKED -> "$unmarked unmarked"
            DayMood.ALL_PRESENT -> "present for all $total"
            DayMood.MIXED -> "$present present, $absent absent"
            DayMood.MOSTLY_ABSENT -> "$absent absent of $total"
        }
}

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val semester: Semester? = null,
        val month: YearMonth = YearMonth.now(),
        val today: LocalDate = LocalDate.now(),
        val days: Map<LocalDate, DaySummary> = emptyMap(),
        val selectedDate: LocalDate? = null,
        val selectedSessions: List<ClassSession> = emptyList(),
        val subjectsById: Map<Long, Subject> = emptyMap(),
        val message: String? = null,
    )

    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val messages = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> =
        combine(container.semesterRepository.observeActive(), month) { s, m -> s to m }
            .flatMapLatest { (semester, visibleMonth) ->
                if (semester == null) {
                    flowOf(UiState(loading = false, month = visibleMonth))
                } else {
                    // A whole month plus the leading/trailing week the grid shows.
                    val from = visibleMonth.atDay(1).minusDays(7)
                    val to = visibleMonth.atEndOfMonth().plusDays(7)
                    combine(
                        container.sessionRepository.observeInRange(semester.id, from, to),
                        container.subjectRepository.observeForSemester(semester.id),
                        selected,
                        messages,
                    ) { sessions, subjects, selectedDate, message ->
                        val byDate = sessions.groupBy { it.date }
                        UiState(
                            loading = false,
                            semester = semester,
                            month = visibleMonth,
                            today = container.today(),
                            days = byDate.mapValues { (date, daySessions) -> summarise(date, daySessions) },
                            selectedDate = selectedDate,
                            selectedSessions = selectedDate
                                ?.let { byDate[it].orEmpty() }
                                ?.sortedWith(compareBy({ it.startTime }, { it.id }))
                                ?: emptyList(),
                            subjectsById = subjects.associateBy { it.id },
                            message = message,
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * Classification for one day. "Mostly absent" is defined as missing at least half
     * the attendance-bearing classes, so a single missed class in a five-class day
     * does not paint the whole day red.
     */
    private fun summarise(date: LocalDate, sessions: List<ClassSession>): DaySummary {
        val present = sessions.count { it.status == AttendanceStatus.PRESENT }
        val absent = sessions.count { it.status == AttendanceStatus.ABSENT }
        val excused = sessions.count { it.status == AttendanceStatus.EXCUSED }
        val cancelled = sessions.count { it.status == AttendanceStatus.CANCELLED }
        val unmarked = sessions.count { it.status == AttendanceStatus.NOT_MARKED }
        val bearing = present + absent + excused

        val mood = when {
            sessions.isEmpty() -> DayMood.NONE
            cancelled == sessions.size -> DayMood.CANCELLED_ONLY
            unmarked > 0 && bearing == 0 -> DayMood.UNMARKED
            absent == 0 && bearing > 0 -> DayMood.ALL_PRESENT
            bearing > 0 && absent * 2 >= bearing -> DayMood.MOSTLY_ABSENT
            bearing > 0 -> DayMood.MIXED
            else -> DayMood.NONE
        }

        return DaySummary(date, mood, sessions.size, present, absent, unmarked)
    }

    fun showMonth(value: YearMonth) {
        month.value = value
        viewModelScope.launch {
            val semester = container.semesterRepository.getActive() ?: return@launch
            // Expand the timetable for the month being looked at, so browsing back
            // through the semester fills in the days that were never opened.
            runCatching {
                container.sessionRepository.ensureMaterialised(
                    semester,
                    value.atDay(1),
                    minOf(value.atEndOfMonth(), container.today()),
                )
            }
        }
    }

    fun previousMonth() = showMonth(month.value.minusMonths(1))
    fun nextMonth() = showMonth(month.value.plusMonths(1))
    fun select(date: LocalDate?) { selected.value = date }

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

    init { showMonth(YearMonth.now()) }

    companion object {
        val Factory = containerViewModelFactory { CalendarViewModel(it) }
    }
}
