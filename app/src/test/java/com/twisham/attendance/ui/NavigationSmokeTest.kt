package com.twisham.attendance.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.twisham.attendance.AttendanceApp
import com.twisham.attendance.domain.model.AttendanceStatus
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.domain.model.Percent
import com.twisham.attendance.domain.model.Semester
import com.twisham.attendance.domain.model.Subject
import com.twisham.attendance.domain.model.SubjectType
import com.twisham.attendance.domain.model.ThemeMode
import com.twisham.attendance.domain.model.TimetableSlot
import com.twisham.attendance.ui.navigation.AttendanceAppRoot
import com.twisham.attendance.ui.theme.AttendanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Composes the whole app — the real navigation graph, the real ViewModels, the real
 * database — and walks every tab.
 *
 * The other UI tests each render one screen in isolation, which means a screen that
 * crashes on first composition could still ship unnoticed. This walks the app the way
 * a person would, so every top-level destination is proven to compose and navigate.
 *
 * It uses the Application's own container (a real on-disk database in Robolectric's
 * sandbox), because the point is to exercise the production wiring, not a test double.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NavigationSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: AttendanceApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    @Before
    fun seed() = runBlocking {
        val today = container.today()
        val semesterId = container.semesterRepository.create(
            Semester(
                name = "Semester 5",
                academicYear = "2026–27",
                startDate = today.minusWeeks(6),
                endDate = today.plusWeeks(6),
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
        container.timetableRepository.create(
            TimetableSlot(
                subjectId = subjectId,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 50),
                room = "Room 204",
            ),
        )
        container.sessionRepository.addExtra(
            ClassSession(
                semesterId = semesterId,
                subjectId = subjectId,
                date = today,
                startTime = LocalTime.MIDNIGHT,
                endTime = LocalTime.of(23, 59),
                status = AttendanceStatus.PRESENT,
            ),
        ).getOrThrow()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        container.database.semesterDao().getAll().forEach {
            container.database.semesterDao().delete(it)
        }
        container.database.materialisedDayDao().clearAll()
    }

    private fun renderApp(hasAnySemester: Boolean = true) {
        val semester = runBlocking { container.semesterRepository.getActive() }
        compose.setContent {
            AttendanceTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                AttendanceAppRoot(
                    state = MainViewModel.UiState(
                        activeSemester = semester,
                        hasAnySemester = hasAnySemester,
                        loading = false,
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Taps a bottom-bar tab.
     *
     * A label can legitimately appear twice — the Calendar screen has a "Today"
     * action in its top bar as well as the "Today" tab — so this picks the
     * bottom-most match, which is always the navigation bar.
     */
    private fun openTab(label: String) {
        awaitText(label)
        val nodes = compose.onAllNodesWithText(label, useUnmergedTree = true)
        val index = nodes.fetchSemanticsNodes()
            .withIndex()
            .maxByOrNull { it.value.boundsInWindow.top }!!
            .index
        nodes[index].performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the app opens on Today`() {
        renderApp()
        awaitText("Today")
        awaitText("Algorithms")
    }

    @Test
    fun `every top-level tab composes and navigates`() {
        renderApp()
        awaitText("Today")

        openTab("Overview")
        awaitText("Pooled across every subject this semester")

        openTab("Timetable")
        awaitText("Monday")

        openTab("Calendar")
        awaitText("What the dots mean")

        openTab("Today")
        awaitText("Algorithms")
    }

    @Test
    fun `the timetable lists the weekly plan`() {
        renderApp()
        openTab("Timetable")
        awaitText("Monday")
        awaitText("09:00")
        awaitText("Algorithms")
    }

    @Test
    fun `the calendar renders a month with its legend`() {
        renderApp()
        openTab("Calendar")
        awaitText("What the dots mean")
        awaitText("Present for all")
        awaitText("Mostly absent")
    }

    @Test
    fun `the overview reaches the subject detail screen`() {
        renderApp()
        openTab("Overview")
        awaitText("Algorithms")

        compose.onNode(hasText("Algorithms", substring = true), useUnmergedTree = true)
            .performClick()
        compose.waitForIdle()

        awaitText("Attended")
        awaitText("Conducted")
    }

    @Test
    fun `with no semester the app shows onboarding instead of empty tabs`() {
        renderApp(hasAnySemester = false)

        awaitText("Set up your semester")
        awaitText("Create semester")
    }
}
