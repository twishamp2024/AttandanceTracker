package com.twisham.attendance.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.entity.ClassSessionEntity
import com.twisham.attendance.data.local.entity.MaterialisedDayEntity
import com.twisham.attendance.data.local.toDomain
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.repository.SessionRepository
import com.twisham.attendance.util.DuplicateSessionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class SessionRepositoryImpl(private val db: AppDatabase) : SessionRepository {

    private val sessions = db.sessionDao()
    private val timetable = db.timetableDao()
    private val days = db.materialisedDayDao()

    override fun observeForSemester(semesterId: Long): Flow<List<ClassSession>> =
        sessions.observeForSemester(semesterId).map { list -> list.map { it.toDomain() } }

    override fun observeForDay(semesterId: Long, date: LocalDate): Flow<List<ClassSession>> =
        sessions.observeForDay(semesterId, date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override fun observeForSubject(subjectId: Long): Flow<List<ClassSession>> =
        sessions.observeForSubject(subjectId).map { list -> list.map { it.toDomain() } }

    override fun observeInRange(semesterId: Long, from: LocalDate, to: LocalDate): Flow<List<ClassSession>> =
        sessions.observeInRange(semesterId, from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): ClassSession? = sessions.getById(id)?.toDomain()

    override suspend fun setStatus(id: Long, status: AttendanceStatus) =
        sessions.setStatus(id, status.name)

    override suspend fun setNote(id: Long, note: String?) =
        sessions.setNote(id, note?.takeIf { it.isNotBlank() })

    override suspend fun update(session: ClassSession) = sessions.update(session.toEntity())

    override suspend fun delete(session: ClassSession) = sessions.delete(session.toEntity())

    override suspend fun addExtra(session: ClassSession): Result<Long> = try {
        Result.success(sessions.insert(session.toEntity()))
    } catch (e: SQLiteConstraintException) {
        // The unique index caught a session that already exists at this subject,
        // date and time. Surfacing this as a typed failure lets the UI say something
        // useful instead of either crashing or silently doing nothing.
        Result.failure(DuplicateSessionException(session.date, session.startTime, e))
    }

    /**
     * Expands the timetable into concrete sessions.
     *
     * Only days that have never been expanded are touched, tracked in
     * `materialised_days`. That gives three properties that matter:
     *
     *  1. **Idempotence** – opening the same day twice does not duplicate anything.
     *  2. **Deletions stick** – a session the user deleted is not resurrected the
     *     next time the screen is opened, because the day is already marked done.
     *  3. **History is immutable** – editing the weekly timetable only re-expands
     *     days from today forward (see [invalidateMaterialisationFrom]); last
     *     month's attendance is never rewritten by a plan change made today.
     *
     * The whole range is written in one transaction so a crash halfway cannot leave
     * a day marked as expanded without its sessions.
     */
    override suspend fun ensureMaterialised(semester: Semester, from: LocalDate, to: LocalDate) {
        val start = maxOf(from, semester.startDate)
        val end = minOf(to, semester.endDate)
        if (start.isAfter(end)) return

        val wanted = generateSequence(start) { d ->
            d.plusDays(1).takeIf { !it.isAfter(end) }
        }.map { it.toEpochDay() }.toList()
        if (wanted.isEmpty()) return

        db.withTransaction {
            val already = wanted.chunked(400)
                .flatMap { days.which(semester.id, it) }
                .toHashSet()
            val pending = wanted.filterNot { it in already }
            if (pending.isEmpty()) return@withTransaction

            // Cache slots per weekday: a semester-long range hits at most 7 queries.
            val slotsByDay = HashMap<Int, List<com.twisham.attendance.data.local.entity.TimetableSlotEntity>>()
            val newRows = ArrayList<ClassSessionEntity>()

            for (epochDay in pending) {
                val date = LocalDate.ofEpochDay(epochDay)
                val dow = date.dayOfWeek.value
                val slots = slotsByDay.getOrPut(dow) { timetable.getActiveForDay(semester.id, dow) }
                for (slot in slots) {
                    newRows += ClassSessionEntity(
                        semesterId = semester.id,
                        subjectId = slot.subjectId,
                        dateEpochDay = epochDay,
                        startSecond = slot.startSecond,
                        endSecond = slot.endSecond,
                        status = AttendanceStatus.NOT_MARKED.name,
                        kind = SessionKind.SCHEDULED.name,
                        note = null,
                        slotId = slot.id,
                        room = slot.room,
                    )
                }
            }

            if (newRows.isNotEmpty()) {
                newRows.chunked(500).forEach { sessions.insertIgnoring(it) }
            }
            pending.chunked(500).forEach { chunk ->
                days.mark(chunk.map { MaterialisedDayEntity(it, semester.id) })
            }
        }
    }

    override suspend fun invalidateMaterialisationFrom(semesterId: Long, from: LocalDate) {
        days.clearFrom(semesterId, from.toEpochDay())
    }
}
