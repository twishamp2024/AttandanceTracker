package com.twisham.attendance.ui.semester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SemesterState
import com.twisham.attendance.ui.containerViewModelFactory
import com.twisham.attendance.ui.today.friendly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Summary row for the semester list. */
data class SemesterRow(
    val semester: Semester,
    val state: SemesterState,
    val subjectCount: Int,
    val sessionCount: Int,
)

class SemesterListViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val rows: List<SemesterRow> = emptyList(),
        val message: String? = null,
    )

    private val messages = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        container.semesterRepository.observeAll(),
        messages,
    ) { semesters, message ->
        val today = container.today()
        UiState(
            loading = false,
            rows = semesters.map { semester ->
                SemesterRow(
                    semester = semester,
                    state = semester.stateOn(today),
                    subjectCount = container.database.subjectDao().getForSemester(semester.id).size,
                    sessionCount = container.database.sessionDao().getForSemester(semester.id).size,
                )
            },
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setActive(id: Long) {
        viewModelScope.launch {
            runCatching { container.semesterRepository.setActive(id) }
                .onFailure { e -> messages.value = e.friendly() }
        }
    }

    fun setArchived(semester: Semester, archived: Boolean) {
        viewModelScope.launch {
            runCatching {
                container.semesterRepository.update(semester.copy(isArchived = archived))
                // An archived semester must not stay the active one, or the dashboard
                // would keep showing a term that is over.
                if (archived && semester.isActive) {
                    container.semesterRepository.observeAll()
                    val replacement = container.database.semesterDao().getAll()
                        .firstOrNull { !it.isArchived && it.id != semester.id }
                    replacement?.let { container.semesterRepository.setActive(it.id) }
                }
            }.onFailure { e -> messages.value = e.friendly() }
        }
    }

    fun delete(semester: Semester) {
        viewModelScope.launch {
            runCatching {
                container.semesterRepository.delete(semester)
                container.database.materialisedDayDao().clearForSemester(semester.id)
                if (container.semesterRepository.getActive() == null) {
                    container.database.semesterDao().getAll().firstOrNull()?.let {
                        container.semesterRepository.setActive(it.id)
                    }
                }
            }.onFailure { e -> messages.value = e.friendly() }
        }
    }

    fun consumeMessage() { messages.value = null }

    companion object {
        val Factory = containerViewModelFactory { SemesterListViewModel(it) }
    }
}

/**
 * Create/edit form state.
 *
 * Validation is computed from the form rather than being run on submit, so the Save
 * button can be disabled with the reason already visible next to the offending field.
 */
class SemesterEditViewModel(private val container: AppContainer) : ViewModel() {

    data class FormState(
        val id: Long? = null,
        val name: String = "",
        val academicYear: String = "",
        val startDate: LocalDate = LocalDate.now(),
        val endDate: LocalDate = LocalDate.now().plusMonths(5),
        val requirementPercent: String = "75",
        val makeActive: Boolean = true,
        val loading: Boolean = true,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val message: String? = null,
    ) {
        val nameError: String? get() = if (name.isBlank()) "Give the semester a name." else null

        val dateError: String? get() =
            if (endDate.isBefore(startDate)) "The end date must be after the start date." else null

        val requirementError: String? get() {
            val parsed = requirementPercent.trim().toDoubleOrNull()
            return when {
                parsed == null -> "Enter a number."
                parsed < 0 || parsed > 100 -> "Must be between 0 and 100."
                else -> null
            }
        }

        val isValid: Boolean get() = nameError == null && dateError == null && requirementError == null
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun load(id: Long?) {
        viewModelScope.launch {
            if (id == null) {
                val today = container.today()
                _form.update {
                    it.copy(
                        loading = false,
                        startDate = today,
                        endDate = today.plusMonths(5),
                        academicYear = "${today.year}–${(today.year + 1) % 100}",
                    )
                }
                return@launch
            }
            val semester = container.semesterRepository.getById(id)
            if (semester == null) {
                _form.update { it.copy(loading = false, message = "That semester no longer exists.") }
                return@launch
            }
            _form.update {
                it.copy(
                    id = semester.id,
                    name = semester.name,
                    academicYear = semester.academicYear,
                    startDate = semester.startDate,
                    endDate = semester.endDate,
                    requirementPercent = semester.defaultRequirement.let { p ->
                        if (p.basisPoints % 100 == 0) (p.basisPoints / 100).toString() else p.asDouble.toString()
                    },
                    makeActive = semester.isActive,
                    loading = false,
                )
            }
        }
    }

    fun onName(value: String) = _form.update { it.copy(name = value) }
    fun onAcademicYear(value: String) = _form.update { it.copy(academicYear = value) }
    fun onStartDate(value: LocalDate) = _form.update { it.copy(startDate = value) }
    fun onEndDate(value: LocalDate) = _form.update { it.copy(endDate = value) }
    fun onRequirement(value: String) = _form.update { it.copy(requirementPercent = value) }
    fun onMakeActive(value: Boolean) = _form.update { it.copy(makeActive = value) }
    fun consumeMessage() = _form.update { it.copy(message = null) }

    fun save() {
        val current = _form.value
        if (!current.isValid || current.saving) return

        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            runCatching {
                val requirement = Percent.of(current.requirementPercent.trim().toDouble())
                val existing = current.id?.let { container.semesterRepository.getById(it) }
                val semester = Semester(
                    id = current.id ?: 0L,
                    name = current.name.trim(),
                    academicYear = current.academicYear.trim(),
                    startDate = current.startDate,
                    endDate = current.endDate,
                    defaultRequirement = requirement,
                    isActive = existing?.isActive ?: false,
                    isArchived = existing?.isArchived ?: false,
                )
                val id = if (current.id == null) {
                    container.semesterRepository.create(semester)
                } else {
                    container.semesterRepository.update(semester)
                    current.id
                }
                // First semester is always made active, otherwise the app would open
                // on an empty dashboard straight after onboarding.
                if (current.makeActive || container.semesterRepository.getActive() == null) {
                    container.semesterRepository.setActive(id)
                }
                id
            }.onSuccess {
                _form.update { it.copy(saving = false, saved = true) }
            }.onFailure { error ->
                _form.update { it.copy(saving = false, message = error.friendly()) }
            }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { SemesterEditViewModel(it) }
    }
}
