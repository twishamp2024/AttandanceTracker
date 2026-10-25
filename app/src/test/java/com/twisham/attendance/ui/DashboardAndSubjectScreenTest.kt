package com.twisham.attendance.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twisham.attendance.data.local.AppDatabase
import com.twisham.attendance.data.local.toEntity
import com.twisham.attendance.di.AppContainer
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.ThemeMode
import com.twisham.attendance.ui.dashboard.DashboardScreen
import com.twisham.attendance.ui.dashboard.DashboardViewModel
import com.twisham.attendance.ui.subject.SubjectDetailScreen
import com.twisham.attendance.ui.subject.SubjectDetailViewModel
import com.twisham.attendance.ui.theme.AttendanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Checks that the numbers the domain layer computes actually reach the screen, and
 * that the What-if simulator is genuinely read-only.
 *
 * Text *positions* are not asserted — Robolectric measures with a stub font, so a
 * Text node's reported width is about one pixel per character and means nothing.
 * Existence and content are meaningful; pixel layout is verified on a real device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class DashboardAndSubjectScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val container = AppContainer(
        context = ApplicationProvider.getApplicationContext(),
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

    /** Builds a subject with exactly [present] attended out of [conducted] conducted. */
    private fun seed(present: Int, conducted: Int, requirement: Int = 75): Pair<Long, Long> =
        runBlocking {
            val today = container.today()
            val semesterId = container.semesterRepository.create(
                Semester(
                    name = "Semester 5",
                    academicYear = "2026–27",
                    startDate = today.minusWeeks(20),
                    endDate = today.plusWeeks(8),
                    defaultRequirement = Percent.ofWhole(requirement),
                    isActive = true,
                    isArchived = false,
                ),
            )
            container.semesterRepository.setActive(semesterId)
            val subjectId = container.subjectRepository.create(
                Subject(
                    semesterId = semesterId,
                    name = "Database Management Systems",
                    code = "DBMS",
                    teacher = null,
                    requirement = Percent.ofWhole(requirement),
                    type = SubjectType.THEORY,
                    colorIndex = 1,
                ),
            )
            repeat(conducted) { index ->
                container.database.sessionDao().insert(
                    ClassSession(
                        semesterId = semesterId,
                        subjectId = subjectId,
                        date = today.minusDays((conducted - index).toLong()),
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 0),
                        status = if (index < present) AttendanceStatus.PRESENT
                        else AttendanceStatus.ABSENT,
                    ).toEntity(),
                )
            }
            semesterId to subjectId
        }

    /**
     * Scrolls the list until [text] is composed, then asserts it is present.
     *
     * Both steps matter: a LazyColumn does not compose what is far off screen, and
     * strings such as "Caution" appear both in the overall summary counts and on the
     * subject's own chip, so uniqueness is the wrong assertion.
     */
    private fun assertTextPresent(text: String) {
        // The screen's state arrives from a Room Flow on another dispatcher, so wait
        // for the content to land rather than racing it.
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
            }
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val matches = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assert(matches.isNotEmpty()) { "expected to find \"$text\" on screen" }
    }

    private fun renderDashboard() {
        val viewModel = DashboardViewModel(container)
        compose.setContent {
            AttendanceTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                DashboardScreen(
                    onOpenSubject = {},
                    onAddSubject = {},
                    onOpenSettings = {},
                    onOpenHistory = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun renderSubject(subjectId: Long) {
        val viewModel = SubjectDetailViewModel(container)
        compose.setContent {
            AttendanceTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                SubjectDetailScreen(
                    subjectId = subjectId,
                    onBack = {},
                    onEdit = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    // ------------------------------------------------------------- dashboard

    @Test
    fun `dashboard shows the computed percentage and the caution guidance`() {
        seed(present = 40, conducted = 52)
        renderDashboard()

        // 40/52 = 76.92%, which must display as 76.9% and allow exactly one more miss.
        assertTextPresent("76.9%")
        assertTextPresent("You can miss 1 more class.")
        assertTextPresent("40 / 52 · needs 75%")
        assertTextPresent("Caution")
    }

    @Test
    fun `a failing subject is told exactly how many classes to attend`() {
        seed(present = 35, conducted = 49)
        renderDashboard()

        // 35/49 = 71.4%; attending the next 7 gives 42/56 = exactly 75%.
        assertTextPresent("Attend the next 7 classes to reach 75%.")
        assertTextPresent("Critical")
    }

    @Test
    fun `a comfortable subject reports its spare classes`() {
        seed(present = 48, conducted = 52)
        renderDashboard()

        // 48/52 = 92.3%; 48/64 is exactly 75%, so twelve are affordable.
        assertTextPresent("You can miss 12 classes.")
        assertTextPresent("Safe")
    }

    @Test
    fun `the dashboard explains how the overall figure is produced`() {
        seed(present = 40, conducted = 52)
        renderDashboard()

        assertTextPresent("Pooled across every subject this semester")
        assertTextPresent("40 of 52 classes attended")
    }

    @Test
    fun `an empty semester offers the next step instead of a blank screen`() {
        runBlocking {
            val today = container.today()
            val id = container.semesterRepository.create(
                Semester(
                    name = "Semester 5",
                    academicYear = "2026–27",
                    startDate = today,
                    endDate = today.plusWeeks(8),
                    defaultRequirement = Percent.ofWhole(75),
                    isActive = true,
                    isArchived = false,
                ),
            )
            container.semesterRepository.setActive(id)
        }
        renderDashboard()

        assertTextPresent("No subjects yet")
        compose.onNodeWithText(useUnmergedTree = true, text = "Add a subject").assertIsDisplayed()
    }

    // --------------------------------------------------------- subject detail

    @Test
    fun `subject detail shows the full breakdown`() {
        val (_, subjectId) = seed(present = 40, conducted = 52)
        renderSubject(subjectId)

        assertTextPresent("Attended")
        assertTextPresent("Conducted")
        assertTextPresent("Can miss")
        assertTextPresent("You can miss 1 more class.")
    }

    @Test
    fun `the what-if simulator projects a change without touching the database`() {
        val (semesterId, subjectId) = seed(present = 40, conducted = 52)
        val before = runBlocking { container.database.sessionDao().getForSemester(semesterId) }

        renderSubject(subjectId)
        assertTextPresent("What if?")
        assertTextPresent("Right now")

        // Two steppers are on screen: "Attend the next" then "Miss the next".
        // Tapping the second one's "+" projects a single missed class.
        val plusButtons = compose.onAllNodesWithText("+", useUnmergedTree = true)
        plusButtons[1].performClick()
        compose.waitForIdle()

        // 40/53 = 75.47%, still above the line.
        assertTextPresent("Would become")
        assertTextPresent("40 / 53 classes")

        // Tapping again gives 40/54 = 74.07%, which drops below the requirement.
        plusButtons[1].performClick()
        compose.waitForIdle()
        assertTextPresent("40 / 54 classes")
        assertTextPresent("That drops you below the required 75%.")

        // The crucial property: nothing was written.
        val after = runBlocking { container.database.sessionDao().getForSemester(semesterId) }
        assertEquals(before.size, after.size)
        assertEquals(
            before.count { it.status == AttendanceStatus.PRESENT.name },
            after.count { it.status == AttendanceStatus.PRESENT.name },
        )
        assertEquals(before.map { it.id to it.status }, after.map { it.id to it.status })
    }
}
