package com.twisham.attendance.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twisham.attendance.data.backup.ImportMode
import com.twisham.attendance.data.sample.SampleData
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.ui.containerViewModelFactory
import com.twisham.attendance.ui.today.friendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val semesterCount: Int = 0,
        val busy: Boolean = false,
        val message: String? = null,
        /** Pending import, held until the user picks merge or replace. */
        val pendingImport: PendingImport? = null,
    )

    data class PendingImport(
        val raw: String,
        val semesters: Int,
        val subjects: Int,
        val sessions: Int,
    )

    private val transient = MutableStateFlow(Transient())

    private data class Transient(
        val busy: Boolean = false,
        val message: String? = null,
        val pendingImport: PendingImport? = null,
    )

    val uiState: StateFlow<UiState> = combine(
        container.settingsRepository.observe(),
        container.semesterRepository.observeAll(),
        transient,
    ) { settings, semesters, extra ->
        UiState(
            settings = settings,
            semesterCount = semesters.size,
            busy = extra.busy,
            message = extra.message,
            pendingImport = extra.pendingImport,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            runCatching {
                val updated = transform(container.settingsRepository.get())
                container.settingsRepository.update(updated)
                container.reminderScheduler.apply(updated)
            }.onFailure { e ->
                transient.value = transient.value.copy(message = e.friendly())
            }
        }
    }

    // ---------------------------------------------------------------- export

    /**
     * Writes the backup through the Storage Access Framework.
     *
     * The user picks the destination, so the app needs no storage permission at all
     * and the file lands somewhere they chose and can find again.
     */
    fun exportJson(context: Context, uri: Uri) = exportTo(context, uri, json = true)

    fun exportCsv(context: Context, uri: Uri) = exportTo(context, uri, json = false)

    private fun exportTo(context: Context, uri: Uri, json: Boolean) {
        viewModelScope.launch {
            transient.value = transient.value.copy(busy = true)
            runCatching {
                val content = if (json) {
                    container.backupManager.exportToJson()
                } else {
                    container.backupManager.exportToCsv()
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open that file for writing.")
                }
                content.length
            }.onSuccess { size ->
                transient.value = Transient(
                    message = "Exported ${if (json) "backup" else "CSV"} (${size / 1024 + 1} KB).",
                )
            }.onFailure { e ->
                transient.value = Transient(message = "Export failed: ${e.friendly()}")
            }
        }
    }

    // ---------------------------------------------------------------- import

    /** Reads and validates the file, then asks how to apply it. Nothing is written yet. */
    fun stageImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            transient.value = transient.value.copy(busy = true)
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Could not read that file.")
                }
                val parsed = container.backupManager.validate(raw)
                PendingImport(
                    raw = raw,
                    semesters = parsed.semesters.size,
                    subjects = parsed.semesters.sumOf { it.subjects.size },
                    sessions = parsed.semesters.sumOf { s -> s.subjects.sumOf { it.sessions.size } },
                )
            }.onSuccess { pending ->
                transient.value = Transient(pendingImport = pending)
            }.onFailure { e ->
                transient.value = Transient(message = e.friendly())
            }
        }
    }

    fun confirmImport(mode: ImportMode) {
        val pending = transient.value.pendingImport ?: return
        viewModelScope.launch {
            transient.value = transient.value.copy(busy = true, pendingImport = null)
            runCatching { container.backupManager.import(pending.raw, mode) }
                .onSuccess { summary ->
                    transient.value = Transient(
                        message = "Imported ${summary.semesters} semesters, " +
                            "${summary.subjects} subjects and ${summary.sessions} classes.",
                    )
                }
                .onFailure { e ->
                    transient.value = Transient(message = "Import failed: ${e.friendly()}")
                }
        }
    }

    fun cancelImport() { transient.value = transient.value.copy(pendingImport = null) }

    // ----------------------------------------------------------- demo / reset

    fun installSampleData() {
        viewModelScope.launch {
            transient.value = transient.value.copy(busy = true)
            runCatching { SampleData.install(container.database, container.today()) }
                .onSuccess {
                    transient.value = Transient(
                        message = "Added \"${SampleData.SEMESTER_NAME}\". " +
                            "Switch to it from Semesters, and delete it whenever you like.",
                    )
                }
                .onFailure { e ->
                    transient.value = Transient(message = "Could not add demo data: ${e.friendly()}")
                }
        }
    }

    fun resetEverything() {
        viewModelScope.launch {
            transient.value = transient.value.copy(busy = true)
            runCatching {
                container.database.semesterDao().getAll().forEach {
                    container.database.semesterDao().delete(it)
                }
                container.database.materialisedDayDao().clearAll()
                container.settingsRepository.update(AppSettings())
                container.reminderScheduler.cancel()
            }.onSuccess {
                transient.value = Transient(message = "All data deleted.")
            }.onFailure { e ->
                transient.value = Transient(message = "Could not reset: ${e.friendly()}")
            }
        }
    }

    fun consumeMessage() { transient.value = transient.value.copy(message = null) }

    companion object {
        val Factory = containerViewModelFactory { SettingsViewModel(it) }

        fun suggestedJsonName(): String =
            "attendance-backup-${java.time.LocalDate.now()}.json"

        fun suggestedCsvName(): String =
            "attendance-${java.time.LocalDate.now()}.csv"
    }
}
