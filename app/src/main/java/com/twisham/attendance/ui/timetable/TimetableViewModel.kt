package com.twisham.attendance.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.ui.containerViewModelFactory
import com.twisham.attendance.ui.today.friendly
import com.twisham.attendance.util.ValidationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

class TimetableViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val semester: Semester? = null,
        val slotsByDay: Map<DayOfWeek, List<TimetableSlot>> = emptyMap(),
        val subjectsById: Map<Long, Subject> = emptyMap(),
        val subjects: List<Subject> = emptyList(),
        val message: String? = null,
    ) {
        val isEmpty: Boolean get() = slotsByDay.values.all { it.isEmpty() }
        val weeklyClassCount: Int get() = slotsByDay.values.sumOf { it.size }
    }

    private val messages = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = container.semesterRepository.observeActive()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(UiState(loading = false))
            } else {
                combine(
                    container.timetableRepository.observeForSemester(semester.id),
                    container.subjectRepository.observeForSemester(semester.id),
                    messages,
                ) { slots, subjects, message ->
                    UiState(
                        loading = false,
                        semester = semester,
                        slotsByDay = DayOfWeek.entries.associateWith { day ->
                            slots.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
                        },
                        subjectsById = subjects.associateBy { it.id },
                        subjects = subjects.filter { it.isActive },
                        message = message,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * Saving validates against the rest of the day before writing.
     *
     * Overlap is a warning-level problem rather than a hard error for the same slot
     * on the same subject, but a slot that ends before it starts is always rejected:
     * it would generate sessions with a negative duration and quietly corrupt the
     * timetable view.
     */
    fun save(slot: TimetableSlot) {
        viewModelScope.launch {
            runCatching {
                if (!slot.endTime.isAfter(slot.startTime)) {
                    throw ValidationException("The class must end after it starts.")
                }
                if (slot.subjectId <= 0) {
                    throw ValidationException("Choose a subject for this class.")
                }
                val clash = uiState.value.slotsByDay[slot.dayOfWeek].orEmpty()
                    .firstOrNull { it.id != slot.id && it.subjectId == slot.subjectId && it.overlaps(slot) }
                if (clash != null) {
                    throw ValidationException(
                        "This subject already has a class overlapping that time on " +
                            "${slot.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}.",
                    )
                }
                if (slot.id == 0L) {
                    container.timetableRepository.create(slot)
                } else {
                    container.timetableRepository.update(slot)
                }
                rematerialiseFuture()
            }.onFailure { error -> messages.value = error.friendly() }
        }
    }

    fun delete(slot: TimetableSlot) {
        viewModelScope.launch {
            runCatching {
                container.timetableRepository.delete(slot)
                rematerialiseFuture()
            }.onFailure { error -> messages.value = error.friendly() }
        }
    }

    /**
     * After a timetable change, regenerate sessions from today forward.
     *
     * Past days are untouched on purpose: attendance already recorded is history,
     * and a plan changed today must not rewrite what happened last month.
     */
    private suspend fun rematerialiseFuture() {
        val semester = container.semesterRepository.getActive() ?: return
        val today = container.today()
        container.sessionRepository.invalidateMaterialisationFrom(semester.id, today)
        container.sessionRepository.ensureMaterialised(semester, today, today.plusWeeks(2))
    }

    fun consumeMessage() { messages.value = null }

    companion object {
        val Factory = containerViewModelFactory { TimetableViewModel(it) }
        val DEFAULT_START: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(9, 55)
    }
}
