package com.twisham.attendance.data.repository

import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.toDomain
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.domain.repository.SemesterRepository
import com.twisham.attendance.domain.repository.SessionRepository
import com.twisham.attendance.domain.repository.SettingsRepository
import com.twisham.attendance.domain.repository.SubjectRepository
import com.twisham.attendance.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SemesterRepositoryImpl(db: AppDatabase) : SemesterRepository {
    private val dao = db.semesterDao()

    override fun observeAll(): Flow<List<Semester>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<Semester?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getActive(): Semester? = dao.getActive()?.toDomain()
    override suspend fun getById(id: Long): Semester? = dao.getById(id)?.toDomain()
    override suspend fun create(semester: Semester): Long = dao.insert(semester.toEntity())
    override suspend fun update(semester: Semester) = dao.update(semester.toEntity())
    override suspend fun delete(semester: Semester) = dao.delete(semester.toEntity())
    override suspend fun setActive(id: Long) = dao.setActive(id)
    override suspend fun count(): Int = dao.count()
}

class SubjectRepositoryImpl(db: AppDatabase) : SubjectRepository {
    private val dao = db.subjectDao()

    override fun observeForSemester(semesterId: Long): Flow<List<Subject>> =
        dao.observeForSemester(semesterId).map { list -> list.map { it.toDomain() } }

    override fun observeActiveForSemester(semesterId: Long): Flow<List<Subject>> =
        dao.observeActiveForSemester(semesterId).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<Subject?> = dao.observeById(id).map { it?.toDomain() }
    override suspend fun getById(id: Long): Subject? = dao.getById(id)?.toDomain()
    override suspend fun create(subject: Subject): Long = dao.insert(subject.toEntity())
    override suspend fun update(subject: Subject) = dao.update(subject.toEntity())
    override suspend fun delete(subject: Subject) = dao.delete(subject.toEntity())
}

/**
 * Timetable edits invalidate the expansion of *future* days only, so that changing
 * next term's plan never rewrites the attendance already recorded this term.
 */
class TimetableRepositoryImpl(
    private val db: AppDatabase,
    private val sessions: SessionRepository,
    private val today: () -> java.time.LocalDate,
) : TimetableRepository {
    private val dao = db.timetableDao()

    override fun observeForSemester(semesterId: Long): Flow<List<TimetableSlot>> =
        dao.observeForSemester(semesterId).map { list -> list.map { it.toDomain() } }

    override suspend fun getForSubject(subjectId: Long): List<TimetableSlot> =
        dao.getForSubject(subjectId).map { it.toDomain() }

    override suspend fun create(slot: TimetableSlot): Long {
        val id = dao.insert(slot.toEntity())
        invalidate(slot.subjectId)
        return id
    }

    override suspend fun update(slot: TimetableSlot) {
        dao.update(slot.toEntity())
        invalidate(slot.subjectId)
    }

    override suspend fun delete(slot: TimetableSlot) {
        dao.delete(slot.toEntity())
        invalidate(slot.subjectId)
    }

    private suspend fun invalidate(subjectId: Long) {
        val semesterId = db.subjectDao().getById(subjectId)?.semesterId ?: return
        sessions.invalidateMaterialisationFrom(semesterId, today())
    }
}

class SettingsRepositoryImpl(db: AppDatabase) : SettingsRepository {
    private val dao = db.settingsDao()

    /** A missing row simply means "defaults", so the first launch needs no seeding. */
    override fun observe(): Flow<AppSettings> = dao.observe().map { it?.toDomain() ?: AppSettings() }
    override suspend fun get(): AppSettings = dao.get()?.toDomain() ?: AppSettings()
    override suspend fun update(settings: AppSettings) = dao.upsert(settings.toEntity())
}
