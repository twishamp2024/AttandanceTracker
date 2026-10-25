package com.twisham.attendance.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.ui.containerViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * State for the Today screen.
 *
 * Marking is optimistic in feel but not in fact — the write goes straight to Room
 * and the UI re-renders from the resulting Flow. With a local database that round
 * trip is sub-frame, and it means the screen can never display a mark that failed
 * to save.
 */
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val date: LocalDate = LocalDate.now(),
        val semester: Semester? = null,
        val sessions: List<ClassSession> = emptyList(),
        val subjectsById: Map<Long, Subject> = emptyMap(),
        val message: String? = null,
        val lastUndo: UndoAction? = null,
    ) {
        val unmarked: List<ClassSession> get() = sessions.filterNot { it.isMarked }
        val marked: List<ClassSession> get() = sessions.filter { it.isMarked }
        val allDone: Boolean get() = sessions.isNotEmpty() && unmarked.isEmpty()

        /** Unmarked classes that have started, so can take a Present/Absent answer. */
        fun markable(now: LocalDateTime): List<ClassSession> =
            unmarked.filter { it.canMarkAttendance(now) }

        /** The class to lead with: the one happening now, else the next one due. */
        fun focus(now: LocalTime): ClassSession? =
            sessions.firstOrNull { !it.isMarked && now >= it.startTime && now <= it.endTime }
                ?: sessions.firstOrNull { !it.isMarked && it.startTime > now }
                ?: sessions.firstOrNull { !it.isMarked }
    }

    /** Enough to put a mark back exactly as it was. */
    data class UndoAction(
        val sessionId: Long,
        val previousStatus: AttendanceStatus,
        val label: String,
    )

    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(
        val message: String? = null,
        val undo: UndoAction? = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = container.semesterRepository.observeActive()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(Triple<Semester?, List<ClassSession>, List<Subject>>(null, emptyList(), emptyList()))
            } else {
                val today = container.today()
                combine(
                    container.sessionRepository.observeForDay(semester.id, today),
                    container.subjectRepository.observeForSemester(semester.id),
                ) { sessions, subjects -> Triple(semester, sessions, subjects) }
            }
        }
        .combine(transient) { (semester, sessions, subjects), extra ->
            UiState(
                loading = false,
                date = container.today(),
                semester = semester,
                sessions = sessions.sortedWith(compareBy({ it.startTime }, { it.id })),
                subjectsById = subjects.associateBy { it.id },
                message = extra.message,
                lastUndo = extra.undo,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * The current time, for deciding which classes can be marked yet.
     *
     * Rather than ticking constantly, this sleeps until the next unmarked class
     * starts and re-emits then, so the buttons enable on their own while the screen
     * stays open. Each wait is capped at a minute because `delay` runs on uptime,
     * which stalls while the phone sleeps, and the wall clock can also be changed.
     * Once every remaining class has started there is nothing left to wait for.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val now: StateFlow<LocalDateTime> = uiState
        .map { state -> state.unmarked.map { it.startDateTime } }
        .distinctUntilChanged()
        .transformLatest { starts ->
            while (true) {
                val current = LocalDateTime.now(container.clock)
                emit(current)
                val next = starts.filter { it > current }.minOrNull() ?: break
                // +1ms so a sub-millisecond gap cannot wake just before the start.
                val untilNext = Duration.between(current, next).toMillis() + 1
                delay(minOf(untilNext, MAX_CLOCK_CHECK_MS))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now(container.clock))

    init {
        viewModelScope.launch {
            val semester = container.semesterRepository.getActive() ?: return@launch
            val today = container.today()
            runCatching { container.sessionRepository.ensureMaterialised(semester, today, today) }
        }
    }

    fun mark(session: ClassSession, status: AttendanceStatus) {
        viewModelScope.launch {
            val previous = session.status
            runCatching { container.sessionRepository.setStatus(session.id, status) }
                .onSuccess {
                    transient.update {
                        it.copy(
                            undo = UndoAction(session.id, previous, "Marked ${status.name.lowercase()}"),
                        )
                    }
                }
                .onFailure { error ->
                    transient.update { it.copy(message = "Could not save that: ${error.friendly()}") }
                }
        }
    }

    fun undo() {
        val action = transient.value.undo ?: return
        viewModelScope.launch {
            runCatching { container.sessionRepository.setStatus(action.sessionId, action.previousStatus) }
            transient.update { it.copy(undo = null) }
        }
    }

    /**
     * Marks every unmarked class today that has started as present, for the "I was
     * in all of them" case. Classes still to come are left alone.
     */
    fun markAllPresent() {
        viewModelScope.launch {
            val pending = uiState.value.markable(LocalDateTime.now(container.clock))
            if (pending.isEmpty()) return@launch
            runCatching {
                pending.forEach {
                    container.sessionRepository.setStatus(it.id, AttendanceStatus.PRESENT)
                }
            }.onFailure { error ->
                transient.update { it.copy(message = "Could not save those: ${error.friendly()}") }
            }
        }
    }

    fun setNote(session: ClassSession, note: String?) {
        viewModelScope.launch {
            runCatching { container.sessionRepository.setNote(session.id, note) }
                .onFailure { error ->
                    transient.update { it.copy(message = "Could not save the note: ${error.friendly()}") }
                }
        }
    }

    fun setStatus(session: ClassSession, status: AttendanceStatus) = mark(session, status)

    fun delete(session: ClassSession) {
        viewModelScope.launch {
            runCatching { container.sessionRepository.delete(session) }
                .onFailure { error ->
                    transient.update { it.copy(message = "Could not delete that: ${error.friendly()}") }
                }
        }
    }

    /** Adds an unscheduled class that happened today. */
    fun addExtraClass(subjectId: Long, start: LocalTime, end: LocalTime, status: AttendanceStatus) {
        viewModelScope.launch {
            val semester = uiState.value.semester ?: return@launch
            val result = container.sessionRepository.addExtra(
                ClassSession(
                    semesterId = semester.id,
                    subjectId = subjectId,
                    date = container.today(),
                    startTime = start,
                    endTime = end,
                    status = status,
                    kind = SessionKind.EXTRA,
                ),
            )
            result.onFailure { error ->
                transient.update { it.copy(message = error.friendly()) }
            }
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }
    fun consumeUndo() = transient.update { it.copy(undo = null) }

    companion object {
        val Factory = containerViewModelFactory { TodayViewModel(it) }

        private const val MAX_CLOCK_CHECK_MS = 60_000L
    }
}

/** Turns an exception into something worth showing a person. */
internal fun Throwable.friendly(): String = when (this) {
    is com.twisham.attendance.util.DuplicateSessionException ->
        "There is already a class for that subject at that time."
    is com.twisham.attendance.util.ValidationException -> reason
    is com.twisham.attendance.util.InvalidBackupException -> reason
    else -> message ?: "Something went wrong."
}
