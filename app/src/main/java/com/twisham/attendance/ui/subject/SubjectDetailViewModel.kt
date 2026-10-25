package com.twisham.attendance.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.attendance.SubjectAttendance
import com.twisham.attendance.domain.attendance.WhatIfOutcome
import com.twisham.attendance.domain.attendance.WhatIfSimulator
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.ui.components.TrendPoint
import com.twisham.attendance.ui.containerViewModelFactory
import com.twisham.attendance.ui.today.friendly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SubjectDetailViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val attendance: SubjectAttendance? = null,
        val sessions: List<ClassSession> = emptyList(),
        val trend: List<TrendPoint> = emptyList(),
        val settings: AppSettings = AppSettings(),
        val today: LocalDate = LocalDate.now(),
        val whatIfAttend: Int = 0,
        val whatIfMiss: Int = 0,
        val message: String? = null,
        val missing: Boolean = false,
    ) {
        /** Live projection. Pure arithmetic — it can never write to the database. */
        val whatIf: WhatIfOutcome?
            get() = attendance?.let {
                WhatIfSimulator.project(
                    attended = it.counts.attended,
                    conducted = it.counts.conducted,
                    requirement = it.requirement,
                    attend = whatIfAttend,
                    miss = whatIfMiss,
                    cautionThreshold = settings.cautionSafeMissThreshold,
                )
            }

        val whatIfChanged: Boolean get() = whatIfAttend > 0 || whatIfMiss > 0
    }

    private val subjectId = MutableStateFlow(-1L)
    private val whatIf = MutableStateFlow(0 to 0)
    private val messages = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = subjectId
        .flatMapLatest { id ->
            combine(
                container.observeSubjectDetail(id),
                whatIf,
                messages,
            ) { detail, (attend, miss), message ->
                if (detail == null) {
                    UiState(loading = false, missing = true, message = message)
                } else {
                    UiState(
                        loading = false,
                        attendance = detail.attendance,
                        sessions = detail.sessions.sortedWith(
                            compareByDescending<ClassSession> { it.date }.thenByDescending { it.startTime },
                        ),
                        trend = buildTrend(detail.sessions, detail.settings),
                        settings = detail.settings,
                        today = container.today(),
                        whatIfAttend = attend,
                        whatIfMiss = miss,
                        message = message,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun load(id: Long) { subjectId.value = id }

    /**
     * Running attendance after each attendance-bearing class.
     *
     * Cancelled and unmarked classes are skipped so the line does not dip for events
     * that carry no meaning. The series is thinned to roughly 40 points so a full
     * semester still renders as a readable curve on a phone.
     */
    private fun buildTrend(sessions: List<ClassSession>, settings: AppSettings): List<TrendPoint> {
        val ordered = sessions
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .filter {
                it.status == AttendanceStatus.PRESENT ||
                    it.status == AttendanceStatus.ABSENT ||
                    (it.status == AttendanceStatus.EXCUSED &&
                        settings.excusedPolicy == com.twisham.attendance.domain.model.ExcusedPolicy.COUNT_AS_PRESENT)
            }
        if (ordered.size < 2) return emptyList()

        var attended = 0
        val all = ordered.mapIndexed { index, session ->
            if (session.status != AttendanceStatus.ABSENT) attended++
            TrendPoint(
                label = com.twisham.attendance.util.Formatters.dayMonth(session.date),
                percent = Percent.ofRatio(attended, index + 1) ?: Percent.ZERO,
            )
        }

        val maxPoints = 40
        if (all.size <= maxPoints) return all
        val step = all.size.toDouble() / maxPoints
        return (0 until maxPoints)
            .map { all[(it * step).toInt().coerceAtMost(all.lastIndex)] }
            .plus(all.last())
    }

    fun setWhatIfAttend(value: Int) { whatIf.value = value.coerceIn(0, 60) to whatIf.value.second }
    fun setWhatIfMiss(value: Int) { whatIf.value = whatIf.value.first to value.coerceIn(0, 60) }
    fun resetWhatIf() { whatIf.value = 0 to 0 }

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
        val Factory = containerViewModelFactory { SubjectDetailViewModel(it) }
    }
}
