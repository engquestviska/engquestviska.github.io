package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.scores.ScoresScreen
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
class ScoresTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun start(no: Int = 1) {
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { ScoresScreen(graph, Profile(Grade.G10, "XE2", no, "Student $no")) } }
    }

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun chapterShowsEachScoreAndTheResult() {
        start()
        waitText("Formative")
        compose.onNodeWithTag("tile-Task 1").assertExists()
        waitText("80.25")
        waitText("76.0")
        waitText("Safe")
    }

    @Test fun finalTabShowsCalculatedScores() {
        start()
        waitText("Formative")
        compose.onNodeWithTag("tab-final").performClick()
        waitText("Not Safe")
        waitText("38.0")
        waitText("ASAT")
    }

    @Test fun leaderboardShowsTopFiveAndMarksMe() {
        start()
        waitText("Class leaderboard")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("rank-row")).fetchSemanticsNodes().size == 5 }
        assertEquals(5, compose.onAllNodes(hasTestTag("rank-row")).fetchSemanticsNodes().size)
    }

    @Test fun studentWithNoRowSeesNoDataYet() {
        start(no = 99)
        waitText("No scores yet")
    }
}
