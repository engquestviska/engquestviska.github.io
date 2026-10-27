package com.engquest.teacher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The activity starts from the app's graph and, signed out, shows the login screen.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTeacherApp::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensWithTheAppName() {
        compose.onNodeWithText("EngQuest Teacher").assertIsDisplayed()
    }
}
