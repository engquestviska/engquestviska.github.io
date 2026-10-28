package com.engquest.teacher.ui.home

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.engquest.teacher.domain.SubmissionRow
import com.engquest.teacher.testGraph
import com.engquest.teacher.ui.TeacherApp
import com.engquest.teacher.ui.theme.EngQuestTheme
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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class HomeTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun row(id: String, cls: String, no: String, task: String, day: String, status: String = "pending") =
        SubmissionRow(id, cls, no, "Student $no", task, day, status)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return MockResponse().setBody(
                    when {
                        "getLeaderboard" in path ->
                            """{"success":true,"top":[{"name":"Bella Kusuma","className":"XE1","xp":1240},{"name":"Fajar Saputra","className":"XIF7","xp":1180}]}"""
                        "getTaskSubmissions" in path ->
                            """{"success":true,"submissions":[
                              {"subId":"9","className":"XE2","studentNo":"4","studentName":"Dewi Lestari","task":"C1T2","timestamp":"2026-10-28","status":"pending"},
                              {"subId":"8","className":"XE2","studentNo":"4","studentName":"Dewi Lestari","task":"C1T2","timestamp":"2026-10-28","status":"pending"},
                              {"subId":"7","className":"XE1","studentNo":"2","studentName":"Bima Andika","task":"C2F","timestamp":"2026-10-27","status":"reviewed"}
                            ]}"""
                        else -> """{"students":[],"meetings":[]}"""
                    },
                )
            }
        }
        server.start()
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit().clear()
            .putString("username", "teacher").putString("password", "pw1").commit()
        context.getSharedPreferences("ui-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ComposeContentTestRule.waitForText(text: String) =
        waitUntil(15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for the lists to load, then scrolls Home down to the first activity line. */
    private fun showActivity() {
        compose.waitForText("Bella")
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("home").performScrollToNode(hasText("Dewi Lestari")) }.isSuccess
        }
    }

    private fun open() {
        val graph = testGraph(context, server.url("/exec").toString())
        compose.setContent { EngQuestTheme { TeacherApp(graph) } }
    }

    @Test
    fun `photos of one task on one day are one line, newest first`() {
        val lines = recentActivity(
            listOf(
                row("3", "XE1", "5", "C1T1", "2026-10-28"),
                row("2", "XE1", "5", "C1T1", "2026-10-28", "reviewed"),
                row("1", "XE2", "1", "C2S", "2026-10-27", "rejected"),
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(2, lines[0].photos)
        assertEquals("pending", lines[0].status)
        assertEquals("rejected", lines[1].status)
    }

    @Test
    fun `days read as today, yesterday or a date`() {
        val today = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 28) }
        assertEquals("Today", dayLabel("2026-10-28", today))
        assertEquals("Yesterday", dayLabel("2026-10-27", today))
        assertEquals("21 Oct", dayLabel("2026-10-21", today))
        assertEquals("junk", dayLabel("junk", today))
    }

    @Test
    fun `the app opens on Home with champions and recent activity`() {
        open()
        compose.onNodeWithTag("screenTitle").assertTextEquals("Home")
        compose.waitForText("2 photos are waiting")
        compose.onNodeWithTag("homeWaiting").assertTextEquals("2 photos are waiting for you to check.")
        showActivity()
        compose.onNodeWithText("X E-2 · sent C1 Task 2 · 2 photos").assertExists()
        compose.onNodeWithTag("home").performScrollToNode(hasText("Approved"))
    }

    @Test
    fun `tapping an activity opens that student's submissions`() {
        open()
        showActivity()
        compose.onAllNodesWithTag("activity").onFirst().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Submissions").fetchSemanticsNodes().size > 1
        }
        compose.onNodeWithTag("screenTitle").assertTextEquals("Submissions")
        assert(compose.onAllNodesWithText("X E-2", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
