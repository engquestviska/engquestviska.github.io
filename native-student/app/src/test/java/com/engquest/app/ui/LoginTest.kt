package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.engquest.app.FakeServer
import com.engquest.app.data.ProfileStore
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.login.LoginScreen
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
class LoginTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private var done: Profile? = null

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun start(preselect: Grade? = null, note: String? = null) {
        val graph = testGraph(context, fake.start())
        compose.setContent {
            EqTheme(Grade.G10) { LoginScreen(graph.repo, preselect, note, onDone = { done = it; graph.profiles.save(it) }) }
        }
    }

    @Test fun pickingGrade11ShowsOnlyGrade11Classes() {
        start()
        compose.onNodeWithText("Grade 11").performClick()
        compose.onNodeWithText("XIF8").assertIsDisplayed()
        compose.onNodeWithText("XE1").assertDoesNotExist()
    }

    @Test fun pickingANameSavesTheProfile() {
        start(preselect = Grade.G10)
        compose.onNodeWithText("XE2").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Student 3")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Student 3").performClick()
        compose.waitForIdle()
        assertEquals(Profile(Grade.G10, "XE2", 2, "Student 3"), done)
        assertEquals(done, ProfileStore(context.getSharedPreferences("student-test", Context.MODE_PRIVATE)).get())
    }

    @Test fun searchFiltersNames() {
        start(preselect = Grade.G10)
        compose.onNodeWithText("XE2").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Student 3")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search your name").performTextInput("student 4")
        compose.onNodeWithText("Student 4").assertIsDisplayed()
        compose.onNodeWithText("Student 3").assertDoesNotExist()
    }

    @Test fun goneNoteIsShown() {
        start(note = "We couldn't find your name in XE2 any more. Please pick it again.")
        compose.onNodeWithText("We couldn't find your name in XE2 any more. Please pick it again.").assertIsDisplayed()
    }
}
