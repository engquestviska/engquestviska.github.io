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
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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

    private fun show(checker: UpdateChecker, waitFor: String? = null, onUpdate: (String, () -> Unit) -> Unit = { _, _ -> }) {
        compose.setContent { EngQuestTheme { UpdateBanner(checker, onUpdate) } }
        compose.waitForIdle()
        if (waitFor != null) compose.waitUntil(5_000) { compose.onAllNodesWithText(waitFor).fetchSemanticsNodes().isNotEmpty() }
        else runBlocking { delay(300) }
        compose.waitForIdle()
    }

    private fun json(code: Int) =
        MockResponse().setBody("""{"versionCode":$code,"versionName":"4.0","apk":"New.apk","notes":"Faster scores"}""")

    @Test
    fun `newer version shows the banner`() {
        server.enqueue(json(4))
        var asked: String? = null
        show(checker(), "Update") { url, _ -> asked = url }
        compose.onNodeWithText("New version 4.0 — Faster scores").assertIsDisplayed()
        compose.onNodeWithText("Update").performClick()
        assertEquals("https://example.test/New.apk", asked)
    }

    private val appContext get() = RuntimeEnvironment.getApplication()

    @Test
    fun `failed download resets the button and says so`() {
        server.enqueue(json(4))
        show(checker(), "Update") { url, onFailed ->
            UpdateInstaller(appContext).start(url, onFailed)
            // The fake download manager never finishes a download, so "complete" means "did not succeed".
            for (id in 0L..3L) {
                appContext.sendBroadcast(Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id))
            }
        }
        compose.onNodeWithText("Update").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.onNodeWithText("The download didn't finish. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Update").assertIsEnabled()
    }

    @Test
    fun `receiver is registered on the application context`() {
        val activityLike = ContextWrapper(appContext)
        UpdateInstaller(activityLike).start("https://example.test/New.apk") {}
        val registered = shadowOf(appContext as android.app.Application).registeredReceivers
        assertEquals(1, registered.count { it.intentFilter.hasAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE) })
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
