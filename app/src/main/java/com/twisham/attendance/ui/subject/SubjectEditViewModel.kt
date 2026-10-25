package com.twisham.attendance.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.ui.containerViewModelFactory
import com.twisham.attendance.ui.today.friendly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubjectEditViewModel(private val container: AppContainer) : ViewModel() {

    data class FormState(
        val id: Long? = null,
        val name: String = "",
        val code: String = "",
        val teacher: String = "",
        val type: SubjectType = SubjectType.THEORY,
        val requirementPercent: String = "75",
        val colorIndex: Int = 0,
        val isActive: Boolean = true,
        val sessionCount: Int = 0,
        val loading: Boolean = true,
        val saving: Boolean = false,
        val finished: Boolean = false,
        val message: String? = null,
    ) {
        val nameError: String? get() = if (name.isBlank()) "Give the subject a name." else null

        val requirementError: String? get() {
            val parsed = requirementPercent.trim().toDoubleOrNull()
            return when {
                parsed == null -> "Enter a number."
                parsed < 0 || parsed > 100 -> "Must be between 0 and 100."
                else -> null
            }
        }

        val isValid: Boolean get() = nameError == null && requirementError == null
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun load(id: Long?) {
        viewModelScope.launch {
            if (id == null) {
                // A new subject inherits the semester's default requirement, and a
                // colour that is not already in use, so subjects stay distinguishable.
                val semester = container.semesterRepository.getActive()
                val used = semester?.let {
                    container.database.subjectDao().getForSemester(it.id).map { s -> s.colorIndex }
                }.orEmpty()
                val palette = 8
                val nextColor = (0 until palette).firstOrNull { it !in used } ?: (used.size % palette)
                _form.update {
                    it.copy(
                        loading = false,
                        colorIndex = nextColor,
                        requirementPercent = semester?.defaultRequirement?.let { p ->
                            if (p.basisPoints % 100 == 0) (p.basisPoints / 100).toString()
                            else p.asDouble.toString()
                        } ?: "75",
                    )
                }
                return@launch
            }

            val subject = container.subjectRepository.getById(id)
            if (subject == null) {
                _form.update { it.copy(loading = false, message = "That subject no longer exists.") }
                return@launch
            }
            val sessionCount = container.database.sessionDao()
                .getForSemester(subject.semesterId)
                .count { it.subjectId == id }

            _form.update {
                it.copy(
                    id = subject.id,
                    name = subject.name,
                    code = subject.code,
                    teacher = subject.teacher.orEmpty(),
                    type = subject.type,
                    requirementPercent = subject.requirement.let { p ->
                        if (p.basisPoints % 100 == 0) (p.basisPoints / 100).toString()
                        else p.asDouble.toString()
                    },
                    colorIndex = subject.colorIndex,
                    isActive = subject.isActive,
                    sessionCount = sessionCount,
                    loading = false,
                )
            }
        }
    }

    fun onName(value: String) = _form.update { it.copy(name = value) }
    fun onCode(value: String) = _form.update { it.copy(code = value) }
    fun onTeacher(value: String) = _form.update { it.copy(teacher = value) }
    fun onType(value: SubjectType) = _form.update { it.copy(type = value) }
    fun onRequirement(value: String) = _form.update { it.copy(requirementPercent = value) }
    fun onColor(value: Int) = _form.update { it.copy(colorIndex = value) }
    fun onActive(value: Boolean) = _form.update { it.copy(isActive = value) }
    fun consumeMessage() = _form.update { it.copy(message = null) }

    fun save() {
        val current = _form.value
        if (!current.isValid || current.saving) return

        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            runCatching {
                val semester = container.semesterRepository.getActive()
                    ?: error("Create a semester before adding subjects.")
                val existing = current.id?.let { container.subjectRepository.getById(it) }
                val subject = Subject(
                    id = current.id ?: 0L,
                    semesterId = existing?.semesterId ?: semester.id,
                    name = current.name.trim(),
                    code = current.code.trim(),
                    teacher = current.teacher.trim().takeIf { it.isNotBlank() },
                    requirement = Percent.of(current.requirementPercent.trim().toDouble()),
                    type = current.type,
                    colorIndex = current.colorIndex,
                    isActive = current.isActive,
                )
                if (current.id == null) {
                    container.subjectRepository.create(subject)
                } else {
                    container.subjectRepository.update(subject)
                }
            }.onSuccess {
                _form.update { it.copy(saving = false, finished = true) }
            }.onFailure { error ->
                _form.update { it.copy(saving = false, message = error.friendly()) }
            }
        }
    }

    fun delete() {
        val id = _form.value.id ?: return
        viewModelScope.launch {
            runCatching {
                val subject = container.subjectRepository.getById(id) ?: return@runCatching
                container.subjectRepository.delete(subject)
            }.onSuccess {
                _form.update { it.copy(finished = true) }
            }.onFailure { error ->
                _form.update { it.copy(message = error.friendly()) }
            }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { SubjectEditViewModel(it) }
    }
}
