package com.engquest.teacher.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.engquest.teacher.AppGraph
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.engquest.teacher.data.Outbox
import com.engquest.teacher.data.OutboxItem
import com.engquest.teacher.data.OutboxState
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.testGraph
import com.engquest.teacher.ui.theme.EngQuestTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppShellTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val online = MutableStateFlow(true)
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        server.start()
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().putString("startTab", "attendance").commit()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Builds the graph after [setUp]-time prefs edits, so the session reads them. */
    private fun start(signedIn: Boolean = false, edit: (Context) -> Unit = {}) {
        if (signedIn) {
            context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
                .putString("username", "teacher").putString("password", "pw1").commit()
        }
        edit(context)
        graph = testGraph(context, server.url("/exec").toString(), online)
        compose.setContent { EngQuestTheme { TeacherApp(graph) } }
    }

    private fun ComposeContentTestRule.waitForText(text: String) =
        waitUntil(5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun logIn(user: String, pass: String) {
        compose.onNodeWithTag("loginUser").performTextInput(user)
        compose.onNodeWithTag("loginPass").performTextInput(pass)
        compose.onNodeWithText("Log in").performClick()
    }

    private fun addItem(state: OutboxState, error: String? = null) = runBlocking {
        graph.db.outboxDao().insert(
            OutboxItem(
                rid = "r-${System.nanoTime()}",
                action = "saveAttendance",
                className = "XE2",
                paramsJson = """{"date":"2026-10-27"}""",
                createdAt = 0,
                state = state,
                error = error,
            ),
        )
    }

    @Test
    fun `shows login when signed out`() {
        start()
        compose.onNodeWithText("EngQuest Teacher").assertIsDisplayed()
        compose.onNodeWithTag("loginUser").assertIsDisplayed()
        compose.onNodeWithTag("loginPass").assertIsDisplayed()
        compose.onNodeWithText("Log in").assertIsDisplayed()
    }

    @Test
    fun `wrong password shows a message`() {
        start()
        server.enqueue(MockResponse().setBody("""{"ok":false}"""))
        logIn("teacher", "wrong")
        compose.waitForText("didn't work")
        compose.onNodeWithText("That username or password didn't work", substring = true).assertIsDisplayed()
        assertNull(graph.session.creds())
    }

    @Test
    fun `offline login says so in plain words`() {
        start()
        server.shutdown()
        logIn("teacher", "pw1")
        compose.waitForText("No internet connection")
    }

    @Test
    fun `after login the Home tab is shown`() {
        // Every read answers at once, so Home's lists finish loading before the test ends.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                MockResponse().setBody("""{"ok":true,"success":true,"submissions":[],"top":[]}""")
        }
        start { it.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().remove("startTab").commit() }
        logIn("teacher", "pw1")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("screenTitle")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForText("No submissions yet")
        compose.onNodeWithTag("screenTitle").assertTextEquals("Home")
        compose.onNode(hasText("Home") and hasAnyAncestor(hasTestTag("tabBar"))).assertIsSelected()
        listOf("Submissions", "XP", "Scores").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("tabBar"))).assertIsDisplayed()
        }
    }

    @Test
    fun `class picker lists 8 classes and remembers the choice`() {
        var shown by mutableStateOf(true)
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
            .putString("username", "teacher").putString("password", "pw1").commit()
        graph = testGraph(context, server.url("/exec").toString(), online)
        compose.setContent { EngQuestTheme { if (shown) TeacherApp(graph) } }

        compose.onNodeWithTag("classChip").assertTextContains("X E-1").performClick()
        val options = compose.onAllNodes(hasAnyAncestor(hasTestTag("classSheet")) and hasClickAction())
            .fetchSemanticsNodes()
        assertEquals(8, options.size)
        Classes.ALL.forEach {
            compose.onNode(hasText(it.label) and hasAnyAncestor(hasTestTag("classSheet"))).assertIsDisplayed()
        }
        compose.onNode(hasText("XI F-8") and hasAnyAncestor(hasTestTag("classSheet"))).performClick()
        compose.onNodeWithTag("classChip").assertTextContains("XI F-8")
        assertEquals("XIF8", graph.prefs.getString("class", null))

        // The tab is not remembered: pick Scores, reopen the app, and it starts on its first screen again.
        compose.onNode(hasText("Scores") and hasAnyAncestor(hasTestTag("tabBar"))).performClick()
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        compose.onNodeWithTag("classChip").assertTextContains("XI F-8")
        compose.onNodeWithTag("screenTitle").assertTextEquals("Attendance")
    }

    @Test
    fun `expired session keeps the username and says changes are kept`() {
        start {
            it.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
                .putString("username", "teacher").putBoolean("expired", true).commit()
        }
        compose.onNodeWithText("Please log in again — your unsent changes are kept.").assertIsDisplayed()
        compose.onNodeWithTag("loginUser").assertTextContains("teacher")
    }

    @Test
    fun `pending count appears`() {
        start(signedIn = true)
        addItem(OutboxState.PENDING)
        addItem(OutboxState.PENDING)
        addItem(OutboxState.SENDING)
        compose.waitForText("3 changes waiting to send")

        online.value = false
        compose.waitForText("offline")
    }

    @Test
    fun `failed items show in plain words with retry and discard`() {
        start(signedIn = true)
        addItem(OutboxState.FAILED, "Server error 404.")
        compose.waitForText("1 not saved — tap to see")
        compose.onNodeWithText("1 not saved — tap to see").performClick()
        compose.onNodeWithText("Attendance for X E-2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The server couldn't save this (404).").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("not saved", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `log out warns when changes are waiting and clears them`() {
        start(signedIn = true)
        addItem(OutboxState.PENDING)
        compose.waitForText("1 change waiting to send")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Log out").performClick()
        compose.onNodeWithText("will be lost", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("confirmLogout").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("loginUser")).fetchSemanticsNodes().isNotEmpty() }
        assertNull(graph.session.creds())
        assertEquals(0, runBlocking { graph.db.outboxDao().countUnsent() })
    }

    @Test
    fun `recreating the screen mid-login leaves the button usable`() {
        graph = testGraph(context, server.url("/exec").toString(), online)
        val restorer = StateRestorationTester(compose)
        restorer.setContent { EngQuestTheme { TeacherApp(graph) } }
        // The reply takes long enough that the screen is recreated while it is being checked.
        server.enqueue(MockResponse().setBody("""{"ok":false}""").setBodyDelay(3, TimeUnit.SECONDS))
        logIn("teacher", "pw1")
        compose.onNodeWithText("Checking…").assertIsDisplayed()

        restorer.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Log in").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `coming back online sends a waiting run at once`() {
        online.value = false
        start(signedIn = true)
        runBlocking { graph.outbox.enqueue("saveAttendance", "XE1", mapOf("date" to "2026-10-27")) }
        val work = WorkManager.getInstance(context)
        Thread.sleep(200) // the outbox's start-up check may append a run of its own
        val waiting = work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().map { it.id }.toSet()

        online.value = true

        compose.waitUntil(5_000) {
            work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().any { it.id !in waiting && !it.state.isFinished }
        }
        val runs = work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get()
        assertTrue(runs.filter { it.id in waiting }.all { it.state == WorkInfo.State.CANCELLED })
    }
}
