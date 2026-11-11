package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.send.SendScreen
import com.engquest.app.ui.theme.EqTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SendTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { SendScreen(graph, Profile(Grade.G10, "XE2", 1, "Student 1")) } }
    }

    @After fun tearDown() = fake.stop()

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun opensOnTheFirstChapterWithWorkLeft() {
        // Fixture: chapter 1 all approved, chapter 2 waiting -> chapter 2 opens.
        waitText("Waiting for teacher")
        compose.onNodeWithTag("task-1").assertIsDisplayed()
    }

    @Test fun sendIsOffUntilPhotosAreAdded() {
        waitText("Which task?")
        compose.onNodeWithTag("task-1").performClick()
        waitText("No photos yet")
        compose.onNodeWithContentDescription("Send to teacher, add photos first").performScrollTo().assertIsDisplayed()
    }

    @Test fun approvedTaskWarnsBeforeReplacing() {
        waitText("Which task?")
        compose.onNodeWithTag("chapter-1").performClick()
        compose.onNodeWithTag("task-1").performClick()
        waitText("Already approved")
    }

    @Test fun historyListsSentTasks() {
        waitText("My sent tasks")
        waitText("Chapter 1 · Task 1")
    }
}
