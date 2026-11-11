package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.home.HomeScreen
import com.engquest.app.ui.theme.EqTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HomeTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val me = Profile(Grade.G10, "XE2", 1, "Student 1")
    private var opened: String? = null

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun start() {
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { HomeScreen(graph, me, onOpen = { opened = it }) } }
    }

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun showsLevelFromTotalXp() {
        // Fixture: student 1 has 119 XP -> level 6 (19 of 35 into it), title Legend.
        start()
        waitText("119 XP · 16 XP to level 7")
        compose.onNodeWithText("Legend").assertExists()
        compose.onNodeWithText("Student 1").assertExists()
        compose.onNodeWithText("XE2 · No. 1").assertExists()
    }

    @Test fun showsScoreTasksAndStrikes() {
        start()
        waitText("6/9")
        compose.onNodeWithText("38").assertExists()   // Final Score in the fixture
        compose.onNodeWithText("0").assertExists()    // strikes
    }

    @Test fun noEventsSaysNothingNew() {
        fake.replies["getMyEvents"] = """{"success":true,"events":[]}"""
        start()
        waitText("Nothing new yet")
    }

    @Test fun eventsShowAndSeeAllOpensTheBell() {
        fake.replies["getMyEvents"] = """{"success":true,"events":[
            {"id":5,"kind":"approved","title":"Task approved","detail":"C1 Task 2 · Score 90","link":"send","at":"2026-11-10 09:00"},
            {"id":4,"kind":"xp","title":"+20 XP","detail":"Presenting","link":"levelxp","at":"2026-11-10 08:00"}]}"""
        start()
        waitText("C1 Task 2 · Score 90")
        compose.onAllNodes(hasText("See all"))[0].performClick()
        assertEquals("bell", opened)
    }

    @Test fun serverWithoutTheBellStillShowsHome() {
        fake.replies["getMyEvents"] = """{"error":"Action not bridged yet: getMyEvents"}"""
        start()
        waitText("Nothing new yet")
        waitText("Legend")
    }

    @Test fun recentAttendanceShowsLastMeetings() {
        start()
        waitText("Recent attendance")
        waitText("Present")
    }
}
