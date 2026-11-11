package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.bell.BellScreen
import com.engquest.app.ui.theme.EqTheme
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
class BellTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val me = Profile(Grade.G10, "XE2", 1, "Student 1")

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
        fake.replies["getMyEvents"] = """{"success":true,"events":[
            {"id":9,"kind":"xp","title":"+20 XP","detail":"Presenting","link":"levelxp","at":"2026-11-11 09:00"},
            {"id":7,"kind":"strike","title":"You got a strike","detail":"Late","link":"strikes","at":"2026-11-10 08:00"}]}"""
    }

    @After fun tearDown() = fake.stop()

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun openingTheBellMarksEverythingSeen() {
        val graph = testGraph(context, fake.start())
        assertTrue(graph.bellUnread(9))
        compose.setContent { EqTheme(Grade.G10) { BellScreen(graph, me, onBack = {}, onOpen = {}) } }
        waitText("Presenting")
        compose.waitUntil(5000) { !graph.bellUnread(9) }
        assertFalse(graph.bellUnread(9))
    }

    @Test fun tappingAnXpEventOpensLevelXp() {
        val graph = testGraph(context, fake.start())
        var opened: String? = null
        compose.setContent { EqTheme(Grade.G10) { BellScreen(graph, me, onBack = {}, onOpen = { opened = it }) } }
        waitText("Presenting")
        compose.onAllNodes(hasText("+20 XP"))[0].performClick()
        assertEquals("levelxp", opened)
    }

    @Test fun emptyBellSaysSo() {
        fake.replies["getMyEvents"] = """{"success":true,"events":[]}"""
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { BellScreen(graph, me, onBack = {}, onOpen = {}) } }
        waitText("Nothing yet")
    }
}
