package com.twisham.attendance.data.local

import com.twisham.attendance.data.local.entity.ClassSessionEntity
import com.twisham.attendance.data.local.entity.SemesterEntity
import com.twisham.attendance.data.local.entity.SettingsEntity
import com.twisham.attendance.data.local.entity.SubjectEntity
import com.twisham.attendance.data.local.entity.TimetableSlotEntity
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Entity <-> domain translation.
 *
 * Enum parsing is defensive on the way *in*: a row written by a newer version of the
 * app, or hand-edited, must not crash the reader. Unknown values fall back to the
 * most conservative option rather than throwing, because a single bad row should
 * never make the whole semester unreadable.
 */

private inline fun <reified T : Enum<T>> parseEnum(raw: String, fallback: T): T =
    runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

fun SemesterEntity.toDomain() = Semester(
    id = id,
    name = name,
    academicYear = academicYear,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = LocalDate.ofEpochDay(endEpochDay),
    defaultRequirement = Percent(defaultRequirementBp.coerceIn(0, Percent.MAX_BP)),
    isActive = isActive,
    isArchived = isArchived,
)

fun Semester.toEntity() = SemesterEntity(
    id = id,
    name = name,
    academicYear = academicYear,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate.toEpochDay(),
    defaultRequirementBp = defaultRequirement.basisPoints,
    isActive = isActive,
    isArchived = isArchived,
)

fun SubjectEntity.toDomain() = Subject(
    id = id,
    semesterId = semesterId,
    name = name,
    code = code,
    teacher = teacher,
    requirement = Percent(requirementBp.coerceIn(0, Percent.MAX_BP)),
    type = parseEnum(type, SubjectType.OTHER),
    colorIndex = colorIndex,
    isActive = isActive,
)

fun Subject.toEntity() = SubjectEntity(
    id = id,
    semesterId = semesterId,
    name = name,
    code = code,
    teacher = teacher,
    requirementBp = requirement.basisPoints,
    type = type.name,
    colorIndex = colorIndex,
    isActive = isActive,
)

fun TimetableSlotEntity.toDomain() = TimetableSlot(
    id = id,
    subjectId = subjectId,
    dayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
    startTime = LocalTime.ofSecondOfDay(startSecond.coerceIn(0, 86_399).toLong()),
    endTime = LocalTime.ofSecondOfDay(endSecond.coerceIn(0, 86_399).toLong()),
    room = room,
    isActive = isActive,
)

fun TimetableSlot.toEntity() = TimetableSlotEntity(
    id = id,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek.value,
    startSecond = startTime.toSecondOfDay(),
    endSecond = endTime.toSecondOfDay(),
    room = room,
    isActive = isActive,
)

fun ClassSessionEntity.toDomain() = ClassSession(
    id = id,
    semesterId = semesterId,
    subjectId = subjectId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    startTime = LocalTime.ofSecondOfDay(startSecond.coerceIn(0, 86_399).toLong()),
    endTime = LocalTime.ofSecondOfDay(endSecond.coerceIn(0, 86_399).toLong()),
    // An unrecognised status must not be guessed as PRESENT or ABSENT; NOT_MARKED
    // keeps it out of the maths entirely and visibly asks the user to resolve it.
    status = parseEnum(status, AttendanceStatus.NOT_MARKED),
    kind = parseEnum(kind, SessionKind.SCHEDULED),
    note = note,
    slotId = slotId,
    room = room,
)

fun ClassSession.toEntity() = ClassSessionEntity(
    id = id,
    semesterId = semesterId,
    subjectId = subjectId,
    dateEpochDay = date.toEpochDay(),
    startSecond = startTime.toSecondOfDay(),
    endSecond = endTime.toSecondOfDay(),
    status = status.name,
    kind = kind.name,
    note = note?.takeIf { it.isNotBlank() },
    slotId = slotId,
    room = room,
)

fun SettingsEntity.toDomain() = AppSettings(
    themeMode = parseEnum(themeMode, ThemeMode.SYSTEM),
    excusedPolicy = parseEnum(excusedPolicy, ExcusedPolicy.IGNORE),
    cautionSafeMissThreshold = cautionSafeMissThreshold.coerceIn(0, 20),
    trendWindowSize = trendWindowSize.coerceIn(3, 30),
    remindersEnabled = remindersEnabled,
    reminderTime = LocalTime.ofSecondOfDay(reminderSecond.coerceIn(0, 86_399).toLong()),
    weekStartsOn = DayOfWeek.of(weekStartsOn.coerceIn(1, 7)),
    stretchTargets = stretchTargetsBp.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 0..Percent.MAX_BP }
        .map { Percent(it) }
        .ifEmpty { AppSettings().stretchTargets },
)

fun AppSettings.toEntity() = SettingsEntity(
    id = 0,
    themeMode = themeMode.name,
    excusedPolicy = excusedPolicy.name,
    cautionSafeMissThreshold = cautionSafeMissThreshold,
    trendWindowSize = trendWindowSize,
    remindersEnabled = remindersEnabled,
    reminderSecond = reminderTime.toSecondOfDay(),
    weekStartsOn = weekStartsOn.value,
    stretchTargetsBp = stretchTargets.joinToString(",") { it.basisPoints.toString() },
)
