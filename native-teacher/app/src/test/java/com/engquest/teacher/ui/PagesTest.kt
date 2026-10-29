package com.engquest.teacher.ui

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.engquest.teacher.AppGraph
import com.engquest.teacher.testGraph
import com.engquest.teacher.ui.strikes.parseStrikes
import com.engquest.teacher.ui.strikes.withStrike
import com.engquest.teacher.ui.strikes.Strike
import com.engquest.teacher.ui.tasks.mergeTasks
import com.engquest.teacher.ui.tasks.taskKeysFor
import com.engquest.teacher.ui.theme.EngQuestTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PagesTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var graph: AppGraph

    private val strikes = """{"students":[
        {"no":1,"name":"Adit Pratama","total":0,"reason":"","date":"","strikes":[]},
        {"no":2,"name":"Bella Kusuma","total":1,"reason":"Late to class","date":"2026-10-20","strikes":[{"id":5,"reason":"Late to class","date":"2026-10-20"}]}]}"""
    private val roster = """{"students":[{"no":1,"name":"Adit Pratama"},{"no":2,"name":"Bella Kusuma"}]}"""
    private val tasks = """{"students":[{"no":2,"name":"Bella Kusuma","tasks":{"C1T1":true}}],"taskKeys":["C1T1","C2T1"]}"""

    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return MockResponse().setBody(
                    when {
                        "getAllStrikes" in path -> strikes
                        "getAllTasks" in path -> tasks
                        "getStudents" in path -> roster
                        else -> """{"success":true,"submissions":[],"top":[]}"""
                    },
                )
            }
        }
        server.start()
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear()
            .putString("username", "teacher").putString("password", "pw1").commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().commit()
        graph = testGraph(context, server.url("/exec").toString())
        compose.setContent { EngQuestTheme { TeacherApp(graph) } }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun oldest() = runBlocking { graph.db.outboxDao().oldestPending() }

    @Test
    fun `strike helpers add newest first and remove the newest`() {
        val added = withStrike(json(strikes), "1", Strike("Cheating", "2026-10-29"))
        val adit = parseStrikes(added).first { it.no == "1" }
        assertEquals(1, adit.total)
        assertEquals("Cheating", adit.strikes.first().reason)
        val removed = withStrike(json(strikes), "2", null)
        assertEquals(0, parseStrikes(removed).first { it.no == "2" }.total)
    }

    @Test
    fun `tasks list every student and chapter keys`() {
        val merged = mergeTasks(json(roster), json(tasks))
        assertEquals(listOf("1", "2"), merged.map { it.no })
        assertEquals(true, merged[1].done["C1T1"])
        assertEquals(listOf("C1T1", "C1T2", "C1T3"), taskKeysFor(1, listOf("C1T1", "C2T1")))
    }

    @Test
    fun `strikes opens from Home, adds a strike and goes back`() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("quick-strikes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("quick-strikes").performClick()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Strikes")
        waitForText("Adit Pratama")
        compose.onAllNodesWithTag("strikeRow").onFirst().performClick()
        compose.onNodeWithText("Cheating").performClick()
        compose.onNodeWithTag("strikeAdd").performClick()
        compose.waitUntil(5_000) { oldest() != null }
        val item = oldest()!!
        assertEquals("addStrike", item.action)
        assertTrue(item.paramsJson.contains("Cheating"))
        compose.onNodeWithTag("pageBack").performClick()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Home")
    }

    @Test
    fun `tasks opens from Home and ticking a task queues it`() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("quick-tasks").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("quick-tasks").performClick()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Tasks")
        waitForText("Adit Pratama")
        compose.onNodeWithTag("task-1-C1T2").performClick()
        compose.waitUntil(5_000) { oldest() != null }
        val item = oldest()!!
        assertEquals("saveTaskStatus", item.action)
        assertTrue(item.paramsJson.contains("C1T2"))
    }
}
