package com.twisham.attendance.domain.repository

import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.TimetableSlot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository contracts, expressed only in domain types.
 *
 * The domain layer depends on these interfaces; the data layer implements them.
 * Nothing above the data layer knows Room exists, which is what makes the
 * ViewModels and the attendance maths testable with plain fakes.
 */

interface SemesterRepository {
    fun observeAll(): Flow<List<Semester>>
    fun observeActive(): Flow<Semester?>
    suspend fun getActive(): Semester?
    suspend fun getById(id: Long): Semester?
    suspend fun create(semester: Semester): Long
    suspend fun update(semester: Semester)
    suspend fun delete(semester: Semester)
    suspend fun setActive(id: Long)
    suspend fun count(): Int
}

interface SubjectRepository {
    fun observeForSemester(semesterId: Long): Flow<List<Subject>>
    fun observeActiveForSemester(semesterId: Long): Flow<List<Subject>>
    fun observeById(id: Long): Flow<Subject?>
    suspend fun getById(id: Long): Subject?
    suspend fun create(subject: Subject): Long
    suspend fun update(subject: Subject)
    suspend fun delete(subject: Subject)
}

interface TimetableRepository {
    fun observeForSemester(semesterId: Long): Flow<List<TimetableSlot>>
    suspend fun getForSubject(subjectId: Long): List<TimetableSlot>
    suspend fun create(slot: TimetableSlot): Long
    suspend fun update(slot: TimetableSlot)
    suspend fun delete(slot: TimetableSlot)
}

interface SessionRepository {
    fun observeForSemester(semesterId: Long): Flow<List<ClassSession>>
    fun observeForDay(semesterId: Long, date: LocalDate): Flow<List<ClassSession>>
    fun observeForSubject(subjectId: Long): Flow<List<ClassSession>>
    fun observeInRange(semesterId: Long, from: LocalDate, to: LocalDate): Flow<List<ClassSession>>
    suspend fun getById(id: Long): ClassSession?
    suspend fun setStatus(id: Long, status: AttendanceStatus)
    suspend fun setNote(id: Long, note: String?)
    suspend fun update(session: ClassSession)
    suspend fun delete(session: ClassSession)

    /** Adds a one-off session (extra class, or a class on an unscheduled day). */
    suspend fun addExtra(session: ClassSession): Result<Long>

    /**
     * Expands the weekly timetable into real sessions for every day in the range
     * that has not been expanded before. Idempotent.
     */
    suspend fun ensureMaterialised(semester: Semester, from: LocalDate, to: LocalDate)

    /** Re-expands future days after a timetable edit, leaving history untouched. */
    suspend fun invalidateMaterialisationFrom(semesterId: Long, from: LocalDate)
}

interface SettingsRepository {
    fun observe(): Flow<AppSettings>
    suspend fun get(): AppSettings
    suspend fun update(settings: AppSettings)
}
