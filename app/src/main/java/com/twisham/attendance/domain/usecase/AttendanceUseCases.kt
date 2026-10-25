package com.twisham.attendance.domain.usecase

import com.twisham.attendance.domain.attendance.AttendanceCalculator
import com.twisham.attendance.domain.attendance.AttendanceCounts
import com.twisham.attendance.domain.attendance.OverallAttendance
import com.twisham.attendance.domain.attendance.SubjectAttendance
import com.twisham.attendance.domain.attendance.TargetProjection
import com.twisham.attendance.domain.model.AppSettings
import com.twisham.attendance.domain.model.AttendanceHealth
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.repository.SessionRepository
import com.twisham.attendance.domain.repository.SettingsRepository
import com.twisham.attendance.domain.repository.SubjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything the dashboard needs, computed in one place. */
data class SemesterAttendance(
    val overall: OverallAttendance,
    val subjects: List<SubjectAttendance>,
    val settings: AppSettings,
)

/**
 * Turns raw rows into the numbers every screen displays.
 *
 * This is the only place that joins subjects to sessions and applies the calculator,
 * so the dashboard, the subject screen and the history screen can never disagree
 * about a percentage.
 */
class AttendanceAnalytics {

    fun forSubject(
        subject: Subject,
        sessions: List<ClassSession>,
        settings: AppSettings,
    ): SubjectAttendance {
        val counts = AttendanceCounts.from(sessions, settings.excusedPolicy)
        val attended = counts.attended
        val conducted = counts.conducted
        val requirement = subject.requirement

        val stretch = settings.stretchTargets
            // A stretch target below the requirement is noise; the requirement line
            // is already shown separately.
            .filter { it > requirement }
            .map { target ->
                val alreadyMet = AttendanceCalculator.meetsRequirement(attended, conducted, target)
                TargetProjection(
                    target = target,
                    classesNeeded = AttendanceCalculator.classesNeededToReach(attended, conducted, target),
                    alreadyMet = alreadyMet,
                )
            }

        return SubjectAttendance(
            subject = subject,
            counts = counts,
            health = AttendanceCalculator.health(
                attended, conducted, requirement, settings.cautionSafeMissThreshold,
            ),
            safeMisses = AttendanceCalculator.safeMisses(attended, conducted, requirement),
            classesToRequirement = AttendanceCalculator.classesNeededToReach(
                attended, conducted, requirement,
            ),
            stretchTargets = stretch,
            trend = AttendanceCalculator.trend(
                sessions.sortedWith(compareBy({ it.date }, { it.startTime })),
                settings.trendWindowSize,
                counts,
            ),
        )
    }

    fun forSemester(
        subjects: List<Subject>,
        sessions: List<ClassSession>,
        settings: AppSettings,
    ): SemesterAttendance {
        val bySubject = sessions.groupBy { it.subjectId }
        val perSubject = subjects.map { subject ->
            forSubject(subject, bySubject[subject.id].orEmpty(), settings)
        }

        // Pooled, not averaged — see OverallAttendance for why.
        val pooled = perSubject.fold(AttendanceCounts(policy = settings.excusedPolicy)) { acc, s ->
            acc + s.counts
        }

        return SemesterAttendance(
            overall = OverallAttendance(
                counts = pooled,
                subjectCount = perSubject.size,
                safeCount = perSubject.count { it.health == AttendanceHealth.SAFE },
                cautionCount = perSubject.count { it.health == AttendanceHealth.CAUTION },
                criticalCount = perSubject.count { it.health == AttendanceHealth.CRITICAL },
                unmarkedSessions = pooled.notMarked,
            ),
            subjects = perSubject.sortedWith(
                // Worst first: the dashboard should lead with what needs action.
                compareBy<SubjectAttendance> { it.health.sortOrder() }
                    .thenBy { it.percent?.basisPoints ?: Int.MAX_VALUE }
                    .thenBy { it.subject.name.lowercase() },
            ),
            settings = settings,
        )
    }

    private fun AttendanceHealth.sortOrder(): Int = when (this) {
        AttendanceHealth.CRITICAL -> 0
        AttendanceHealth.CAUTION -> 1
        AttendanceHealth.SAFE -> 2
        AttendanceHealth.NO_DATA -> 3
    }
}

/** Reactive semester-wide attendance, recomputed whenever anything changes. */
class ObserveSemesterAttendance(
    private val subjects: SubjectRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val analytics: AttendanceAnalytics = AttendanceAnalytics(),
) {
    operator fun invoke(semesterId: Long): Flow<SemesterAttendance> = combine(
        subjects.observeActiveForSemester(semesterId),
        sessions.observeForSemester(semesterId),
        settings.observe(),
    ) { subjectList, sessionList, appSettings ->
        analytics.forSemester(subjectList, sessionList, appSettings)
    }
}

/** Reactive attendance for a single subject, including its full session history. */
data class SubjectDetail(
    val attendance: SubjectAttendance,
    val sessions: List<ClassSession>,
    val settings: AppSettings,
)

class ObserveSubjectDetail(
    private val subjects: SubjectRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val analytics: AttendanceAnalytics = AttendanceAnalytics(),
) {
    operator fun invoke(subjectId: Long): Flow<SubjectDetail?> = combine(
        subjects.observeById(subjectId),
        sessions.observeForSubject(subjectId),
        settings.observe(),
    ) { subject, sessionList, appSettings ->
        subject ?: return@combine null
        val ordered = sessionList.sortedWith(compareBy({ it.date }, { it.startTime }))
        SubjectDetail(
            attendance = analytics.forSubject(subject, ordered, appSettings),
            sessions = ordered,
            settings = appSettings,
        )
    }
}

/** Convenience for the dashboard header when no subject has data yet. */
fun OverallAttendance.headlinePercent(): Percent? = percent
