package com.twisham.attendance.di

import android.content.Context
import com.twisham.attendance.BuildConfig
import com.twisham.attendance.data.backup.BackupManager
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.repository.SemesterRepositoryImpl
import com.twisham.attendance.data.repository.SessionRepositoryImpl
import com.twisham.attendance.data.repository.SettingsRepositoryImpl
import com.twisham.attendance.data.repository.SubjectRepositoryImpl
import com.twisham.attendance.data.repository.TimetableRepositoryImpl
import com.twisham.attendance.domain.repository.SemesterRepository
import com.twisham.attendance.domain.repository.SessionRepository
import com.twisham.attendance.domain.repository.SettingsRepository
import com.twisham.attendance.domain.repository.SubjectRepository
import com.twisham.attendance.domain.repository.TimetableRepository
import com.twisham.attendance.domain.usecase.AttendanceAnalytics
import com.twisham.attendance.domain.usecase.ObserveSemesterAttendance
import com.twisham.attendance.domain.usecase.ObserveSubjectDetail
import com.twisham.attendance.notification.ReminderScheduler
import java.time.Clock
import java.time.LocalDate

/**
 * Hand-rolled dependency container.
 *
 * A DI framework was considered and rejected. This app has one module, one database
 * and about a dozen collaborators; Hilt would add an annotation processor, a second
 * build step and a layer of generated indirection to solve a problem that fifty
 * lines of `by lazy` already solve. The container is constructed once in
 * `AttendanceApp` and reached through the Application instance, which keeps it
 * trivially replaceable in tests.
 */
class AppContainer(
    private val context: Context,
    /** Injectable so tests can pin "today" instead of depending on the wall clock. */
    val clock: Clock = Clock.systemDefaultZone(),
    /**
     * Injectable so tests can supply an in-memory database. Production always uses
     * the default; without this seam every UI test would share one on-disk database
     * and leak data into the next test.
     */
    private val databaseFactory: (Context) -> AppDatabase = { AppDatabase.build(it) },
) {
    fun today(): LocalDate = LocalDate.now(clock)

    val database: AppDatabase by lazy { databaseFactory(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(database) }
    val semesterRepository: SemesterRepository by lazy { SemesterRepositoryImpl(database) }
    val subjectRepository: SubjectRepository by lazy { SubjectRepositoryImpl(database) }
    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl(database) }
    val timetableRepository: TimetableRepository by lazy {
        TimetableRepositoryImpl(database, sessionRepository) { today() }
    }

    val analytics: AttendanceAnalytics by lazy { AttendanceAnalytics() }

    val observeSemesterAttendance: ObserveSemesterAttendance by lazy {
        ObserveSemesterAttendance(subjectRepository, sessionRepository, settingsRepository, analytics)
    }

    val observeSubjectDetail: ObserveSubjectDetail by lazy {
        ObserveSubjectDetail(subjectRepository, sessionRepository, settingsRepository, analytics)
    }

    val backupManager: BackupManager by lazy { BackupManager(database, BuildConfig.VERSION_NAME) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }
}
