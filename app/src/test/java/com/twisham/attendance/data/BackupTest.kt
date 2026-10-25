package com.twisham.attendance.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twisham.attendance.data.backup.BackupManager
import com.twisham.attendance.data.backup.ImportMode
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.data.repository.SemesterRepositoryImpl
import com.twisham.attendance.data.repository.SessionRepositoryImpl
import com.twisham.attendance.data.repository.SettingsRepositoryImpl
import com.twisham.attendance.data.repository.SubjectRepositoryImpl
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.domain.usecase.ObserveSemesterAttendance
import com.twisham.attendance.util.InvalidBackupException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Export/import. The property under test is that a backup is a faithful, complete
 * record — and that a bad one is rejected without touching the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    private lateinit var db: AppDatabase
    private lateinit var backup: BackupManager
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var subjects: SubjectRepositoryImpl
    private lateinit var semesters: SemesterRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        backup = BackupManager(db, "1.0-test")
        sessions = SessionRepositoryImpl(db)
        subjects = SubjectRepositoryImpl(db)
        semesters = SemesterRepositoryImpl(db)
        settings = SettingsRepositoryImpl(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Long {
        val semesterId = semesters.create(
            Semester(
                name = "Semester 5",
                academicYear = "2026–27",
                startDate = monday.minusWeeks(4),
                endDate = monday.plusWeeks(8),
                defaultRequirement = Percent.ofWhole(75),
                isActive = true,
                isArchived = false,
            ),
        )
        val subjectId = subjects.create(
            Subject(
                semesterId = semesterId,
                name = "Algorithms",
                code = "DAA",
                teacher = "Dr. Iyer",
                requirement = Percent.ofWhole(75),
                type = SubjectType.THEORY,
                colorIndex = 2,
            ),
        )
        db.timetableDao().insert(
            TimetableSlot(
                subjectId = subjectId,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 50),
                room = "204",
            ).toEntity(),
        )
        val statuses = listOf(
            AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT,
            AttendanceStatus.CANCELLED, AttendanceStatus.EXCUSED,
        )
        statuses.forEachIndexed { index, status ->
            db.sessionDao().insert(
                ClassSession(
                    semesterId = semesterId,
                    subjectId = subjectId,
                    date = monday.plusDays(index.toLong()),
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(10, 50),
                    status = status,
                    kind = if (index == 4) SessionKind.EXTRA else SessionKind.SCHEDULED,
                    note = if (index == 3) "Teacher unavailable" else null,
                ).toEntity(),
            )
        }
        return semesterId
    }

    @Test
    fun `export then import reproduces the attendance exactly`() = runTest {
        val originalId = seed()
        val observe = ObserveSemesterAttendance(subjects, sessions, settings)
        val before = observe(originalId).first().subjects.first()

        val json = backup.exportToJson()
        val summary = backup.import(json, ImportMode.REPLACE)

        assertEquals(1, summary.semesters)
        assertEquals(1, summary.subjects)
        assertEquals(5, summary.sessions)

        val restoredSemester = semesters.observeAll().first().single()
        val after = observe(restoredSemester.id).first().subjects.first()

        assertEquals(before.percent, after.percent)
        assertEquals(before.counts.present, after.counts.present)
        assertEquals(before.counts.absent, after.counts.absent)
        assertEquals(before.counts.cancelled, after.counts.cancelled)
        assertEquals(before.counts.excused, after.counts.excused)
        assertEquals(before.counts.conducted, after.counts.conducted)
        assertEquals(before.subject.name, after.subject.name)
        assertEquals(before.subject.teacher, after.subject.teacher)
        assertEquals(before.subject.requirement, after.subject.requirement)

        // The note and the extra-class marker survive the round trip too.
        val restored = db.sessionDao().getForSemester(restoredSemester.id)
        assertEquals(1, restored.count { it.note == "Teacher unavailable" })
        assertEquals(1, restored.count { it.kind == SessionKind.EXTRA.name })
        assertEquals(1, db.timetableDao().getForSubject(after.subject.id).size)
    }

    @Test
    fun `merge keeps the existing data alongside the imported data`() = runTest {
        seed()
        val json = backup.exportToJson()

        backup.import(json, ImportMode.MERGE)

        assertEquals(2, semesters.observeAll().first().size)
        assertEquals(1, semesters.observeAll().first().count { it.isActive })
    }

    @Test
    fun `importing does not duplicate sessions through later expansion`() = runTest {
        seed()
        val json = backup.exportToJson()
        backup.import(json, ImportMode.REPLACE)

        val semester = semesters.observeAll().first().single()
        val before = db.sessionDao().getForSemester(semester.id).size

        // The imported days are already marked as expanded, so this is a no-op.
        sessions.ensureMaterialised(
            semesters.getById(semester.id)!!,
            monday,
            monday.plusDays(6),
        )

        assertEquals(before, db.sessionDao().getForSemester(semester.id).size)
    }

    @Test
    fun `a file from another app is rejected`() = runTest {
        val before = semesters.observeAll().first().size
        val error = runCatching {
            backup.import("""{"format":"some-other-app","version":1}""", ImportMode.REPLACE)
        }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertEquals(before, semesters.observeAll().first().size)
    }

    @Test
    fun `malformed json leaves the database untouched`() = runTest {
        seed()
        val before = db.sessionDao().getForSemester(semesters.getActive()!!.id).size

        val error = runCatching { backup.import("{ not json at all", ImportMode.REPLACE) }
            .exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertEquals(before, db.sessionDao().getForSemester(semesters.getActive()!!.id).size)
    }

    @Test
    fun `a backup from a newer format version is refused`() = runTest {
        seed()
        val json = backup.exportToJson().replace("\"version\": 1", "\"version\": 99")
        val error = runCatching { backup.import(json, ImportMode.REPLACE) }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertTrue((error as InvalidBackupException).reason.contains("newer version"))
        // Crucially, the existing semester is still there.
        assertEquals(1, semesters.observeAll().first().size)
    }

    @Test
    fun `an unknown status is refused rather than silently guessed`() = runTest {
        seed()
        val json = backup.exportToJson().replace("\"PRESENT\"", "\"MAYBE\"")
        val error = runCatching { backup.import(json, ImportMode.REPLACE) }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertEquals(5, db.sessionDao().getForSemester(semesters.getActive()!!.id).size)
    }

    @Test
    fun `an impossible date is reported clearly`() = runTest {
        seed()
        val json = backup.exportToJson().replace(monday.toString(), "2026-02-30")
        val error = runCatching { backup.import(json, ImportMode.REPLACE) }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertTrue((error as InvalidBackupException).reason.contains("not a date"))
    }

    @Test
    fun `an out of range requirement is refused`() = runTest {
        seed()
        val json = backup.exportToJson()
            .replace("\"requirement_percent\": 75.0", "\"requirement_percent\": 250.0")
        val error = runCatching { backup.import(json, ImportMode.REPLACE) }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
    }

    @Test
    fun `csv export has one header and one row per session`() = runTest {
        seed()
        val csv = backup.exportToCsv().trim().lines()

        assertEquals(6, csv.size)
        assertTrue(csv.first().startsWith("semester,academic_year,subject"))
        assertTrue(csv.any { it.contains("CANCELLED") })
        assertTrue(csv.any { it.contains("Teacher unavailable") })
    }
}
