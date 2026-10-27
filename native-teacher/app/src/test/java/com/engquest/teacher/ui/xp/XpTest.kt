package com.engquest.teacher.ui.xp

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.OutboxState
import com.engquest.teacher.testGraph
import com.engquest.teacher.ui.TeacherApp
import com.engquest.teacher.ui.theme.EngQuestTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class XpTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var graph: AppGraph

    /** Ayu has 10 XP (2 Answering); Budi has nothing. */
    private val data = """{"ok":true,"students":[
        {"no":1,"name":"Ayu Lestari","nickname":"Ayu","Total":10,"Indicator":"x","VocabularyBox":0,"Answering":2,"Presenting":0,"DoingTaskOnTime":0,"HelpingHand":0,"Quiz":0},
        {"no":2,"name":"Budi Santoso","nickname":"","Total":0,"Indicator":"x","VocabularyBox":0,"Answering":0,"Presenting":0,"DoingTaskOnTime":0,"HelpingHand":0,"Quiz":0}]}"""

    @Before
    fun setUp() {
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(data)
        }
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear()
            .putString("username", "teacher").putString("password", "pw1").commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().putString("tab", "xp").commit()
        graph = testGraph(context, server.url("/exec").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun show() = compose.setContent { EngQuestTheme { TeacherApp(graph) } }

    private fun waitFor(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun total(no: Int) = compose.onNodeWithTag("total-$no", useUnmergedTree = true)

    private fun unsent() = runBlocking { graph.db.outboxDao().countUnsent() }

    private fun oldest() = runBlocking { graph.db.outboxDao().oldestPending() }

    @Test
    fun `tap gives XP and updates the total by the reason's points`() {
        show()
        waitFor("tile-1")
        total(1).assertTextEquals("10 XP")
        compose.onNodeWithTag("reason-Helping Hand").performClick()
        compose.onNodeWithTag("tile-1").performClick()
        total(1).assertTextEquals("30 XP")
        compose.waitUntil(5_000) { oldest() != null }
        val item = oldest()!!
        assertEquals("incrementActiveness", item.action)
        assertEquals("XE1", item.className)
        val p = Json.parseToJsonElement(item.paramsJson) as JsonObject
        assertEquals("1", p["studentNo"]!!.jsonPrimitive.content)
        assertEquals("Helping Hand", p["column"]!!.jsonPrimitive.content)
        assertEquals("1", p["delta"]!!.jsonPrimitive.content)
        compose.onNodeWithTag("toast").assertTextContains("+20 XP", substring = true)
    }

    @Test
    fun `take back lowers it`() {
        show()
        waitFor("tile-1")
        compose.onNodeWithTag("mode-minus").performClick()
        compose.onNodeWithTag("tile-1").performClick() // Answering is the first reason
        total(1).assertTextEquals("5 XP")
        compose.waitUntil(5_000) { oldest() != null }
        val p = Json.parseToJsonElement(oldest()!!.paramsJson) as JsonObject
        assertEquals("Answering", p["column"]!!.jsonPrimitive.content)
        assertEquals("-1", p["delta"]!!.jsonPrimitive.content)
    }

    @Test
    fun `take back at zero is refused with the message`() {
        show()
        waitFor("tile-2")
        compose.onNodeWithTag("mode-minus").performClick()
        compose.onNodeWithTag("tile-2").performClick()
        compose.onNodeWithTag("toast").assertTextEquals("Budi has no Answering XP to take back")
        total(2).assertTextEquals("0 XP")
        assertEquals(0, unsent())
    }

    @Test
    fun `undo of a pending tap removes it from the outbox`() {
        show()
        waitFor("tile-1")
        compose.onNodeWithTag("tile-1").performClick()
        compose.waitUntil(5_000) { oldest() != null }
        compose.onNodeWithTag("toastUndo").performClick()
        compose.waitUntil(5_000) { unsent() == 0 }
        total(1).assertTextEquals("10 XP")
    }

    @Test
    fun `undo of a sent tap enqueues the opposite`() {
        show()
        waitFor("tile-1")
        compose.onNodeWithTag("tile-1").performClick()
        compose.waitUntil(5_000) { oldest() != null }
        runBlocking { graph.db.outboxDao().setState(oldest()!!.id, OutboxState.SENDING) }
        compose.onNodeWithTag("toastUndo").performClick()
        compose.waitUntil(5_000) { oldest() != null }
        val p = Json.parseToJsonElement(oldest()!!.paramsJson) as JsonObject
        assertEquals("-1", p["delta"]!!.jsonPrimitive.content)
        assertEquals("Answering", p["column"]!!.jsonPrimitive.content)
        assertEquals(2, unsent())
        total(1).assertTextEquals("10 XP")
    }

    @Test
    fun `mode resets to Give when reopened`() {
        var shown by mutableStateOf(true)
        compose.setContent { EngQuestTheme { if (shown) TeacherApp(graph) } }
        waitFor("tile-1")
        compose.onNodeWithTag("mode-minus").performClick()
        compose.onNodeWithTag("mode-minus").assertIsSelected()
        shown = false
        compose.waitForIdle()
        shown = true
        waitFor("tile-1")
        compose.onNodeWithTag("mode-give").assertIsSelected()
    }

    @Test
    fun `totals survive a reopen while offline`() {
        var shown by mutableStateOf(true)
        compose.setContent { EngQuestTheme { if (shown) TeacherApp(graph) } }
        waitFor("tile-1")
        compose.onNodeWithTag("tile-1").performClick()
        compose.waitUntil(5_000) { oldest() != null }
        total(1).assertTextEquals("15 XP")
        shown = false
        compose.waitForIdle()
        server.shutdown()
        shown = true
        waitFor("tile-1")
        total(1).assertTextEquals("15 XP")
    }

    @Test
    fun `back with unsaved attendance marks asks first`() {
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().putString("tab", "attendance").commit()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                """{"ok":true,"students":[{"no":1,"name":"Ayu Lestari","nickname":"Ayu","attendance":{}}],"meetings":[]}""",
            )
        }
        var back: OnBackPressedDispatcher? = null
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            EngQuestTheme { TeacherApp(graph) }
        }
        waitFor("row-1")
        compose.onNodeWithTag("mark-1-Absent").performClick()
        compose.onNodeWithTag("saveTitle").assertTextEquals("Not saved yet")
        compose.waitForIdle()
        compose.runOnUiThread { back!!.onBackPressed() }
        compose.waitForIdle()
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("confirmLeave").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("screenTitle").assertTextEquals("Attendance")

        // Leave: Back goes through, and the next Back with unsaved marks asks again.
        compose.onNodeWithTag("confirmLeave").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("mark-1-Present").performClick()
        compose.onNodeWithTag("mark-1-Absent").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { back!!.onBackPressed() }
        compose.waitForIdle()
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("confirmLeave").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `take back never drives the total below zero`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                """{"ok":true,"students":[{"no":1,"name":"Ayu Lestari","nickname":"Ayu","Total":3}]}""",
            )
        }
        show()
        waitFor("tile-1")
        compose.onNodeWithTag("mode-minus").performClick()
        compose.onNodeWithTag("tile-1").performClick()
        total(1).assertTextEquals("0 XP")
    }

    @Test
    fun `a tap that cannot be queued is rolled back with a message`() {
        show()
        waitFor("tile-1")
        graph.db.close()
        compose.onNodeWithTag("tile-1").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("toast").fetchSemanticsNodes().isNotEmpty() &&
                runCatching { compose.onNodeWithTag("toast").assertTextContains("Could not save", substring = true) }.isSuccess
        }
        total(1).assertTextEquals("10 XP")
    }
}
