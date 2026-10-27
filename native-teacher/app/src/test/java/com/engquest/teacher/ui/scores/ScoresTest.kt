package com.engquest.teacher.ui.scores

import android.content.Context
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.engquest.teacher.AppGraph
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class ScoresTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var graph: AppGraph

    private val chapter = """{"ok":true,"inputCols":["Task 1","Task 2","Task 3","Formative","Summative"],"students":[
        {"no":1,"name":"Ayu Lestari","nickname":"Ayu","scores":{"Task 1":"80","Task 2":"","Task 3":"","Formative":"","Summative":""},"average":80,"finalScore":80,"indicator":"Safe"},
        {"no":2,"name":"Budi Santoso","nickname":"","scores":{"Task 1":"60","Task 2":"","Task 3":"","Formative":"","Summative":""},"average":60,"finalScore":60,"indicator":"Not Safe"}]}"""

    private val final = """{"ok":true,"inputCols":["ASAT"],"calcCols":["Chapter 1","Chapter 2","Chapter 3","Average","Final Score","Indicator"],"students":[
        {"no":1,"name":"Ayu Lestari","nickname":"Ayu","scores":{"ASAT":""},"calc":{"Chapter 1":70,"Chapter 2":80,"Chapter 3":90,"Average":80,"Final Score":82,"Indicator":"Safe"}},
        {"no":2,"name":"Budi Santoso","nickname":"","scores":{"ASAT":""},"calc":{"Chapter 1":60,"Chapter 2":60,"Chapter 3":60,"Average":60,"Final Score":60,"Indicator":"Not Safe"}}]}"""

    @Before
    fun setUp() {
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                if (request.requestUrl?.queryParameter("action") == "getFinalScores") final else chapter,
            )
        }
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear()
            .putString("username", "teacher").putString("password", "pw1").commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().putString("tab", "scores").commit()
        graph = testGraph(context, server.url("/exec").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun show() = compose.setContent { EngQuestTheme { TeacherApp(graph) } }

    private fun waitFor(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun unsent() = runBlocking { graph.db.outboxDao().countUnsent() }

    private fun oldest() = runBlocking { graph.db.outboxDao().oldestPending() }

    private fun params(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `Final tab labels the exam column ASAS`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("chip-final").performClick()
        waitFor("score-1")
        compose.onNodeWithTag("scHeading").assertTextContains("ASAS", substring = true)
    }

    @Test
    fun `Final tab shows the final score, not Chapter 1`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("chip-final").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("avg-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("avg-1", useUnmergedTree = true).assertTextEquals("82")
        compose.onNodeWithTag("avg-2", useUnmergedTree = true).assertTextEquals("60")
    }

    @Test
    fun `typing 120 shows the range message and enqueues nothing`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("score-1").performClick()
        compose.onNodeWithTag("score-1").performTextReplacement("120")
        compose.onNodeWithTag("scStatus").assertTextContains("between 0 and 100", substring = true)
        compose.onNodeWithTag("score-1").performImeAction()
        compose.waitForIdle()
        assertEquals(0, unsent())
    }

    @Test
    fun `a valid score enqueues one save with the backend chapter id`() {
        show()
        waitFor("score-2")
        compose.onNodeWithTag("col-Task 2").performClick()
        compose.onNodeWithTag("score-2").performClick()
        compose.onNodeWithTag("score-2").performTextReplacement("90")
        compose.onNodeWithTag("score-2").performImeAction()
        compose.waitUntil(5_000) { oldest() != null }
        val item = oldest()!!
        assertEquals("saveChapterScore", item.action)
        assertEquals("XE1", item.className)
        val p = params(item.paramsJson)
        assertEquals("4", p["chapter"]!!.jsonPrimitive.content)
        assertEquals("2", p["studentNo"]!!.jsonPrimitive.content)
        assertEquals("Task 2", p["column"]!!.jsonPrimitive.content)
        assertEquals("90", p["value"]!!.jsonPrimitive.content)
        compose.onNodeWithTag("chip-5").performClick() // leaving does not send it a second time
        compose.waitForIdle()
        assertEquals(1, unsent())
    }

    @Test
    fun `an unchanged score enqueues nothing`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("score-1").performClick()
        compose.onNodeWithTag("score-1").performImeAction()
        compose.waitForIdle()
        assertEquals(0, unsent())
    }

    @Test
    fun `chapter chip asks the server for that chapter`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("chip-6").performClick()
        compose.waitUntil(5_000) { server.requestCount >= 2 }
        val last = (1..server.requestCount).map { server.takeRequest() }.last()
        assertEquals("6", last.requestUrl!!.queryParameter("chapter"))
    }

    @Test
    fun `editing the exam recomputes the final score`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("chip-final").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("avg-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("score-1").performClick()
        compose.onNodeWithTag("score-1").performTextReplacement("100")
        compose.onNodeWithTag("score-1").performImeAction()
        // (70 + 80 + 90 + 100) / 4 = 85
        compose.onNodeWithTag("avg-1", useUnmergedTree = true).assertTextEquals("85")
        compose.waitUntil(5_000) { oldest() != null }
        val item = oldest()!!
        assertEquals("saveFinalScore", item.action)
        val p = params(item.paramsJson)
        assertEquals("ASAT", p["column"]!!.jsonPrimitive.content)
        assertEquals("100", p["value"]!!.jsonPrimitive.content)
        assertEquals(false, p.containsKey("chapter"))
    }

    @Test
    fun `next moves focus to the next student`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("score-1").performClick()
        compose.onNodeWithTag("score-1").performImeAction()
        compose.onNodeWithTag("score-2").assertIsFocused()
    }

    @Test
    fun `chapter and column are remembered`() {
        show()
        waitFor("score-1")
        compose.onNodeWithTag("chip-5").performClick()
        compose.waitForIdle()
        waitFor("col-Task 3")
        compose.onNodeWithTag("col-Task 3").performClick()
        compose.waitForIdle()
        assertEquals("5", context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).getString("scoreChapter", null))
        assertEquals("Task 3", context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).getString("scoreCol_5", null))
    }

    @Test
    fun `many rapid edits with the work on a background thread end exact and a later refresh still applies`() {
        graph = testGraph(context, server.url("/exec").toString(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        show()
        waitFor("score-1")
        repeat(40) { i ->
            compose.onNodeWithTag("score-1").performClick()
            compose.onNodeWithTag("score-1").performTextReplacement((10 + i).toString())
            compose.onNodeWithTag("score-1").performImeAction()
        }
        compose.onNodeWithTag("score-1").assertTextEquals("49")
        compose.waitUntil(10_000) { unsent() == 40 }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(chapter.replace("\"Task 1\":\"80\"", "\"Task 1\":\"55\""))
        }
        runBlocking {
            graph.db.outboxDao().deleteAll()
            graph.repo.notifyDrained("XE1")
        }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("avg-1", useUnmergedTree = true).assertTextEquals("55") }.isSuccess }
    }

    @Test
    fun `a score that cannot be queued is rolled back with a message`() {
        show()
        waitFor("score-1")
        graph.db.close()
        compose.onNodeWithTag("score-1").performClick()
        compose.onNodeWithTag("score-1").performTextReplacement("95")
        compose.onNodeWithTag("score-1").performImeAction()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("scStatus").fetchSemanticsNodes().isNotEmpty() &&
                runCatching { compose.onNodeWithTag("scStatus").assertTextContains("Could not save", substring = true) }.isSuccess
        }
        compose.onNodeWithTag("avg-1", useUnmergedTree = true).assertTextEquals("80")
    }
}
