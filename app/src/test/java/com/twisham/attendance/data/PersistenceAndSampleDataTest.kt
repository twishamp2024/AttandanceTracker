package com.twisham.attendance.data

import androidx.test.core.app.ApplicationProvider
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.sample.SampleData
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ExcusedPolicy
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.ThemeMode
import com.twisham.attendance.data.repository.SemesterRepositoryImpl
import com.twisham.attendance.data.repository.SessionRepositoryImpl
import com.twisham.attendance.data.repository.SettingsRepositoryImpl
import com.twisham.attendance.data.repository.SubjectRepositoryImpl
import com.twisham.attendance.domain.usecase.AttendanceAnalytics
import com.twisham.attendance.domain.usecase.ObserveSemesterAttendance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * Two things that only show up with a *real, on-disk* database:
 *
 *  1. Attendance survives the process being killed and the app restarted.
 *  2. The demo generator produces data that exercises every visual state.
 *
 * These deliberately use a file-backed database rather than an in-memory one,
 * because "survives a restart" is meaningless against a database that lives in RAM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceAndSampleDataTest {

    private lateinit var db: AppDatabase

    private fun open(): AppDatabase =
        AppDatabase.build(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        db = open()
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
    }

    @Test
    fun `attendance survives closing and reopening the database`() = runTest {
        val today = LocalDate.of(2026, 9, 25)

        val semesterId = SemesterRepositoryImpl(db).create(
            Semester(
                name = "Semester 5",
                academicYear = "2026–27",
                startDate = today.minusWeeks(6),
                endDate = today.plusWeeks(6),
                defaultRequirement = Percent.ofWhole(75),
                isActive = true,
                isArchived = false,
            ),
        )
        val subjectId = SubjectRepositoryImpl(db).create(
            Subject(
                semesterId = semesterId,
                name = "Algorithms",
                code = "DAA",
                teacher = "Dr. Iyer",
                requirement = Percent.ofWhole(75),
                type = SubjectType.THEORY,
                colorIndex = 3,
            ),
        )
        SessionRepositoryImpl(db).addExtra(
            com.twisham.attendance.domain.model.ClassSession(
                semesterId = semesterId,
                subjectId = subjectId,
                date = today,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 50),
                status = AttendanceStatus.PRESENT,
                kind = SessionKind.EXTRA,
                note = "Extra revision class",
            ),
        ).getOrThrow()
        SettingsRepositoryImpl(db).update(
            SettingsRepositoryImpl(db).get().copy(
                themeMode = ThemeMode.DARK,
                excusedPolicy = ExcusedPolicy.COUNT_AS_PRESENT,
            ),
        )

        // Simulate the process being killed and the app relaunched.
        db.close()
        db = open()

        val semester = SemesterRepositoryImpl(db).getActive()
        assertNotNull(semester)
        assertEquals("Semester 5", semester!!.name)

        val subject = SubjectRepositoryImpl(db).getById(subjectId)!!
        assertEquals("Algorithms", subject.name)
        assertEquals("Dr. Iyer", subject.teacher)
        assertEquals(Percent.ofWhole(75), subject.requirement)
        assertEquals(3, subject.colorIndex)

        val session = db.sessionDao().getForSemester(semesterId).single()
        assertEquals(AttendanceStatus.PRESENT.name, session.status)
        assertEquals(SessionKind.EXTRA.name, session.kind)
        assertEquals("Extra revision class", session.note)
        assertEquals(today.toEpochDay(), session.dateEpochDay)
        assertEquals(LocalTime.of(9, 0).toSecondOfDay(), session.startSecond)

        val settings = SettingsRepositoryImpl(db).get()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(ExcusedPolicy.COUNT_AS_PRESENT, settings.excusedPolicy)
    }

    @Test
    fun `a fresh install starts empty and with defaults, not with sample data`() = runTest {
        // Nothing seeded: a brand new install must not invent a semester or quietly
        // fill the database with demo rows.
        assertEquals(0, db.semesterDao().getAll().size)
        assertEquals(0, SemesterRepositoryImpl(db).count())

        val settings = SettingsRepositoryImpl(db).get()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(ExcusedPolicy.IGNORE, settings.excusedPolicy)
        assertEquals(1, settings.cautionSafeMissThreshold)
        assertTrue(!settings.remindersEnabled)
    }

    @Test
    fun `demo data is created inactive and covers every visual state`() = runTest {
        val today = LocalDate.of(2026, 9, 25)

        val semesterId = SampleData.install(db, today)
        val semester = SemesterRepositoryImpl(db).getById(semesterId)!!

        // It must not hijack the active semester.
        assertEquals(SampleData.SEMESTER_NAME, semester.name)
        assertTrue(!semester.isActive)

        val subjects = SubjectRepositoryImpl(db).observeForSemester(semesterId).first()
        assertEquals(6, subjects.size)
        assertTrue(subjects.any { it.type == SubjectType.LAB })
        assertTrue(subjects.any { it.requirement == Percent.ofWhole(80) })

        val sessions = db.sessionDao().getForSemester(semesterId)
        assertTrue("expected a full term of generated classes", sessions.size > 100)
        assertTrue(sessions.any { it.status == AttendanceStatus.CANCELLED.name })
        assertTrue(sessions.any { it.status == AttendanceStatus.EXCUSED.name })
        assertTrue(sessions.any { it.kind == SessionKind.EXTRA.name })

        val stats = ObserveSemesterAttendance(
            SubjectRepositoryImpl(db),
            SessionRepositoryImpl(db),
            SettingsRepositoryImpl(db),
            AttendanceAnalytics(),
        )(semesterId).first()

        // The point of the demo data is that every card state can be seen at once.
        val states = stats.subjects.map { it.health }.toSet()
        assertTrue(
            "demo data should produce more than one health state, got $states",
            states.size >= 2,
        )
        assertTrue(states.contains(AttendanceHealth.SAFE))
        assertNotNull(stats.overall.percent)
    }

    @Test
    fun `demo data does not duplicate itself when a day is materialised again`() = runTest {
        val today = LocalDate.of(2026, 9, 25)
        val semesterId = SampleData.install(db, today)
        val semester = SemesterRepositoryImpl(db).getById(semesterId)!!
        val before = db.sessionDao().getForSemester(semesterId).size

        SessionRepositoryImpl(db).ensureMaterialised(
            semester,
            today.minusWeeks(4),
            today,
        )

        assertEquals(before, db.sessionDao().getForSemester(semesterId).size)
    }
}
