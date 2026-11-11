package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.learn.VocabScreen
import com.engquest.app.ui.learn.wordSlotOpensAt
import com.engquest.app.ui.theme.EqTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VocabTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val me = Profile(Grade.G10, "XE2", 1, "Student 1")

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun start() {
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { VocabScreen(graph, me, onBack = {}) } }
    }

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun showsWordsLeftThisWeek() {
        start()
        waitText("30 words left this week")
    }

    @Test fun fullWeekLocksAddingAndCountsDown() {
        fake.replies["getVocabulary"] = """{"success":true,"words":[{"english":"apple","indonesian":"apel","type":"noun","at":"2099-01-01","status":""}],
            "total":30,"weekCount":30,"weeklyCap":30,"weeklyRemaining":0,"nextSlotAt":0}"""
        start()
        waitText("Full for this week")
        compose.onNodeWithTag("add-word").performScrollTo().assertIsNotEnabled()
    }

    @Test fun addingAWordSendsItWithItsType() {
        start()
        waitText("30 words left this week")
        compose.onNodeWithTag("in-english").performTextInput("river")
        compose.onNodeWithTag("in-meaning").performTextInput("sungai")
        compose.onNodeWithTag("type-noun").performClick()
        fake.replies["addVocabulary"] = """{"success":true,"added":1,"xp":2,"rejected":[],"weeklyRemaining":29}"""
        compose.onNodeWithTag("add-word").performScrollTo().performClick()
        waitText("+2 XP")
        val body = Json.parseToJsonElement(fake.calls("addVocabulary").single()).jsonObject
        val words = Json.parseToJsonElement(body["words"]!!.jsonPrimitive.content).jsonArray
        assertEquals("river", words[0].jsonObject["english"]!!.jsonPrimitive.content)
        assertEquals("sungai", words[0].jsonObject["indonesian"]!!.jsonPrimitive.content)
        assertEquals("noun", words[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun wordsAreGroupedByType() {
        fake.replies["getVocabulary"] = """{"success":true,"words":[
            {"english":"run","indonesian":"lari","type":"verb","at":"2026-11-01","status":""},
            {"english":"apple","indonesian":"apel","type":"noun","at":"2026-11-01","status":""}],
            "total":2,"weekCount":0,"weeklyCap":30,"weeklyRemaining":30,"nextSlotAt":0}"""
        start()
        waitText("Nouns")
        waitText("Verbs")
        waitText("lari")
    }

    @Test fun slotOpensAWeekAfterTheOldestWordThisWeek() {
        val day = 24L * 3600 * 1000
        val now = java.text.SimpleDateFormat("yyyy-MM-dd").apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse("2026-11-10")!!.time
        val at = wordSlotOpensAt(listOf("2026-11-05", "2026-11-08", "2026-10-01"), nextSlotAt = 0, now = now)
        assertEquals(now - 5 * day + 7 * day, at)
    }
}
