package com.engquest.teacher.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.engquest.teacher.AppGraph
import com.engquest.teacher.GraphOwner
import com.engquest.teacher.MainActivity
import com.engquest.teacher.TestTeacherApp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTeacherApp::class)
class PushTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val graph: AppGraph get() = (context as GraphOwner).graph
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val submission = mapOf(
        "type" to "submission",
        "className" to "XIF8",
        "studentNo" to "3",
        "taskKey" to "C1",
    )

    private fun signIn() {
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
            .putString("username", "teacher").putString("password", "pw1").commit()
    }

    private fun service() = Robolectric.buildService(EqMessagingService::class.java).create().get()

    private fun pendingTokens(): List<String> = runBlocking {
        val dao = graph.db.outboxDao()
        val out = mutableListOf<String>()
        var id = 1L
        while (true) {
            val item = dao.get(id++) ?: break
            assertEquals("registerDevice", item.action)
            out += Json.parseToJsonElement(item.paramsJson).jsonObject.getValue("token").jsonPrimitive.content
        }
        out
    }

    @Test
    fun `a submission message builds a notification with the student and task and the deep-link extras`() {
        SubmissionNotifications.show(context, "Budi (XI F-8)", "sent Chapter 1 task", submission)

        val shown = shadowOf(manager).getNotification("sub-XIF8-3", SubmissionNotifications.ID)
        assertNotNull(shown)
        assertEquals("Budi (XI F-8)", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("sent Chapter 1 task", shown.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(SubmissionNotifications.CHANNEL, shown.channelId)
        assertEquals("Submissions", manager.getNotificationChannel(SubmissionNotifications.CHANNEL).name)

        val tap = shadowOf(shown.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component?.className)
        assertEquals("XIF8", tap.getStringExtra(MainActivity.EXTRA_CLASS))
        assertEquals("3", tap.getStringExtra(MainActivity.EXTRA_STUDENT))
    }

    @Test
    fun `two uploads from one student replace one notification, other students get their own`() {
        SubmissionNotifications.show(context, "Budi (XI F-8)", "sent C1", submission)
        SubmissionNotifications.show(context, "Budi (XI F-8)", "sent C2", submission + ("taskKey" to "C2"))
        SubmissionNotifications.show(context, "Sari (XI F-8)", "sent C1", submission + ("studentNo" to "4"))

        assertEquals(2, shadowOf(manager).allNotifications.size)
        val budi = shadowOf(manager).getNotification("sub-XIF8-3", SubmissionNotifications.ID)
        assertEquals("sent C2", budi.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `a message that is not a submission shows nothing`() {
        SubmissionNotifications.show(context, "Hi", "there", mapOf("type" to "other"))
        SubmissionNotifications.show(context, "Hi", "there", mapOf("type" to "submission", "className" to "XE1"))

        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `new token enqueues registerDevice`() {
        signIn()
        service().onNewToken("tok-1")

        assertEquals(listOf("tok-1"), pendingTokens())
        assertEquals("tok-1", graph.deviceToken())
    }

    @Test
    fun `the same token is registered only once`() {
        signIn()
        runBlocking {
            graph.registerDevice("tok-1")
            graph.registerDevice("tok-1")
            graph.registerDevice("tok-2")
        }

        assertEquals(listOf("tok-1", "tok-2"), pendingTokens())
    }

    @Test
    fun `a token while logged out waits for the login`() {
        service().onNewToken("tok-1")

        assertEquals(emptyList<String>(), pendingTokens())
        assertNull(graph.deviceToken())
    }

    @Test
    fun `log out forgets the token so the next login registers it again`() {
        signIn()
        runBlocking {
            graph.registerDevice("tok-1")
            graph.logOut()
        }

        assertNull(graph.deviceToken())
    }
}

// After login the app registers this phone and, once, asks to allow notifications.
@RunWith(RobolectricTestRunner::class)
class NotificationPromptTest {
    @get:org.junit.Rule
    val compose = androidx.compose.ui.test.junit4.createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val tokens = PushTokens { onToken -> onToken("tok-login") }

    private fun start(): AppGraph {
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
            .putString("username", "teacher").putString("password", "pw1").commit()
        return com.engquest.teacher.testGraph(context, "http://localhost/exec", push = tokens)
    }

    @Test
    fun `after login the phone is registered and asked once about notifications`() {
        val graph = start()
        compose.setContent { com.engquest.teacher.ui.TeacherApp(graph) }

        compose.onNodeWithText("Get a notification when a student sends a submission?").assertIsDisplayed()
        assertEquals("tok-login", graph.deviceToken())
        compose.onNodeWithText("Not now").performClick()
        compose.onAllNodesWithText("Get a notification when a student sends a submission?").assertCountEquals(0)
        assertEquals(true, graph.prefs.getBoolean(AppGraph.KEY_ASKED_NOTIFICATIONS, false))
    }

    @Test
    fun `the question is not asked again`() {
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit()
            .putBoolean(AppGraph.KEY_ASKED_NOTIFICATIONS, true).commit()
        val graph = start()
        compose.setContent { com.engquest.teacher.ui.TeacherApp(graph) }

        compose.onNodeWithTag("screenTitle").assertExists()
        compose.onAllNodesWithText("Get a notification when a student sends a submission?").assertCountEquals(0)
    }
}

// While the app is closed the phone shows the server's notification; its tap puts the
// message's data fields (plus Firebase's own google.* keys) on the launch intent.
@RunWith(RobolectricTestRunner::class)
class SystemNotificationTapTest {
    @Test
    fun `the phone's own notification tap opens the student's submissions`() {
        val tap = android.content.Intent(Intent.ACTION_MAIN)
            .putExtra("google.message_id", "0:123")
            .putExtra("type", "submission")
            .putExtra("className", "XE2")
            .putExtra("studentNo", "17")
            .putExtra("taskKey", "C1")

        assertEquals(com.engquest.teacher.ui.submissions.SubmissionLink("XE2", "17"), MainActivity.linkFrom(tap))
    }
}
