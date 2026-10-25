package com.twisham.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.twisham.attendance.data.local.entity.ClassSessionEntity
import com.twisham.attendance.data.local.entity.MaterialisedDayEntity
import com.twisham.attendance.data.local.entity.SemesterEntity
import com.twisham.attendance.data.local.entity.SettingsEntity
import com.twisham.attendance.data.local.entity.SubjectEntity
import com.twisham.attendance.data.local.entity.TimetableSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {

    @Query("SELECT * FROM semesters ORDER BY startEpochDay DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): SemesterEntity?

    @Query("SELECT * FROM semesters ORDER BY startEpochDay DESC")
    suspend fun getAll(): List<SemesterEntity>

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun getById(id: Long): SemesterEntity?

    @Query("SELECT COUNT(*) FROM semesters")
    suspend fun count(): Int

    @Insert
    suspend fun insert(semester: SemesterEntity): Long

    @Update
    suspend fun update(semester: SemesterEntity)

    @Delete
    suspend fun delete(semester: SemesterEntity)

    @Query("UPDATE semesters SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE semesters SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    /**
     * "Exactly one active semester" is an invariant, so switching has to be atomic —
     * a crash between clearing and setting would otherwise leave the app with no
     * active semester and an empty dashboard.
     */
    @Transaction
    suspend fun setActive(id: Long) {
        clearActiveFlag()
        markActive(id)
    }
}

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subjects WHERE semesterId = :semesterId ORDER BY name COLLATE NOCASE ASC")
    fun observeForSemester(semesterId: Long): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE semesterId = :semesterId AND isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveForSemester(semesterId: Long): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun observeById(id: Long): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE semesterId = :semesterId")
    suspend fun getForSemester(semesterId: Long): List<SubjectEntity>

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface TimetableDao {

    @Query(
        """
        SELECT s.* FROM timetable_slots s
        INNER JOIN subjects sub ON sub.id = s.subjectId
        WHERE sub.semesterId = :semesterId
        ORDER BY s.dayOfWeek ASC, s.startSecond ASC
        """
    )
    fun observeForSemester(semesterId: Long): Flow<List<TimetableSlotEntity>>

    @Query(
        """
        SELECT s.* FROM timetable_slots s
        INNER JOIN subjects sub ON sub.id = s.subjectId
        WHERE sub.semesterId = :semesterId AND sub.isActive = 1 AND s.isActive = 1
          AND s.dayOfWeek = :dayOfWeek
        ORDER BY s.startSecond ASC
        """
    )
    suspend fun getActiveForDay(semesterId: Long, dayOfWeek: Int): List<TimetableSlotEntity>

    @Query("SELECT * FROM timetable_slots WHERE subjectId = :subjectId ORDER BY dayOfWeek, startSecond")
    suspend fun getForSubject(subjectId: Long): List<TimetableSlotEntity>

    @Query("SELECT * FROM timetable_slots WHERE id = :id")
    suspend fun getById(id: Long): TimetableSlotEntity?

    @Insert
    suspend fun insert(slot: TimetableSlotEntity): Long

    @Update
    suspend fun update(slot: TimetableSlotEntity)

    @Delete
    suspend fun delete(slot: TimetableSlotEntity)
}

@Dao
interface SessionDao {

    @Query("SELECT * FROM class_sessions WHERE semesterId = :semesterId ORDER BY dateEpochDay ASC, startSecond ASC")
    fun observeForSemester(semesterId: Long): Flow<List<ClassSessionEntity>>

    @Query("SELECT * FROM class_sessions WHERE semesterId = :semesterId AND dateEpochDay = :epochDay ORDER BY startSecond ASC")
    fun observeForDay(semesterId: Long, epochDay: Long): Flow<List<ClassSessionEntity>>

    @Query("SELECT * FROM class_sessions WHERE subjectId = :subjectId ORDER BY dateEpochDay ASC, startSecond ASC")
    fun observeForSubject(subjectId: Long): Flow<List<ClassSessionEntity>>

    @Query(
        """
        SELECT * FROM class_sessions
        WHERE semesterId = :semesterId AND dateEpochDay BETWEEN :from AND :to
        ORDER BY dateEpochDay ASC, startSecond ASC
        """
    )
    fun observeInRange(semesterId: Long, from: Long, to: Long): Flow<List<ClassSessionEntity>>

    @Query("SELECT * FROM class_sessions WHERE id = :id")
    suspend fun getById(id: Long): ClassSessionEntity?

    @Query("SELECT * FROM class_sessions WHERE semesterId = :semesterId")
    suspend fun getForSemester(semesterId: Long): List<ClassSessionEntity>

    @Query("SELECT * FROM class_sessions WHERE semesterId = :semesterId AND dateEpochDay = :epochDay")
    suspend fun getForDay(semesterId: Long, epochDay: Long): List<ClassSessionEntity>

    /** IGNORE, not REPLACE: materialisation must never overwrite a recorded answer. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(sessions: List<ClassSessionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ClassSessionEntity): Long

    @Update
    suspend fun update(session: ClassSessionEntity)

    @Delete
    suspend fun delete(session: ClassSessionEntity)

    @Query("UPDATE class_sessions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE class_sessions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("DELETE FROM class_sessions WHERE semesterId = :semesterId")
    suspend fun deleteForSemester(semesterId: Long)
}

@Dao
interface MaterialisedDayDao {

    @Query("SELECT dateEpochDay FROM materialised_days WHERE semesterId = :semesterId AND dateEpochDay IN (:days)")
    suspend fun which(semesterId: Long, days: List<Long>): List<Long>

    @Upsert
    suspend fun mark(days: List<MaterialisedDayEntity>)

    @Query("DELETE FROM materialised_days WHERE semesterId = :semesterId")
    suspend fun clearForSemester(semesterId: Long)

    @Query("DELETE FROM materialised_days")
    suspend fun clearAll()

    @Query("DELETE FROM materialised_days WHERE semesterId = :semesterId AND dateEpochDay >= :fromEpochDay")
    suspend fun clearFrom(semesterId: Long, fromEpochDay: Long)
}

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)
}
