package com.engquest.teacher.ui.attendance

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.engquest.teacher.AppGraph
import com.engquest.teacher.testGraph
import com.engquest.teacher.ui.TeacherApp
import com.engquest.teacher.ui.theme.EngQuestTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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
@Config(qualifiers = "w360dp-h800dp") // tall enough that every row is on screen
class AttendanceTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var graph: AppGraph
    private val today = todayIso()

    /** Three students; the first class (XE1) has a meeting 1 held on [meetingDate]. */
    private fun classData(meetingDate: String?): String {
        val meetings = if (meetingDate == null) "[]" else """[{"number":1,"date":"$meetingDate"}]"""
        fun att(m1: String) = if (meetingDate == null) "{}" else """{"m1":"$m1"}"""
        return """{"ok":true,"students":[
            {"no":1,"name":"Ayu Lestari","nickname":"Ayu","attendance":${att("Present")}},
            {"no":2,"name":"Budi Santoso","nickname":"","attendance":${att("Sick")}},
            {"no":3,"name":"Citra Dewi","nickname":"Citra","attendance":${att("Absent")}}],
            "meetings":$meetings}"""
    }

    private fun serve(body: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(body)
        }
    }

    @Before
    fun setUp() {
        server.start()
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear()
            .putString("username", "teacher").putString("password", "pw1").commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().putString("startTab", "attendance").commit()
        graph = testGraph(context, server.url("/exec").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun show() = compose.setContent { EngQuestTheme { TeacherApp(graph) } }

    private fun waitFor(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun mark(no: Int, status: String): SemanticsNodeInteraction = compose.onNodeWithTag("mark-$no-$status")

    /** The oldest unsent write (these tests make at most one). */
    private fun unsent() = runBlocking { listOfNotNull(graph.db.outboxDao().oldestPending()) }

    @Test
    fun `defaults everyone to Present for a new date`() {
        serve(classData(meetingDate = null))
        show()
        waitFor("row-1")
        (1..3).forEach { mark(it, "Present").assertIsSelected() }
        compose.onNodeWithTag("count-Present", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("meetingLabel", useUnmergedTree = true).assertTextContains("New meeting", substring = true)
        compose.onNodeWithTag("saveTitle").assertTextEquals("Not saved yet")
        compose.onNodeWithTag("saveButton").assertIsEnabled()
    }

    @Test
    fun `loads stored marks for an existing meeting`() {
        serve(classData(meetingDate = today))
        show()
        waitFor("row-1")
        mark(1, "Present").assertIsSelected()
        mark(2, "Sick").assertIsSelected()
        mark(3, "Absent").assertIsSelected()
        compose.onNodeWithTag("count-Sick", useUnmergedTree = true).assertTextEquals("1")
        compose.onNodeWithTag("meetingLabel", useUnmergedTree = true).assertTextContains("Meeting 1", substring = true)
        compose.onNodeWithTag("saveTitle").assertTextEquals("Saved ✓")
        compose.onNodeWithTag("saveButton").assertIsNotEnabled()
    }

    @Test
    fun `save enqueues saveAttendance with all records`() {
        serve(classData(meetingDate = null))
        show()
        waitFor("row-1")
        mark(2, "Absent").performClick()
        mark(3, "Permit").performClick()
        compose.onNodeWithTag("saveTitle").assertTextEquals("Not saved yet")
        compose.onNodeWithTag("saveButton").performClick()
        compose.waitUntil(5_000) { unsent().isNotEmpty() }

        val item = unsent().single()
        assertEquals("saveAttendance", item.action)
        assertEquals("XE1", item.className)
        val params = Json.parseToJsonElement(item.paramsJson) as JsonObject
        assertEquals(today, params["date"]!!.jsonPrimitive.content)
        val records = Json.parseToJsonElement(params["records"]!!.jsonPrimitive.content).jsonArray
        assertEquals(
            listOf("1" to "Present", "2" to "Absent", "3" to "Permit"),
            records.map { (it as JsonObject)["no"]!!.jsonPrimitive.content to it["status"]!!.jsonPrimitive.content },
        )
        waitForText("Meeting 1")
        compose.onNodeWithTag("saveTitle").assertTextEquals("Saved ✓")
        compose.onNodeWithTag("saveButton").assertIsNotEnabled()
    }

    @Test
    fun `save with the work on a background thread ends saved and a later refresh still applies`() {
        graph = testGraph(context, server.url("/exec").toString(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        serve(classData(meetingDate = null))
        show()
        waitFor("row-1")
        mark(2, "Absent").performClick()
        compose.onNodeWithTag("saveButton").performClick()
        compose.waitUntil(5_000) { unsent().isNotEmpty() }
        waitForText("Meeting 1")
        compose.onNodeWithTag("saveTitle").assertTextEquals("Saved ✓")
        compose.onNodeWithTag("saveButton").assertIsNotEnabled()
        // Once the outbox is empty, a newer server copy must be taken in (the save is no longer "in flight").
        serve(classData(meetingDate = today).replace("\"Sick\"", "\"Permit\""))
        runBlocking {
            graph.db.outboxDao().deleteAll()
            graph.repo.notifyDrained("XE1")
        }
        compose.waitUntil(10_000) { runCatching { mark(2, "Permit").assertIsSelected() }.isSuccess }
    }

    @Test
    fun `saved marks show again offline`() {
        serve(classData(meetingDate = null))
        var shown by mutableStateOf(true)
        compose.setContent { EngQuestTheme { if (shown) TeacherApp(graph) } }
        waitFor("row-1")
        mark(2, "Sick").performClick()
        compose.onNodeWithTag("saveButton").performClick()
        compose.waitUntil(5_000) { unsent().isNotEmpty() }
        waitForText("Meeting 1")

        // Leave the screen, lose the internet, come back.
        shown = false
        compose.waitForIdle()
        server.shutdown()
        shown = true
        waitFor("row-1")
        mark(2, "Sick").assertIsSelected()
        mark(1, "Present").assertIsSelected()
        compose.onNodeWithTag("meetingLabel", useUnmergedTree = true).assertTextContains("Meeting 1", substring = true)
        compose.onNodeWithTag("saveTitle").assertTextEquals("Saved ✓")
    }

    @Test
    fun `remove asks first then enqueues`() {
        serve(classData(meetingDate = today))
        show()
        waitFor("row-1")
        compose.onNodeWithTag("attendanceMenu").performClick()
        compose.onNodeWithText("Remove this meeting").performClick()
        compose.onNodeWithText("Remove this meeting?").assertIsDisplayed()
        assertEquals(0, unsent().size)

        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, unsent().size)

        compose.onNodeWithTag("attendanceMenu").performClick()
        compose.onNodeWithText("Remove this meeting").performClick()
        compose.onNodeWithTag("confirmRemove").performClick()
        compose.waitUntil(5_000) { unsent().isNotEmpty() }
        val item = unsent().single()
        assertEquals("removeAttendanceMeeting", item.action)
        val params = Json.parseToJsonElement(item.paramsJson) as JsonObject
        assertEquals(today, params["date"]!!.jsonPrimitive.content)
        // Back to a new meeting, everyone Present.
        waitForText("New meeting")
        mark(2, "Present").assertIsSelected()
    }

    @Test
    fun `mark everyone present`() {
        serve(classData(meetingDate = today))
        show()
        waitFor("row-1")
        mark(2, "Sick").assertIsSelected()
        compose.onNodeWithTag("attendanceMenu").performClick()
        compose.onNodeWithText("Mark everyone Present").performClick()
        (1..3).forEach { mark(it, "Present").assertIsSelected() }
        compose.onNodeWithTag("count-Present", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("saveTitle").assertTextEquals("Not saved yet")
        compose.onNodeWithTag("saveButton").assertIsEnabled()
    }

    @Test
    fun `leaving with unsaved marks asks first`() {
        serve(classData(meetingDate = null))
        show()
        waitFor("row-1")
        mark(1, "Absent").performClick()
        compose.onNode(hasText("Scores") and hasAnyAncestor(hasTestTag("tabBar"))).performClick()
        compose.onNodeWithText("Leave without saving?").assertIsDisplayed()
        // Stay: still on Attendance with the mark kept.
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Attendance")
        mark(1, "Absent").assertIsSelected()
        // Leave: goes to Scores.
        compose.onNode(hasText("Scores") and hasAnyAncestor(hasTestTag("tabBar"))).performClick()
        compose.onNodeWithTag("confirmLeave").performClick()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Scores")
    }

    @Test
    fun `leaving with nothing unsaved does not ask`() {
        serve(classData(meetingDate = null))
        show()
        waitFor("row-1")
        compose.onNode(hasText("Scores") and hasAnyAncestor(hasTestTag("tabBar"))).performClick()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Scores")
        assertEquals(0, compose.onAllNodesWithText("Leave without saving?").fetchSemanticsNodes().size)
    }
}
