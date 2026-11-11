package com.engquest.app.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.learn.LearnScreen
import com.engquest.app.ui.learn.MaterialKind
import com.engquest.app.ui.learn.MaterialsScreen
import com.engquest.app.ui.theme.EqTheme
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
class LearnTest {
    @get:Rule val compose = createComposeRule()
    private val fake = FakeServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val me = Profile(Grade.G10, "XE2", 1, "Student 1")

    @Before fun setUp() {
        context.getSharedPreferences("student-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() = fake.stop()

    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun learnListsFourPlaces() {
        var opened: String? = null
        compose.setContent { EqTheme(Grade.G10) { LearnScreen(onOpen = { opened = it }) } }
        for (t in listOf("Materials", "Assignments", "Reading", "My Vocabulary")) compose.onNodeWithText(t).assertExists()
        compose.onNodeWithText("Reading").performClick()
        assertEquals("reading", opened)
    }

    @Test fun materialsGroupByChapterAndOpenTheLink() {
        val graph = testGraph(context, fake.start())
        var link: String? = null
        compose.setContent { EqTheme(Grade.G10) { MaterialsScreen(graph, me, MaterialKind.MATERIALS, onBack = {}, openLink = { link = it }) } }
        waitText("Descriptive Text")
        waitText("Buku Cetak Kelas X PDF")
        compose.onAllNodes(hasText("Buku Cetak Kelas X PDF"))[0].performClick()
        assertTrue(link!!.startsWith("https://drive.google.com/"))
        assertTrue(fake.calls("getMaterials").any { it.contains("chapter=1") })
    }

    @Test fun assignmentsAskForAssignChapters() {
        fake.replies["getMaterials"] = """{"materials":[]}"""
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { MaterialsScreen(graph, me, MaterialKind.ASSIGNMENTS, onBack = {}, openLink = {}) } }
        waitText("Nothing here yet")
        assertTrue(fake.calls("getMaterials").any { it.contains("chapter=assign2") })
    }

    @Test fun readingUsesTheReadingList() {
        val graph = testGraph(context, fake.start())
        compose.setContent { EqTheme(Grade.G10) { MaterialsScreen(graph, me, MaterialKind.READING, onBack = {}, openLink = {}) } }
        waitText("Buku Cetak Kelas X PDF")
        assertTrue(fake.calls("getMaterials").all { it.contains("chapter=reading") })
    }
}
