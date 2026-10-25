package com.twisham.attendance.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.ThemeMode
import com.twisham.attendance.ui.theme.AttendanceTheme
import com.twisham.attendance.ui.today.TodayScreen
import com.twisham.attendance.ui.today.TodayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Drives the real Today screen against the real database.
 *
 * This is the interaction the entire app is optimised for, so it is worth testing
 * for real rather than trusting that the wiring is correct: the test taps the actual
 * button a user taps and then reads the database to confirm the mark was persisted.
 *
 * Running under Robolectric keeps the whole suite on the JVM — no emulator needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class TodayScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** Real time until a test pins it, so tests about start times can choose "now". */
    private class TestClock : Clock() {
        private var pinned: Instant? = null
        fun set(time: LocalDateTime) { pinned = time.atZone(zone).toInstant() }
        override fun instant(): Instant = pinned ?: Instant.now()
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()
    }

    private val clock = TestClock()

    // A fresh in-memory database per test, so nothing leaks between them.
    private val container = AppContainer(
        context = ApplicationProvider.getApplicationContext(),
        clock = clock,
        databaseFactory = { context ->
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        },
    )

    @After
    fun tearDown() {
        if (container.database.isOpen) container.database.close()
    }

    private fun seed(status: AttendanceStatus = AttendanceStatus.NOT_MARKED): Pair<Semester, Subject> =
        runBlocking {
            val today = container.today()
            val semesterId = container.semesterRepository.create(
                Semester(
                    name = "Semester 5",
                    academicYear = "2026–27",
                    startDate = today.minusWeeks(4),
                    endDate = today.plusWeeks(8),
                    defaultRequirement = Percent.ofWhole(75),
                    isActive = true,
                    isArchived = false,
                ),
            )
            container.semesterRepository.setActive(semesterId)
            val subjectId = container.subjectRepository.create(
                Subject(
                    semesterId = semesterId,
                    name = "Algorithms",
                    code = "DAA",
                    teacher = "Dr. Iyer",
                    requirement = Percent.ofWhole(75),
                    type = SubjectType.THEORY,
                    colorIndex = 0,
                ),
            )
            container.sessionRepository.addExtra(
                ClassSession(
                    semesterId = semesterId,
                    subjectId = subjectId,
                    date = today,
                    // Spans the whole day so the focus card is deterministically the
                    // "happening now" variant whatever time the suite runs at.
                    startTime = LocalTime.MIDNIGHT,
                    endTime = LocalTime.of(23, 59),
                    status = status,
                    room = "Room 204",
                ),
            ).getOrThrow()
            container.semesterRepository.getById(semesterId)!! to
                container.subjectRepository.getById(subjectId)!!
        }

    /**
     * An active semester with the given unmarked classes today, each 55 minutes long.
     * Pin the clock first: "today" is read from it.
     */
    private fun seedDay(vararg classes: Pair<String, LocalTime>): Semester = runBlocking {
        val today = container.today()
        val semesterId = container.semesterRepository.create(
            Semester(
                name = "Semester 5",
                academicYear = "2026–27",
                startDate = today.minusWeeks(4),
                endDate = today.plusWeeks(8),
                defaultRequirement = Percent.ofWhole(75),
                isActive = true,
                isArchived = false,
            ),
        )
        container.semesterRepository.setActive(semesterId)
        classes.forEachIndexed { index, (name, start) ->
            val subjectId = container.subjectRepository.create(
                Subject(
                    semesterId = semesterId,
                    name = name,
                    code = "S$index",
                    teacher = null,
                    requirement = Percent.ofWhole(75),
                    type = SubjectType.THEORY,
                    colorIndex = index,
                ),
            )
            container.sessionRepository.addExtra(
                ClassSession(
                    semesterId = semesterId,
                    subjectId = subjectId,
                    date = today,
                    startTime = start,
                    endTime = start.plusMinutes(55),
                    status = AttendanceStatus.NOT_MARKED,
                ),
            ).getOrThrow()
        }
        container.semesterRepository.getById(semesterId)!!
    }

    private fun storedStatuses(semester: Semester): Map<String, String> = runBlocking {
        val subjects = container.database.subjectDao().getForSemester(semester.id)
            .associate { it.id to it.name }
        container.database.sessionDao().getForSemester(semester.id)
            .associate { subjects.getValue(it.subjectId) to it.status }
    }

    /**
     * Waits for [text] to appear.
     *
     * `waitForIdle` only settles the Compose clock; the screen's state arrives from a
     * Room Flow on another dispatcher, so asserting immediately after rendering is a
     * race. This waits for the emission to actually land.
     */
    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun render(themeMode: ThemeMode = ThemeMode.LIGHT) {
        val viewModel = TodayViewModel(container)
        compose.setContent {
            AttendanceTheme(themeMode = themeMode, dynamicColor = false) {
                TodayScreen(
                    onOpenSubject = {},
                    onOpenSettings = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `today shows an unmarked class with both quick actions`() {
        seed()
        render()

        awaitText("Algorithms")
        awaitText("Present")
        compose.onNodeWithText("Present").assertIsDisplayed()
        awaitText("Absent")
        compose.onNodeWithText("Absent").assertIsDisplayed()
        awaitText("Happening now")
    }

    @Test
    fun `tapping Present persists the mark to the database`() {
        val (semester, subject) = seed()

        render()
        awaitText("Present")
        compose.onNodeWithText("Present").performClick()
        compose.waitForIdle()

        val stored = runBlocking {
            container.database.sessionDao().getForSemester(semester.id).single()
        }
        assertEquals(AttendanceStatus.PRESENT.name, stored.status)
        assertEquals(subject.id, stored.subjectId)
    }

    @Test
    fun `tapping Absent persists the mark to the database`() {
        val (semester, _) = seed()

        render()
        awaitText("Absent")
        compose.onNodeWithText("Absent").performClick()
        compose.waitForIdle()

        val stored = runBlocking {
            container.database.sessionDao().getForSemester(semester.id).single()
        }
        assertEquals(AttendanceStatus.ABSENT.name, stored.status)
    }

    @Test
    fun `an already marked class shows its status instead of the quick actions`() {
        seed(status = AttendanceStatus.PRESENT)
        render()

        awaitText("All marked for today")
        awaitText("Marked")
    }

    @Test
    fun `an empty day explains itself rather than showing a blank screen`() {
        runBlocking {
            val today = container.today()
            val id = container.semesterRepository.create(
                Semester(
                    name = "Semester 5",
                    academicYear = "2026–27",
                    startDate = today.minusWeeks(4),
                    endDate = today.plusWeeks(8),
                    defaultRequirement = Percent.ofWhole(75),
                    isActive = true,
                    isArchived = false,
                ),
            )
            container.semesterRepository.setActive(id)
        }
        render()

        awaitText("No classes today")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h480dp-mdpi")
    fun `the screen still works on a small phone`() {
        seed()
        render()

        awaitText("Algorithms")
        awaitText("Present")
        compose.onNodeWithText("Present").assertIsDisplayed()
        awaitText("Absent")
        compose.onNodeWithText("Absent").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
    fun `the screen still works on a tablet-sized window`() {
        seed()
        render()

        awaitText("Algorithms")
        awaitText("Present")
        compose.onNodeWithText("Present").assertIsDisplayed()
    }

    /**
     * Configuration change.
     *
     * The ViewModel is created outside `setContent`, exactly as it survives a real
     * rotation, and `emulateSavedInstanceStateRestore` recreates the composition the
     * way the framework does. The mark made before the rotation must still be there
     * afterwards — and must still be the one in the database.
     */
    @Test
    fun `a mark survives a configuration change`() {
        val (semester, _) = seed()
        val viewModel = TodayViewModel(container)
        val restorationTester = StateRestorationTester(compose)

        restorationTester.setContent {
            AttendanceTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                TodayScreen(onOpenSubject = {}, onOpenSettings = {}, viewModel = viewModel)
            }
        }
        awaitText("Present")

        compose.onNodeWithText("Present").performClick()
        compose.waitForIdle()
        awaitText("All marked for today")

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        awaitText("All marked for today")
        awaitText("Algorithms")

        val stored = runBlocking {
            container.database.sessionDao().getForSemester(semester.id).single()
        }
        assertEquals(AttendanceStatus.PRESENT.name, stored.status)
    }

    @Test
    fun `the screen renders in dark theme`() {
        seed()
        render(themeMode = ThemeMode.DARK)

        awaitText("Algorithms")
        awaitText("Present")
        compose.onNodeWithText("Present").assertIsDisplayed()
    }

    @Test
    fun `before a class starts Present and Absent are shown but disabled`() {
        clock.set(LocalDate.now().atTime(7, 44))
        val semester = seedDay("Algorithms" to LocalTime.of(9, 0))
        render()

        awaitText("Next up")
        compose.onNodeWithText("Present").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Absent").assertIsDisplayed().assertIsNotEnabled()

        compose.onNodeWithText("Present").performClick()
        compose.waitForIdle()
        assertEquals(AttendanceStatus.NOT_MARKED.name, storedStatuses(semester).getValue("Algorithms"))
    }

    @Test
    fun `before a class starts it can still be cancelled from the details sheet`() {
        clock.set(LocalDate.now().atTime(7, 44))
        val semester = seedDay("Algorithms" to LocalTime.of(9, 0))
        render()

        awaitText("Next up")
        compose.onNodeWithText("Cancelled, excused or add a note").assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Cancelled") and isSelectable())
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNode(hasText("Present") and isSelectable()).assertIsNotEnabled()
        compose.onNode(hasText("Absent") and isSelectable()).assertIsNotEnabled()
        compose.onNode(hasText("Excused") and isSelectable()).assertIsEnabled()
        compose.onNode(hasText("Cancelled") and isSelectable()).assertIsEnabled().performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            storedStatuses(semester).getValue("Algorithms") == AttendanceStatus.CANCELLED.name
        }
    }

    @Test
    fun `long after a class has ended it can still be marked present`() {
        // Phone left at home; back at 18:00 for a 09:00–09:55 class.
        clock.set(LocalDate.now().atTime(18, 0))
        val semester = seedDay("Algorithms" to LocalTime.of(9, 0))
        render()

        awaitText("Earlier today")
        compose.onNodeWithText("Absent").assertIsEnabled()
        compose.onNodeWithText("Present").assertIsEnabled().performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            storedStatuses(semester).getValue("Algorithms") == AttendanceStatus.PRESENT.name
        }
    }

    @Test
    fun `the buttons enable by themselves when the start time arrives`() {
        clock.set(LocalDate.now().atTime(8, 59, 30))
        seedDay("Algorithms" to LocalTime.of(9, 0))
        render()

        awaitText("Algorithms")
        compose.onNodeWithText("Present").assertIsNotEnabled()

        // The screen stays open while 09:00 passes.
        clock.set(LocalDate.now().atTime(9, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Present") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Absent").assertIsEnabled()
    }

    @Test
    fun `each class opens at its own start and mark all only touches started ones`() {
        clock.set(LocalDate.now().atTime(9, 30))
        val semester = seedDay(
            "Databases" to LocalTime.of(8, 0),
            "Algorithms" to LocalTime.of(9, 0),
            "Networks" to LocalTime.of(10, 0),
        )
        render()

        awaitText("Networks")
        compose.onNodeWithContentDescription("Mark present for Databases").assertIsEnabled()
        compose.onNodeWithContentDescription("Mark present for Algorithms").assertIsEnabled()
        compose.onNodeWithContentDescription("Mark present for Networks").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mark absent for Networks").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Mark all remaining classes present").performClick()
        compose.waitForIdle()
        // Both writes have landed once the second one shows up in the Marked list.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Algorithms, 09:00–09:55, Present")
                .fetchSemanticsNodes().isNotEmpty()
        }

        val stored = storedStatuses(semester)
        assertEquals(AttendanceStatus.PRESENT.name, stored.getValue("Databases"))
        assertEquals(AttendanceStatus.NOT_MARKED.name, stored.getValue("Networks"))
    }
}
