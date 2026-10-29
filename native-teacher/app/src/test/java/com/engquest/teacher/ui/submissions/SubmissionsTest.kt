package com.engquest.teacher.ui.submissions

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextReplacement
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.Repo
import com.engquest.teacher.data.jpegBase64
import com.engquest.teacher.testGraph
import com.engquest.teacher.ui.TeacherApp
import com.engquest.teacher.ui.theme.EngQuestTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first test in a run also pays for starting Robolectric's graphics. */
private const val WAIT = 15_000L

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SubmissionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var graph: AppGraph

    // Ayu (3) has two photos waiting; Budi (5) one with a task code that has no score column;
    // Citra (7) is already approved; one row belongs to another class.
    private val submissions = """{"success":true,"pending":4,"submissions":[
        {"subId":"s1","fileId":"s1","className":"XE1","studentNo":"3","studentName":"Ayu Lestari","task":"C1T1","timestamp":"2026-10-20","status":"pending"},
        {"subId":"s2","fileId":"s2","className":"XE1","studentNo":"3","studentName":"Ayu Lestari","task":"C1T2","timestamp":"2026-10-21","status":"pending"},
        {"subId":"s3","fileId":"s3","className":"XE1","studentNo":"5","studentName":"Budi Santoso","task":"C9X","timestamp":"2026-10-21","status":"pending"},
        {"subId":"s4","fileId":"s4","className":"XE1","studentNo":"7","studentName":"Citra Dewi","task":"C2T1","timestamp":"2026-10-19","status":"reviewed"},
        {"subId":"s5","fileId":"s5","className":"XE2","studentNo":"2","studentName":"Dewi Putri","task":"C1T1","timestamp":"2026-10-22","status":"pending"}]}"""

    private lateinit var goodPhoto: String

    @Before
    fun setUp() {
        goodPhoto = jpegBase64(60, 40)
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.queryParameter("action")) {
                    "getTaskSubmissions" -> MockResponse().setBody(submissions)
                    // The mime type is a student's upload: a wrong one must not stop a good picture.
                    "getSubmissionPhoto" -> MockResponse().setBody(
                        if (url.queryParameter("fileId") == "s3") {
                            """{"success":true,"mimeType":"image/jpeg","dataBase64":"%%%garbage%%%"}"""
                        } else {
                            """{"success":true,"mimeType":"text/html","dataBase64":"$goodPhoto"}"""
                        },
                    )
                    else -> MockResponse().setBody("""{"success":true}""")
                }
            }
        }
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear()
            .putString("username", "teacher").putString("password", "pw1").commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().putString("startTab", "submissions").commit()
        graph = testGraph(context, server.url("/exec").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun show(link: SubmissionLink? = null) =
        compose.setContent { EngQuestTheme { TeacherApp(graph, link) } }

    private fun waitFor(tag: String) =
        compose.waitUntil(WAIT) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String) =
        compose.waitUntil(WAIT) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Taps a student in the strip under the photos (scrolled into view first). */
    private fun pick(no: String) {
        compose.onNodeWithTag("subScroll").performScrollToNode(hasTestTag("queue-$no"))
        compose.onNodeWithTag("queue-$no").performClick()
    }

    private fun unsent() = runBlocking { graph.db.outboxDao().countUnsent() }

    private fun item(id: Long) = runBlocking { graph.db.outboxDao().get(id) }!!

    private fun params(id: Long) = Json.parseToJsonElement(item(id).paramsJson) as JsonObject

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    @Test
    fun `approve with a score enqueues score, review, status in that order`() {
        show()
        waitFor("scoreBox")
        compose.onNodeWithTag("subWho").assertTextContains("Ayu Lestari", substring = true)
        compose.onNodeWithTag("scoreBox").performTextReplacement("85")
        compose.onNodeWithTag("approve").performClick()
        compose.waitUntil(WAIT) { unsent() == 5 }

        assertEquals("saveChapterScore", item(1).action)
        assertEquals("XE1", item(1).className)
        val score = params(1)
        assertEquals("4", score.str("chapter"))
        assertEquals("3", score.str("studentNo"))
        assertEquals("Task 1", score.str("column"))
        assertEquals("85", score.str("value"))

        assertEquals("reviewTaskSubmission", item(2).action)
        assertEquals("s1", params(2).str("subId"))
        assertEquals("approved", params(2).str("decision"))

        assertEquals("saveTaskStatus", item(3).action)
        assertEquals("3", params(3).str("studentNo"))
        assertEquals("""{"C1T1":true}""", params(3).str("tasks"))

        // The second photo had no score typed: only review and status.
        assertEquals("reviewTaskSubmission", item(4).action)
        assertEquals("s2", params(4).str("subId"))
        assertEquals("saveTaskStatus", item(5).action)

        // On to the next student waiting.
        compose.onNodeWithTag("subWho").assertTextContains("Budi Santoso", substring = true)
    }

    @Test
    fun `approving updates the badge and the saved list at once`() {
        show()
        waitFor("scoreBox")
        compose.onNodeWithTag("tabBadge-submissions", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("approve").performClick()
        compose.onNodeWithTag("tabBadge-submissions", useUnmergedTree = true).assertTextEquals("1")
        compose.waitUntil(WAIT) {
            val saved = runBlocking { graph.db.cacheDao().get(Repo.cacheKey("getTaskSubmissions", "XE1")) }
            saved != null && saved.json.contains("\"reviewed\"") &&
                (Json.parseToJsonElement(saved.json) as JsonObject)["submissions"].let { it as JsonArray }
                    .count { (it as JsonObject).str("status") == "pending" && it.str("className") == "XE1" } == 1
        }
    }

    @Test
    fun `no score box for a task code it can't map`() {
        show()
        waitFor("queue-5")
        pick("5")
        waitForText("no score column")
        compose.onNodeWithTag("subWho").assertTextContains("Budi Santoso", substring = true)
        assertTrue(compose.onAllNodesWithTag("scoreBox").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `send back asks first`() {
        show()
        waitFor("sendBack")
        compose.onNodeWithTag("sendBack").performClick()
        waitFor("confirmDialog")
        compose.waitForIdle()
        assertEquals(0, unsent())
        compose.onNodeWithTag("confirmDialog").performClick()
        compose.waitUntil(WAIT) { unsent() == 1 }
        assertEquals("reviewTaskSubmission", item(1).action)
        assertEquals("s1", params(1).str("subId"))
        assertEquals("rejected", params(1).str("decision"))
    }

    @Test
    fun `bad mime still shows the photo`() {
        show()
        waitFor("photo-s1")
    }

    @Test
    fun `garbage base64 shows the can't-be-shown message`() {
        show()
        waitFor("queue-5")
        pick("5")
        waitForText("Photo can't be shown")
    }

    @Test
    fun `a typed score is kept back by the leave check`() {
        show()
        waitFor("scoreBox")
        compose.onNodeWithTag("scoreBox").performTextReplacement("90")
        compose.onAllNodesWithText("XP")[0].performClick()
        waitFor("confirmLeave")
    }

    @Test
    fun `deep link opens that student`() {
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().putString("startTab", "attendance").commit()
        show(SubmissionLink("XE2", "2"))
        waitFor("subWho")
        compose.onNodeWithTag("screenTitle").assertTextEquals("Submissions")
        compose.waitUntil(WAIT) {
            runCatching { compose.onNodeWithTag("subWho").assertTextContains("Dewi Putri", substring = true) }.isSuccess
        }
        waitForText("X E-2")
    }

    @Test
    fun `deep link to a student already checked shows them under All`() {
        show(SubmissionLink("XE1", "7"))
        compose.waitUntil(WAIT) {
            runCatching { compose.onNodeWithTag("subWho").assertTextContains("Citra Dewi", substring = true) }.isSuccess
        }
    }
}
