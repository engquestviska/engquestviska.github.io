package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.more.MorePage
import com.engquest.app.ui.more.MoreScreen
import com.engquest.app.ui.theme.EqTheme
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MoreTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val me = Profile(Grade.G10, "XE2", 1, "Student 1")

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun moreListsEveryPage() {
        compose.setContent { EqTheme(Grade.G10) { MoreScreen(me, onOpen = {}, onLogOut = {}) } }
        for (t in listOf("Level / XP", "My Statistics", "Task Status", "Attendance", "My Strikes", "Student List", "Settings", "Log out"))
            compose.onNodeWithText(t).performScrollTo().assertExists()
    }

    @Test fun logOutFromTheAppReturnsToLogin() {
        val graph = testGraph(context, fake.start())
        graph.profiles.save(me)
        compose.setContent { StudentApp(graph) }
        compose.onNodeWithTag("tab-More").performClick()
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNodeWithText("Log out", useUnmergedTree = true).assertExists() // confirm dialog
        compose.onAllNodes(hasText("Yes, log out"))[0].performClick()
        waitText("Which grade are you in?")
        assertNull(graph.profiles.get())
    }

    private fun page(route: String) {
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { MorePage(graph, me, route, onBack = {}) } }
    }

    @Test fun levelXpBreaksDownTheTotal() {
        page("levelxp")
        waitText("119 XP")
        waitText("Answering")
        waitText("3 × 5 XP")
    }

    @Test fun attendanceCountsEachStatus() {
        page("attendance")
        waitText("Present")
        compose.onNodeWithTag("count-Present").assertExists()
    }

    @Test fun strikesShowTheCount() {
        page("strikes")
        waitText("No strikes")
    }

    @Test fun taskStatusShowsDoneAndNotDone() {
        page("taskstatus")
        waitText("6 of 9 tasks done")
    }

    @Test fun studentListShowsClassmates() {
        page("students")
        waitText("Student 3")
    }

    @Test fun statisticsSummarise() {
        page("stats")
        waitText("Attendance")
        waitText("Level 6")
    }
}
