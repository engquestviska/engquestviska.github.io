package com.engquest.teacher.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinResetCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickingAClassAndNumberResetsThatPin() {
        val calls = mutableListOf<Pair<String, String>>()
        compose.setContent { PinResetCard(onReset = { c, n -> calls += c to n; null }) }
        compose.onNodeWithTag("pinResetOpen").performClick()
        compose.onNodeWithTag("pinClass-XE2").performClick()
        compose.onNodeWithTag("pinNumber").performTextInput("7")
        compose.onNodeWithTag("pinResetGo").performClick()
        compose.waitUntil(3000) { calls.isNotEmpty() }
        assertEquals(listOf("XE2" to "7"), calls)
        compose.onNodeWithText("PIN reset for X E-2 no. 7. They can make a new one now.").assertIsDisplayed()
    }

    @Test fun aProblemIsShownInTheSheet() {
        compose.setContent { PinResetCard(onReset = { _, _ -> "No student with that number in this class." }) }
        compose.onNodeWithTag("pinResetOpen").performClick()
        compose.onNodeWithTag("pinClass-XE1").performClick()
        compose.onNodeWithTag("pinNumber").performTextInput("99")
        compose.onNodeWithTag("pinResetGo").performClick()
        compose.waitUntil(3000) { compose.onAllNodes(androidx.compose.ui.test.hasText("No student with that number in this class.")).fetchSemanticsNodes().isNotEmpty() }
    }
}
