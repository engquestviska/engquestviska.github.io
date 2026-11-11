package com.engquest.app.ui

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.engquest.app.AppGraph
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShellTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var graph: AppGraph
    private val back = OnBackPressedDispatcher()
    private val backOwner = object : OnBackPressedDispatcherOwner {
        private val reg = LifecycleRegistry.createUnsafe(this as LifecycleOwner).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = reg
        override val onBackPressedDispatcher get() = back
    }

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun start(profile: Profile?) {
        graph = testGraph(context, fake.start())
        if (profile != null) graph.profiles.save(profile)
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) { StudentApp(graph) }
        }
    }

    private fun waitText(t: String) = compose.waitUntil(5000) { compose.onAllNodes(hasText(t)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun askedOnceAboutNotificationsAndNotNowRemembers() {
        graph = testGraph(context, fake.start(), buzzAsked = false)
        graph.profiles.save(Profile(Grade.G10, "XE2", 1, "Student 1"))
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) { StudentApp(graph) }
        }
        compose.onNodeWithText("Want a buzz?").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Want a buzz?").assertDoesNotExist()
        assertEquals(true, graph.notify.asked)
        assertEquals(false, graph.notify.on)
    }

    @Test fun aNotificationLinkOpensItsPage() {
        graph = testGraph(context, fake.start())
        graph.profiles.save(Profile(Grade.G10, "XE2", 1, "Student 1"))
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) { StudentApp(graph, link = "strikes") }
        }
        compose.onNodeWithTag("screen-strikes").assertIsDisplayed()
    }

    @Test fun noProfileShowsLogin() {
        start(null)
        compose.onNodeWithText("Which grade are you in?").assertIsDisplayed()
    }

    @Test fun fiveTabsAndSendOpensSend() {
        start(Profile(Grade.G10, "XE2", 1, "Student 1"))
        for (t in listOf("Home", "Scores", "Send", "Learn", "More")) compose.onNodeWithTag("tab-$t").assertIsDisplayed()
        compose.onNodeWithTag("tab-Send").performClick()
        waitText("Send a task")
    }

    @Test fun backFromScoresLandsOnHome() {
        start(Profile(Grade.G10, "XE2", 1, "Student 1"))
        compose.onNodeWithTag("tab-Scores").performClick()
        compose.waitForIdle()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("screen-home").assertIsDisplayed()
    }

    @Test fun renumberedStudentIsFollowed() {
        // Fixture XE2 roster: "Student 3" is number 2.
        start(Profile(Grade.G10, "XE2", 9, "Student 3"))
        compose.waitUntil(5000) { graph.profiles.get()?.studentNo == 2 }
        assertEquals(2, graph.profiles.get()?.studentNo)
    }

    @Test fun studentWhoLeftPicksAgain() {
        start(Profile(Grade.G10, "XE2", 1, "Nobody Here"))
        waitText("We couldn't find your name in XE2 any more. Please pick it again.")
        assertNull(graph.profiles.get())
    }
}
