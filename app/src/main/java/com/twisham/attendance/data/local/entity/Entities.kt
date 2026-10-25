package com.twisham.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Storage choices worth knowing:
 *  - Dates are epoch days and times are second-of-day, both plain integers. They
 *    sort and range-query correctly in SQL, they are timezone-free (a class on
 *    25 Sep is on 25 Sep wherever the phone is), and they survive locale changes.
 *  - Enums are stored by `name`, not ordinal, so reordering an enum can never
 *    silently reinterpret existing attendance records.
 *  - Percentages are basis-point integers, matching the domain's exact arithmetic.
 */

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val academicYear: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val defaultRequirementBp: Int,
    val isActive: Boolean,
    val isArchived: Boolean,
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId")],
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val name: String,
    val code: String,
    val teacher: String?,
    val requirementBp: Int,
    val type: String,
    val colorIndex: Int,
    val isActive: Boolean,
)

@Entity(
    tableName = "timetable_slots",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("subjectId"), Index("dayOfWeek")],
)
data class TimetableSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    /** java.time.DayOfWeek value: 1 = Monday .. 7 = Sunday. */
    val dayOfWeek: Int,
    val startSecond: Int,
    val endSecond: Int,
    val room: String?,
    val isActive: Boolean,
)

/**
 * One real class on one real date.
 *
 * The unique index on (subjectId, dateEpochDay, startSecond) is the guard against
 * duplicate sessions: re-running timetable materialisation for a day is idempotent,
 * and an accidental double-add is rejected by the database rather than quietly
 * doubling the denominator.
 *
 * `slotId` intentionally has no foreign key. Deleting a timetable slot must not
 * delete the attendance already recorded against it — history outlives the plan.
 */
@Entity(
    tableName = "class_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("subjectId"),
        Index("semesterId"),
        Index("dateEpochDay"),
        Index(value = ["subjectId", "dateEpochDay", "startSecond"], unique = true),
    ],
)
data class ClassSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val subjectId: Long,
    val dateEpochDay: Long,
    val startSecond: Int,
    val endSecond: Int,
    val status: String,
    val kind: String,
    val note: String?,
    val slotId: Long?,
    val room: String?,
)

/** Single-row table (id is always 0) holding user preferences. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val themeMode: String,
    val excusedPolicy: String,
    val cautionSafeMissThreshold: Int,
    val trendWindowSize: Int,
    val remindersEnabled: Boolean,
    val reminderSecond: Int,
    val weekStartsOn: Int,
    /** Comma-separated basis points, e.g. "8000,8500,9000". */
    val stretchTargetsBp: String,
)

/**
 * A day on which the timetable has already been expanded into sessions.
 *
 * Without this, an empty day is ambiguous: it could mean "no classes" or "not
 * generated yet", and re-generating would resurrect sessions the user deliberately
 * deleted. Recording the expansion explicitly makes the distinction unambiguous.
 */
@Entity(tableName = "materialised_days")
data class MaterialisedDayEntity(
    @PrimaryKey val dateEpochDay: Long,
    val semesterId: Long,
)
