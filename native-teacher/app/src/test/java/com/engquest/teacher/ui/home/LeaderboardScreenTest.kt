package com.engquest.teacher.ui.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.engquest.teacher.data.Cached
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LeaderboardScreenTest {
    @get:Rule val compose = createComposeRule()
    private val asked = mutableListOf<String>()

    /** Each class has one student; their Final Score / XP follow the class number so the order is known. */
    private fun fake(action: String, cls: String) = flowOf(
        Cached(
            Json.parseToJsonElement(
                if (action == "getFinalScores") """{"students":[{"no":1,"name":"Top of $cls","calc":{"Final Score":${60 + cls.last().digitToInt()}}}]}"""
                else """{"students":[{"no":1,"name":"Top of $cls","Total":${cls.last().digitToInt() * 10}}]}""",
            ).jsonObject,
            savedAt = 1L, refreshing = false, error = null,
        ),
    ).also { asked += "$action|$cls" }

    private fun rows() = compose.onAllNodes(hasTestTag("rankRow")).fetchSemanticsNodes().size

    @Test fun wholeGradeXCombinesItsFiveClassesBestFirst() {
        compose.setContent { LeaderboardScreen(Board.SCORE, ::fake) }
        compose.waitUntil(5000) { asked.size == 5 && compose.onAllNodes(hasText("Top of XE5")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(setOf("XE1", "XE2", "XE3", "XE4", "XE5"), asked.map { it.substringAfter('|') }.toSet())
        compose.onAllNodes(hasText("Top of XE5")).fetchSemanticsNodes().single() // best score (65) is listed
    }

    @Test fun pickingOneClassShowsOnlyThatClass() {
        compose.setContent { LeaderboardScreen(Board.SCORE, ::fake) }
        compose.onNodeWithTag("group-XIF8").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5000) { rows() == 1 && compose.onAllNodes(hasText("Top of XIF8")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Top of XIF8")).fetchSemanticsNodes().single()
    }

    @Test fun levelShowsLevelAndXp() {
        compose.setContent { LeaderboardScreen(Board.LEVEL, ::fake) }
        compose.onNodeWithTag("group-G11").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5000) { rows() == 3 }
        compose.onAllNodes(hasText("Level 5 · 90 XP")).fetchSemanticsNodes().single() // XIF9: 90 XP
    }
}
