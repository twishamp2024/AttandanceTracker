package com.twisham.attendance.data.backup

import androidx.room.withTransaction
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.toDomain
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.ExcusedPolicy
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.ThemeMode
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.util.InvalidBackupException
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** How an import should treat data already in the database. */
enum class ImportMode {
    /** Adds the file's semesters alongside existing ones. Nothing is lost. */
    MERGE,

    /** Deletes everything first. Requires explicit confirmation in the UI. */
    REPLACE,
}

data class ImportSummary(
    val semesters: Int,
    val subjects: Int,
    val sessions: Int,
    val mode: ImportMode,
)

/**
 * Export and import of the user's own data.
 *
 * ## Validation strategy
 *
 * The file is parsed and fully converted to domain objects **before** a single row
 * is written. Every date, time and enum is checked, percentages are range-checked,
 * and duplicate sessions within a subject are rejected. Only once the whole file has
 * been proven sound does the write begin, and that write is a single transaction.
 *
 * The consequence is the property that matters: a malformed or truncated backup can
 * never leave the database half-imported. Either the import succeeds completely or
 * the database is exactly as it was.
 */
class BackupManager(
    private val db: AppDatabase,
    private val appVersion: String,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ---------------------------------------------------------------- export

    suspend fun exportToJson(): String {
        val settings = db.settingsDao().get()?.toDomain() ?: AppSettings()
        val semesters = db.semesterDao().getAll()

        val backupSemesters = semesters.map { semesterEntity ->
            val semester = semesterEntity.toDomain()
            val subjects = db.subjectDao().getForSemester(semester.id).map { it.toDomain() }
            val sessionsBySubject = db.sessionDao().getForSemester(semester.id)
                .map { it.toDomain() }
                .groupBy { it.subjectId }

            BackupSemester(
                name = semester.name,
                academicYear = semester.academicYear,
                startDate = semester.startDate.toString(),
                endDate = semester.endDate.toString(),
                defaultRequirement = semester.defaultRequirement.asDouble,
                isActive = semester.isActive,
                isArchived = semester.isArchived,
                subjects = subjects.map { subject ->
                    BackupSubject(
                        name = subject.name,
                        code = subject.code,
                        teacher = subject.teacher,
                        requirement = subject.requirement.asDouble,
                        type = subject.type.name,
                        colorIndex = subject.colorIndex,
                        isActive = subject.isActive,
                        timetable = db.timetableDao().getForSubject(subject.id).map { it.toDomain() }
                            .map { slot ->
                                BackupSlot(
                                    dayOfWeek = slot.dayOfWeek.name,
                                    startTime = slot.startTime.format(timeFormat),
                                    endTime = slot.endTime.format(timeFormat),
                                    room = slot.room,
                                    isActive = slot.isActive,
                                )
                            },
                        sessions = sessionsBySubject[subject.id].orEmpty()
                            .sortedWith(compareBy({ it.date }, { it.startTime }))
                            .map { s ->
                                BackupSession(
                                    date = s.date.toString(),
                                    startTime = s.startTime.format(timeFormat),
                                    endTime = s.endTime.format(timeFormat),
                                    status = s.status.name,
                                    kind = s.kind.name,
                                    note = s.note,
                                    room = s.room,
                                )
                            },
                    )
                },
            )
        }

        return json.encodeToString(
            BackupFile(
                exportedAt = LocalDateTime.now().toString(),
                appVersion = appVersion,
                settings = BackupSettings(
                    themeMode = settings.themeMode.name,
                    excusedPolicy = settings.excusedPolicy.name,
                    cautionSafeMissThreshold = settings.cautionSafeMissThreshold,
                    trendWindowSize = settings.trendWindowSize,
                    remindersEnabled = settings.remindersEnabled,
                    reminderTime = settings.reminderTime.format(timeFormat),
                    weekStartsOn = settings.weekStartsOn.name,
                    stretchTargets = settings.stretchTargets.map { it.asDouble },
                ),
                semesters = backupSemesters,
            ),
        )
    }

    /**
     * Flat CSV of every session, one row per class. Deliberately lossy — it exists
     * for spreadsheets and for pasting into an email to a tutor, not for restoring.
     * JSON is the format that round-trips.
     */
    suspend fun exportToCsv(): String = buildString {
        appendLine("semester,academic_year,subject,subject_code,date,start_time,end_time,status,kind,note")
        db.semesterDao().getAll().forEach { semesterEntity ->
            val semester = semesterEntity.toDomain()
            val subjects = db.subjectDao().getForSemester(semester.id).associateBy({ it.id }, { it.toDomain() })
            db.sessionDao().getForSemester(semester.id)
                .map { it.toDomain() }
                .sortedWith(compareBy({ it.date }, { it.startTime }))
                .forEach { s ->
                    val subject = subjects[s.subjectId] ?: return@forEach
                    appendLine(
                        listOf(
                            semester.name, semester.academicYear,
                            subject.name, subject.displayCode,
                            s.date.toString(),
                            s.startTime.format(timeFormat), s.endTime.format(timeFormat),
                            s.status.name, s.kind.name, s.note.orEmpty(),
                        ).joinToString(",") { csvCell(it) },
                    )
                }
        }
    }

    private fun csvCell(raw: String): String =
        if (raw.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + raw.replace("\"", "\"\"") + "\""
        } else {
            raw
        }

    // ---------------------------------------------------------------- import

    /** Parses and validates without writing anything. Safe to call for a preview. */
    fun validate(raw: String): BackupFile {
        val parsed = try {
            json.decodeFromString<BackupFile>(raw)
        } catch (e: Exception) {
            throw InvalidBackupException(
                "This file is not valid JSON, or does not match the backup format.", e,
            )
        }
        if (parsed.format != BackupFile.FORMAT) {
            throw InvalidBackupException(
                "This file was not created by this app (format \"${parsed.format}\").",
            )
        }
        if (parsed.version > BackupFile.VERSION) {
            throw InvalidBackupException(
                "This backup was made by a newer version of the app " +
                    "(format version ${parsed.version}). Update the app and try again.",
            )
        }
        if (parsed.semesters.isEmpty()) {
            throw InvalidBackupException("This backup contains no semesters.")
        }
        // Force full conversion so every field is proven valid before any write.
        parsed.semesters.forEach { it.toDomainChecked() }
        return parsed
    }

    suspend fun import(raw: String, mode: ImportMode): ImportSummary {
        val file = validate(raw)
        val converted = file.semesters.map { it.toDomainChecked() }

        var subjectCount = 0
        var sessionCount = 0

        db.withTransaction {
            if (mode == ImportMode.REPLACE) {
                // Cascades remove subjects, slots and sessions.
                db.semesterDao().getAll().forEach { db.semesterDao().delete(it) }
                db.materialisedDayDao().clearAll()
            }

            val existingActive = db.semesterDao().getActive() != null

            converted.forEach { parsed ->
                val makeActive = parsed.semester.isActive && !existingActive && mode == ImportMode.REPLACE
                val semesterId = db.semesterDao().insert(
                    parsed.semester.copy(id = 0, isActive = makeActive).toEntity(),
                )
                parsed.subjects.forEach { parsedSubject ->
                    val subjectId = db.subjectDao().insert(
                        parsedSubject.subject.copy(id = 0, semesterId = semesterId).toEntity(),
                    )
                    subjectCount++
                    parsedSubject.slots.forEach { slot ->
                        db.timetableDao().insert(slot.copy(id = 0, subjectId = subjectId).toEntity())
                    }
                    val rows = parsedSubject.sessions.map { session ->
                        session.copy(
                            id = 0,
                            subjectId = subjectId,
                            semesterId = semesterId,
                        ).toEntity()
                    }
                    rows.chunked(500).forEach { db.sessionDao().insertIgnoring(it) }
                    sessionCount += rows.size

                    // Days that already hold imported sessions must count as expanded,
                    // otherwise materialisation would add a second copy of each class.
                    val days = rows.map { it.dateEpochDay }.distinct()
                    days.chunked(500).forEach { chunk ->
                        db.materialisedDayDao().mark(
                            chunk.map {
                                com.twisham.attendance.data.local.entity.MaterialisedDayEntity(it, semesterId)
                            },
                        )
                    }
                }
            }

            if (db.semesterDao().getActive() == null) {
                db.semesterDao().getAll().firstOrNull()?.let {
                    db.semesterDao().setActive(it.id)
                }
            }

            file.settings.toDomainChecked()?.let { db.settingsDao().upsert(it.toEntity()) }
        }

        return ImportSummary(
            semesters = converted.size,
            subjects = subjectCount,
            sessions = sessionCount,
            mode = mode,
        )
    }

    // ------------------------------------------------------------ conversion

    private class ParsedSubject(
        val subject: Subject,
        val slots: List<TimetableSlot>,
        val sessions: List<ClassSession>,
    )

    private class ParsedSemester(
        val semester: Semester,
        val subjects: List<ParsedSubject>,
    )

    private fun BackupSemester.toDomainChecked(): ParsedSemester {
        val start = parseDate(startDate, "semester \"$name\" start date")
        val end = parseDate(endDate, "semester \"$name\" end date")
        if (end.isBefore(start)) {
            throw InvalidBackupException(
                "Semester \"$name\" ends ($endDate) before it starts ($startDate).",
            )
        }
        val semester = Semester(
            name = name.ifBlank { throw InvalidBackupException("A semester has no name.") },
            academicYear = academicYear,
            startDate = start,
            endDate = end,
            defaultRequirement = parsePercent(defaultRequirement, "semester \"$name\" requirement"),
            isActive = isActive,
            isArchived = isArchived,
        )
        return ParsedSemester(semester, subjects.map { it.toDomainChecked(name) })
    }

    private fun BackupSubject.toDomainChecked(semesterName: String): ParsedSubject {
        val label = "subject \"$name\" in \"$semesterName\""
        if (name.isBlank()) throw InvalidBackupException("A subject in \"$semesterName\" has no name.")

        val subject = Subject(
            semesterId = 0,
            name = name,
            code = code,
            teacher = teacher,
            requirement = parsePercent(requirement, "$label requirement"),
            type = parseEnum<SubjectType>(type, "$label type"),
            colorIndex = colorIndex.coerceAtLeast(0),
            isActive = isActive,
        )

        val slots = timetable.map { slot ->
            val from = parseTime(slot.startTime, "$label timetable start")
            val to = parseTime(slot.endTime, "$label timetable end")
            if (!to.isAfter(from)) {
                throw InvalidBackupException(
                    "A timetable entry for $label ends at or before it starts (${slot.startTime}–${slot.endTime}).",
                )
            }
            TimetableSlot(
                subjectId = 0,
                dayOfWeek = parseEnum<DayOfWeek>(slot.dayOfWeek, "$label timetable day"),
                startTime = from,
                endTime = to,
                room = slot.room,
                isActive = slot.isActive,
            )
        }

        val seen = HashSet<Pair<LocalDate, LocalTime>>()
        val sessions = sessions.map { s ->
            val date = parseDate(s.date, "$label session date")
            val from = parseTime(s.startTime, "$label session start")
            val to = parseTime(s.endTime, "$label session end")
            if (!seen.add(date to from)) {
                throw InvalidBackupException(
                    "$label has two classes on $date at ${s.startTime}. " +
                        "Each subject can only have one class at a given time.",
                )
            }
            ClassSession(
                semesterId = 0,
                subjectId = 0,
                date = date,
                startTime = from,
                endTime = if (to.isAfter(from)) to else from,
                status = parseEnum<AttendanceStatus>(s.status, "$label session status"),
                kind = parseEnum<SessionKind>(s.kind, "$label session kind"),
                note = s.note,
                slotId = null,
                room = s.room,
            )
        }

        return ParsedSubject(subject, slots, sessions)
    }

    private fun BackupSettings.toDomainChecked(): AppSettings? = runCatching {
        AppSettings(
            themeMode = parseEnum<ThemeMode>(themeMode, "theme"),
            excusedPolicy = parseEnum<ExcusedPolicy>(excusedPolicy, "excused policy"),
            cautionSafeMissThreshold = cautionSafeMissThreshold.coerceIn(0, 20),
            trendWindowSize = trendWindowSize.coerceIn(3, 30),
            remindersEnabled = remindersEnabled,
            reminderTime = parseTime(reminderTime, "reminder time"),
            weekStartsOn = parseEnum<DayOfWeek>(weekStartsOn, "week start"),
            stretchTargets = stretchTargets.map { parsePercent(it, "stretch target") }
                .ifEmpty { AppSettings().stretchTargets },
        )
    }.getOrNull()

    private fun parseDate(raw: String, label: String): LocalDate = try {
        LocalDate.parse(raw)
    } catch (e: Exception) {
        throw InvalidBackupException("Could not read $label: \"$raw\" is not a date (expected YYYY-MM-DD).", e)
    }

    private fun parseTime(raw: String, label: String): LocalTime = try {
        LocalTime.parse(raw, timeFormat)
    } catch (e: Exception) {
        try {
            LocalTime.parse(raw)
        } catch (e2: Exception) {
            throw InvalidBackupException("Could not read $label: \"$raw\" is not a time (expected HH:mm).", e2)
        }
    }

    private fun parsePercent(raw: Double, label: String): Percent {
        if (raw.isNaN() || raw < 0.0 || raw > 100.0) {
            throw InvalidBackupException("Could not read $label: $raw is not a percentage between 0 and 100.")
        }
        return Percent.of(raw)
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, label: String): T = try {
        enumValueOf<T>(raw.uppercase())
    } catch (e: IllegalArgumentException) {
        throw InvalidBackupException(
            "Could not read $label: \"$raw\" is not one of " +
                enumValues<T>().joinToString(", ") { it.name } + ".",
            e,
        )
    }
}
