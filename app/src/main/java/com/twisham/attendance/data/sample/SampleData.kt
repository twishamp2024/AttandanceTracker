package com.twisham.attendance.data.sample

import androidx.room.withTransaction
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.entity.MaterialisedDayEntity
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.SessionKind
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Optional demo data, created only when the user asks for it from Settings.
 *
 * It is never seeded automatically. A first launch gives an empty app with a real
 * "create your semester" flow, because sample rows quietly mixed into someone's
 * actual attendance would be worse than no sample data at all. The generated
 * semester is clearly named and can be deleted like any other.
 *
 * The generator is seeded with a fixed value so the demo looks the same every time,
 * and it deliberately produces one healthy, one borderline and one failing subject
 * so every visual state can be checked at a glance.
 */
object SampleData {

    const val SEMESTER_NAME = "Semester 5 (Demo)"

    private data class Spec(
        val name: String,
        val code: String,
        val teacher: String,
        val type: SubjectType,
        val requirement: Int,
        val colorIndex: Int,
        /** Probability of having attended any given past class. */
        val attendRate: Double,
        val slots: List<Triple<DayOfWeek, LocalTime, LocalTime>>,
    )

    private val specs = listOf(
        Spec(
            "Design & Analysis of Algorithms", "DAA", "Dr. R. Iyer", SubjectType.THEORY, 75, 0, 0.94,
            listOf(
                Triple(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 50)),
                Triple(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 50)),
                Triple(DayOfWeek.FRIDAY, LocalTime.of(11, 0), LocalTime.of(11, 55)),
            ),
        ),
        Spec(
            "Database Management Systems", "DBMS", "Prof. S. Nair", SubjectType.THEORY, 75, 1, 0.78,
            listOf(
                Triple(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 50)),
                Triple(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(10, 50)),
            ),
        ),
        Spec(
            "Microprocessors", "MP", "Dr. A. Kulkarni", SubjectType.THEORY, 75, 2, 0.70,
            listOf(
                Triple(DayOfWeek.MONDAY, LocalTime.of(10, 50), LocalTime.of(12, 40)),
                Triple(DayOfWeek.WEDNESDAY, LocalTime.of(10, 50), LocalTime.of(12, 40)),
            ),
        ),
        Spec(
            "Microprocessors Lab", "MP-L", "Dr. A. Kulkarni", SubjectType.LAB, 80, 3, 0.88,
            listOf(Triple(DayOfWeek.MONDAY, LocalTime.of(13, 50), LocalTime.of(16, 35))),
        ),
        Spec(
            "Operating Systems", "OS", "Prof. M. Desai", SubjectType.THEORY, 75, 4, 0.91,
            listOf(
                Triple(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(12, 40)),
                Triple(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(10, 50)),
            ),
        ),
        Spec(
            "Technical Communication", "TC", "Ms. P. Rao", SubjectType.TUTORIAL, 70, 5, 0.85,
            listOf(Triple(DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(14, 55))),
        ),
    )

    /** Returns the id of the created semester. */
    suspend fun install(db: AppDatabase, today: LocalDate): Long = db.withTransaction {
        val random = Random(20260925)
        val start = today.minusWeeks(10)
        val end = today.plusWeeks(6)

        val semesterId = db.semesterDao().insert(
            Semester(
                name = SEMESTER_NAME,
                academicYear = "${today.year}–${(today.year + 1) % 100}",
                startDate = start,
                endDate = end,
                defaultRequirement = Percent.ofWhole(75),
                isActive = false,
                isArchived = false,
            ).toEntity(),
        )

        val sessions = ArrayList<ClassSession>()

        specs.forEach { spec ->
            val subjectId = db.subjectDao().insert(
                Subject(
                    semesterId = semesterId,
                    name = spec.name,
                    code = spec.code,
                    teacher = spec.teacher,
                    requirement = Percent.ofWhole(spec.requirement),
                    type = spec.type,
                    colorIndex = spec.colorIndex,
                    isActive = true,
                ).toEntity(),
            )

            spec.slots.forEach { (day, from, to) ->
                db.timetableDao().insert(
                    TimetableSlot(
                        subjectId = subjectId,
                        dayOfWeek = day,
                        startTime = from,
                        endTime = to,
                        room = "Room ${200 + spec.colorIndex * 3}",
                        isActive = true,
                    ).toEntity(),
                )
            }

            // Fill in history from the start of the semester up to today.
            var date = start
            while (!date.isAfter(today)) {
                spec.slots.filter { it.first == date.dayOfWeek }.forEach { (_, from, to) ->
                    val status = when {
                        // A couple of genuinely cancelled classes, so the UI can show
                        // that they do not count against attendance.
                        random.nextDouble() < 0.04 -> AttendanceStatus.CANCELLED
                        random.nextDouble() < 0.03 -> AttendanceStatus.EXCUSED
                        date == today -> AttendanceStatus.NOT_MARKED
                        random.nextDouble() < spec.attendRate -> AttendanceStatus.PRESENT
                        else -> AttendanceStatus.ABSENT
                    }
                    sessions += ClassSession(
                        semesterId = semesterId,
                        subjectId = subjectId,
                        date = date,
                        startTime = from,
                        endTime = to,
                        status = status,
                        kind = SessionKind.SCHEDULED,
                        note = when (status) {
                            AttendanceStatus.CANCELLED -> "Teacher unavailable"
                            AttendanceStatus.EXCUSED -> "Medical leave"
                            else -> null
                        },
                        room = "Room ${200 + spec.colorIndex * 3}",
                    )
                }
                date = date.plusDays(1)
            }

            // One extra class, to exercise the EXTRA session kind.
            val extraDate = today.minusWeeks(2)
            sessions += ClassSession(
                semesterId = semesterId,
                subjectId = subjectId,
                date = extraDate,
                startTime = LocalTime.of(17, 0),
                endTime = LocalTime.of(17, 55),
                status = AttendanceStatus.PRESENT,
                kind = SessionKind.EXTRA,
                note = "Extra revision class",
            )
        }

        sessions.map { it.toEntity() }.chunked(500).forEach { db.sessionDao().insertIgnoring(it) }

        // Every day up to today has been filled in, so mark it expanded to stop
        // materialisation from adding duplicates on top of the generated history.
        val markedDays = generateSequence(start) { d ->
            d.plusDays(1).takeIf { !it.isAfter(today) }
        }.map { MaterialisedDayEntity(it.toEpochDay(), semesterId) }.toList()
        markedDays.chunked(500).forEach { db.materialisedDayDao().mark(it) }

        semesterId
    }
}
