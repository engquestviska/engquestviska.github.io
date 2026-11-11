package com.engquest.app.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.engquest.app.domain.Grade
import com.engquest.app.ui.theme.EqTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateTest {
    @get:Rule val compose = createComposeRule()
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun checker(current: Int) =
        UpdateChecker(server.url("/app-version.json").toString(), server.url("/").toString(), current, OkHttpClient())

    private val file = """{"versionCode":11,"versionName":"2.1","apk":"EngQuest-app-7fq93kx2.apk","notes":"Faster sending"}"""

    @Test fun newerVersionIsFound() = runBlocking {
        server.enqueue(MockResponse().setBody(file))
        val info = checker(10).check()!!
        assertEquals("2.1", info.versionName)
        assertEquals(server.url("/EngQuest-app-7fq93kx2.apk").toString(), info.apkUrl)
    }

    @Test fun sameVersionOrOfflineShowsNothing() = runBlocking {
        server.enqueue(MockResponse().setBody(file))
        assertNull(checker(11).check())
        server.shutdown()
        assertNull(checker(10).check())
    }

    @Test fun bannerShowsTheNewVersion() {
        server.enqueue(MockResponse().setBody(file))
        val c = checker(10)
        compose.setContent { EqTheme(Grade.G10) { UpdateBanner(c, onUpdate = { _, _ -> }) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New version 2.1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("updateBanner").assertExists()
    }
}
