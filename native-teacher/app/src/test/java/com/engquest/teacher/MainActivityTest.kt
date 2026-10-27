package com.engquest.teacher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Proves Compose UI tests run on the JVM under Robolectric (later screens rely on it).
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensWithTheAppName() {
        compose.onNodeWithText("EngQuest Teacher").assertIsDisplayed()
    }
}
