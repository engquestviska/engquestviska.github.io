package com.engquest.teacher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// The activity starts from the app's graph and, signed out, shows the login screen.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTeacherApp::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensWithTheAppName() {
        compose.onNodeWithText("EngQuest Teacher").assertIsDisplayed()
    }
}

// A notification opens the app on one student's submissions, from cold start or while it is open.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTeacherApp::class)
class MainActivityLinkTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun signIn() {
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
            .putString("username", "teacher").putString("password", "pw1").commit()
    }

    private fun link(className: String, studentNo: String) =
        Intent(context, MainActivity::class.java).putExtra("className", className).putExtra("studentNo", studentNo)

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a link opens Submissions for that class from cold start`() {
        signIn()
        Robolectric.buildActivity(MainActivity::class.java, link("XIF8", "3")).setup()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Submissions")
        waitForText("XI F-8")
    }

    @Test
    fun `a link arriving while the app is open switches to it`() {
        signIn()
        // (Built directly: ActivityScenario.close() never returns under Robolectric here.)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Attendance")
        activity.onNewIntent(link("XE4", "9"))
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("screenTitle").assertTextEquals("Submissions") }.isSuccess
        }
        waitForText("X E-4")
    }
}
