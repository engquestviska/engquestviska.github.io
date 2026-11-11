package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.push.NotifyPrefs
import com.engquest.app.testGraph
import com.engquest.app.ui.settings.SettingsScreen
import com.engquest.app.ui.theme.EqTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val me = Profile(Grade.G10, "XE2", 1, "Student 1")

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    @Test fun prefsStartOffAndRememberEachKind() {
        val prefs = NotifyPrefs(context.getSharedPreferences("student-test", Context.MODE_PRIVATE))
        assertFalse(prefs.on)
        prefs.on = true
        prefs.set("xp", false)
        assertFalse(prefs.wants("xp"))
        assertTrue(prefs.wants("approved"))
        assertEquals("""{"on":true,"approved":true,"sent_back":true,"xp":false,"level":true,"attendance":true,"strike":true,"material":true,"announcement":true}""", prefs.json())
    }

    @Test fun turningOffXpTellsTheServer() {
        val graph = testGraph(context, fake.start())
        graph.notify.on = true
        graph.notify.saveToken("tok-1")
        compose.setContent { EqTheme(Grade.G10) { SettingsScreen(graph, me, onBack = {}, permissionGranted = { true }, askPermission = {}) } }
        compose.onNodeWithTag("switch-xp").performScrollTo().performClick()
        compose.waitUntil(10_000) { fake.calls("registerStudentDevice").isNotEmpty() }
        val body = Json.parseToJsonElement(fake.calls("registerStudentDevice").last()).jsonObject
        assertEquals("tok-1", body["token"]!!.jsonPrimitive.content)
        val p = Json.parseToJsonElement(body["prefs"]!!.jsonPrimitive.content).jsonObject
        assertFalse(p["xp"]!!.jsonPrimitive.boolean)
        assertTrue(p["on"]!!.jsonPrimitive.boolean)
    }

    @Test fun mainSwitchOffHidesTheKinds() {
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { SettingsScreen(graph, me, onBack = {}, permissionGranted = { true }, askPermission = {}) } }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Notifications are off")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("switch-xp").assertDoesNotExist()
    }

    @Test fun classNewsFollowsTheSwitchesThroughGradeTopics() {
        val log = mutableListOf<String>()
        val graph = testGraph(context, fake.start(), topicLog = log)
        graph.notify.on = true
        graph.notify.saveToken("tok-1")
        graph.notify.set("material", false)
        graph.syncNotifyPrefs(me)
        assertTrue(log.contains("+eq_g10_announcement"))
        assertTrue(log.contains("-eq_g10_material"))
        assertTrue(log.contains("-eq_g11_announcement"))
        log.clear()
        graph.notify.on = false
        graph.syncNotifyPrefs(me)
        assertFalse(log.any { it.startsWith("+") })
    }
}
