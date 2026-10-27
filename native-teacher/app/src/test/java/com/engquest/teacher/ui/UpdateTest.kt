package com.engquest.teacher.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import com.engquest.teacher.ui.theme.EngQuestTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun checker(current: Int = 3) =
        UpdateChecker(server.url("/teacher-app-version.json").toString(), "https://example.test/", current, OkHttpClient())

    private fun show(checker: UpdateChecker, onUpdate: (String) -> Unit = {}) {
        compose.setContent { EngQuestTheme { UpdateBanner(checker, onUpdate) } }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    private fun json(code: Int) =
        MockResponse().setBody("""{"versionCode":$code,"versionName":"4.0","apk":"New.apk","notes":"Faster scores"}""")

    @Test
    fun `newer version shows the banner`() {
        server.enqueue(json(4))
        var asked: String? = null
        show(checker()) { asked = it }
        compose.onNodeWithText("New version 4.0 — Faster scores").assertIsDisplayed()
        compose.onNodeWithText("Update").performClick()
        assertEquals("https://example.test/New.apk", asked)
    }

    @Test
    fun `same version shows nothing`() {
        server.enqueue(json(3))
        show(checker())
        compose.onAllNodesWithText("Update").assertCountEquals(0)
    }

    @Test
    fun `offline shows nothing`() {
        server.shutdown()
        show(checker())
        compose.onAllNodesWithText("Update").assertCountEquals(0)
    }

    @Test
    fun `garbled reply shows nothing`() {
        server.enqueue(MockResponse().setBody("<html>oops"))
        show(checker())
        compose.onAllNodesWithText("Update").assertCountEquals(0)
    }
}
