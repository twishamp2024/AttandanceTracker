package com.twisham.attendance.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.data.repository.SemesterRepositoryImpl
import com.twisham.attendance.data.repository.SessionRepositoryImpl
import com.twisham.attendance.data.repository.SettingsRepositoryImpl
import com.twisham.attendance.data.repository.SubjectRepositoryImpl
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.ExcusedPolicy
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.domain.usecase.ObserveSemesterAttendance
import com.twisham.attendance.util.DuplicateSessionException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * End-to-end checks against a real (in-memory) Room database.
 *
 * These cover the properties that pure-maths tests cannot: that attendance stays
 * inside its semester, that editing history actually changes the numbers, and that
 * timetable expansion is safe to run repeatedly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var subjects: SubjectRepositoryImpl
    private lateinit var semesters: SemesterRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    private val monday: LocalDate = LocalDate.of(2026, 9, 7) // a Monday

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        sessions = SessionRepositoryImpl(db)
        subjects = SubjectRepositoryImpl(db)
        semesters = SemesterRepositoryImpl(db)
        settings = SettingsRepositoryImpl(db)
    }

    @After
    fun tearDown() = db.close()

    // ------------------------------------------------------------- helpers

    private suspend fun makeSemester(name: String, active: Boolean = false): Semester {
        val id = semesters.create(
            Semester(
                name = name,
                academicYear = "2026–27",
                startDate = monday.minusWeeks(4),
                endDate = monday.plusWeeks(8),
                defaultRequirement = Percent.ofWhole(75),
                isActive = active,
                isArchived = false,
            ),
        )
        return semesters.getById(id)!!
    }

    private suspend fun makeSubject(
        semesterId: Long,
        name: String,
        requirement: Int = 75,
    ): Subject {
        val id = subjects.create(
            Subject(
                semesterId = semesterId,
                name = name,
                code = name.take(3).uppercase(),
                teacher = null,
                requirement = Percent.ofWhole(requirement),
                type = SubjectType.THEORY,
                colorIndex = 0,
            ),
        )
        return subjects.getById(id)!!
    }

    private suspend fun addSession(
        semesterId: Long,
        subjectId: Long,
        date: LocalDate,
        status: AttendanceStatus,
        kind: SessionKind = SessionKind.SCHEDULED,
        start: LocalTime = LocalTime.of(9, 0),
    ): Long = db.sessionDao().insert(
        ClassSession(
            semesterId = semesterId,
            subjectId = subjectId,
            date = date,
            startTime = start,
            endTime = start.plusHours(1),
            status = status,
            kind = kind,
        ).toEntity(),
    )

    // --------------------------------------------------------------- tests

    @Test
    fun `attendance never mixes between semesters`() = runTest {
        val past = makeSemester("Semester 4")
        val current = makeSemester("Semester 5", active = true)

        val pastSubject = makeSubject(past.id, "Old Paper")
        val currentSubject = makeSubject(current.id, "New Paper")

        // A disastrous old semester...
        repeat(10) { addSession(past.id, pastSubject.id, monday.plusDays(it.toLong()), AttendanceStatus.ABSENT) }
        // ...and a perfect current one.
        repeat(10) { addSession(current.id, currentSubject.id, monday.plusDays(it.toLong()), AttendanceStatus.PRESENT) }

        val observe = ObserveSemesterAttendance(subjects, sessions, settings)

        val currentStats = observe(current.id).first()
        assertEquals(1, currentStats.subjects.size)
        assertEquals("New Paper", currentStats.subjects.first().subject.name)
        assertEquals(Percent.FULL, currentStats.overall.percent)

        val pastStats = observe(past.id).first()
        assertEquals(Percent.ZERO, pastStats.overall.percent)

        // Archiving the old semester leaves its data intact and retrievable.
        semesters.update(past.copy(isArchived = true))
        assertTrue(semesters.getById(past.id)!!.isArchived)
        assertEquals(10, db.sessionDao().getForSemester(past.id).size)
    }

    @Test
    fun `editing a historical record changes the percentage`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "Algorithms")

        repeat(3) { addSession(semester.id, subject.id, monday.plusDays(it.toLong()), AttendanceStatus.PRESENT) }
        val mistake = addSession(semester.id, subject.id, monday.plusDays(3), AttendanceStatus.ABSENT)

        val observe = ObserveSemesterAttendance(subjects, sessions, settings)
        assertEquals(Percent.ofWhole(75), observe(semester.id).first().subjects.first().percent)

        // Correct the mistake in place — the row is updated, not recreated.
        sessions.setStatus(mistake, AttendanceStatus.PRESENT)

        assertEquals(Percent.FULL, observe(semester.id).first().subjects.first().percent)
        assertEquals(mistake, sessions.getById(mistake)!!.id)
        assertEquals(4, db.sessionDao().getForSemester(semester.id).size)
    }

    @Test
    fun `a cancelled class stops counting against the student`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "DBMS")

        repeat(3) { addSession(semester.id, subject.id, monday.plusDays(it.toLong()), AttendanceStatus.PRESENT) }
        val absence = addSession(semester.id, subject.id, monday.plusDays(3), AttendanceStatus.ABSENT)

        val observe = ObserveSemesterAttendance(subjects, sessions, settings)
        assertEquals(Percent.ofWhole(75), observe(semester.id).first().subjects.first().percent)

        // The class turns out to have been cancelled: it leaves the denominator.
        sessions.setStatus(absence, AttendanceStatus.CANCELLED)

        val stats = observe(semester.id).first().subjects.first()
        assertEquals(Percent.FULL, stats.percent)
        assertEquals(3, stats.counts.conducted)
        assertEquals(1, stats.counts.cancelled)
    }

    @Test
    fun `notes survive a status change`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "OS")
        val id = addSession(semester.id, subject.id, monday, AttendanceStatus.ABSENT)

        sessions.setNote(id, "Class shifted to room 204.")
        sessions.setStatus(id, AttendanceStatus.PRESENT)

        val loaded = sessions.getById(id)!!
        assertEquals("Class shifted to room 204.", loaded.note)
        assertEquals(AttendanceStatus.PRESENT, loaded.status)
    }

    @Test
    fun `blank notes are stored as absent rather than empty strings`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "OS")
        val id = addSession(semester.id, subject.id, monday, AttendanceStatus.PRESENT)

        sessions.setNote(id, "   ")
        assertNull(sessions.getById(id)!!.note)
    }

    @Test
    fun `timetable expansion is idempotent`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "Algorithms")
        db.timetableDao().insert(
            TimetableSlot(
                subjectId = subject.id,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 50),
            ).toEntity(),
        )

        val weekEnd = monday.plusDays(6)
        sessions.ensureMaterialised(semester, monday, weekEnd)
        val afterFirst = db.sessionDao().getForSemester(semester.id).size

        // Running it again — as happens every time the screen is opened — adds nothing.
        repeat(3) { sessions.ensureMaterialised(semester, monday, weekEnd) }

        assertEquals(1, afterFirst)
        assertEquals(afterFirst, db.sessionDao().getForSemester(semester.id).size)
    }

    @Test
    fun `expansion does not overwrite an answer already given`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "Algorithms")
        db.timetableDao().insert(
            TimetableSlot(
                subjectId = subject.id,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 50),
            ).toEntity(),
        )

        sessions.ensureMaterialised(semester, monday, monday)
        val created = db.sessionDao().getForDay(semester.id, monday.toEpochDay()).single()
        sessions.setStatus(created.id, AttendanceStatus.PRESENT)

        // Re-expanding after a timetable edit must not reset the mark back to unmarked.
        sessions.invalidateMaterialisationFrom(semester.id, monday)
        sessions.ensureMaterialised(semester, monday, monday)

        val after = db.sessionDao().getForDay(semester.id, monday.toEpochDay()).single()
        assertEquals(AttendanceStatus.PRESENT.name, after.status)
    }

    @Test
    fun `a deleted session is not resurrected by expansion`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "Algorithms")
        db.timetableDao().insert(
            TimetableSlot(
                subjectId = subject.id,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 50),
            ).toEntity(),
        )

        sessions.ensureMaterialised(semester, monday, monday)
        val created = sessions.getById(
            db.sessionDao().getForDay(semester.id, monday.toEpochDay()).single().id,
        )!!
        sessions.delete(created)

        sessions.ensureMaterialised(semester, monday, monday)
        assertEquals(0, db.sessionDao().getForDay(semester.id, monday.toEpochDay()).size)
    }

    @Test
    fun `duplicate sessions are rejected with a typed error`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "Algorithms")

        val first = sessions.addExtra(
            ClassSession(
                semesterId = semester.id,
                subjectId = subject.id,
                date = monday,
                startTime = LocalTime.of(17, 0),
                endTime = LocalTime.of(18, 0),
                status = AttendanceStatus.PRESENT,
                kind = SessionKind.EXTRA,
            ),
        )
        assertTrue(first.isSuccess)

        val second = sessions.addExtra(
            ClassSession(
                semesterId = semester.id,
                subjectId = subject.id,
                date = monday,
                startTime = LocalTime.of(17, 0),
                endTime = LocalTime.of(18, 0),
                status = AttendanceStatus.PRESENT,
                kind = SessionKind.EXTRA,
            ),
        )
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is DuplicateSessionException)
        assertEquals(1, db.sessionDao().getForSemester(semester.id).size)
    }

    @Test
    fun `deleting a subject removes its sessions but leaves other subjects alone`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val doomed = makeSubject(semester.id, "Dropped Elective")
        val kept = makeSubject(semester.id, "Algorithms")

        addSession(semester.id, doomed.id, monday, AttendanceStatus.PRESENT)
        addSession(semester.id, kept.id, monday, AttendanceStatus.PRESENT)

        subjects.delete(doomed)

        val remaining = db.sessionDao().getForSemester(semester.id)
        assertEquals(1, remaining.size)
        assertEquals(kept.id, remaining.single().subjectId)
        assertNotNull(subjects.getById(kept.id))
        assertNull(subjects.getById(doomed.id))
    }

    @Test
    fun `switching the active semester leaves exactly one active`() = runTest {
        val first = makeSemester("Semester 4", active = true)
        val second = makeSemester("Semester 5")

        semesters.setActive(second.id)

        assertEquals(second.id, semesters.getActive()!!.id)
        assertEquals(1, semesters.observeAll().first().count { it.isActive })
        assertTrue(!semesters.getById(first.id)!!.isActive)
    }

    @Test
    fun `settings default cleanly and persist once changed`() = runTest {
        val defaults = settings.get()
        assertEquals(ExcusedPolicy.IGNORE, defaults.excusedPolicy)

        settings.update(defaults.copy(excusedPolicy = ExcusedPolicy.COUNT_AS_PRESENT))
        assertEquals(ExcusedPolicy.COUNT_AS_PRESENT, settings.get().excusedPolicy)
    }

    @Test
    fun `the excused policy setting changes the reported percentage`() = runTest {
        val semester = makeSemester("Semester 5", active = true)
        val subject = makeSubject(semester.id, "DBMS")

        repeat(7) { addSession(semester.id, subject.id, monday.plusDays(it.toLong()), AttendanceStatus.PRESENT) }
        repeat(3) { addSession(semester.id, subject.id, monday.plusDays((10 + it).toLong()), AttendanceStatus.ABSENT) }
        repeat(5) { addSession(semester.id, subject.id, monday.plusDays((20 + it).toLong()), AttendanceStatus.EXCUSED) }

        val observe = ObserveSemesterAttendance(subjects, sessions, settings)
        assertEquals(Percent.ofWhole(70), observe(semester.id).first().subjects.first().percent)

        settings.update(settings.get().copy(excusedPolicy = ExcusedPolicy.COUNT_AS_PRESENT))
        assertEquals(Percent.ofWhole(80), observe(semester.id).first().subjects.first().percent)
    }
}
