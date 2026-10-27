package com.engquest.teacher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.engquest.teacher.ui.TeacherApp
import com.engquest.teacher.ui.submissions.SubmissionLink
import com.engquest.teacher.ui.theme.Brand
import com.engquest.teacher.ui.theme.EngQuestTheme

class MainActivity : ComponentActivity() {
    /** A notification's link not yet opened (it waits here through the login screen). */
    private var link by mutableStateOf<SubmissionLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // White status-bar icons on the brand top bar.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Brand.toArgb()))
        super.onCreate(savedInstanceState)
        // Only a fresh start: after a rotation the link was already opened.
        if (savedInstanceState == null) link = linkFrom(intent)
        val graph = (application as GraphOwner).graph
        setContent {
            EngQuestTheme {
                TeacherApp(graph, link, onLinkUsed = { link = null })
            }
        }
    }

    /** A notification tapped while the app is already open. */
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkFrom(intent)?.let { link = it }
    }

    companion object {
        const val EXTRA_CLASS = "className"
        const val EXTRA_STUDENT = "studentNo"

        /** The class and student a notification points to, or null for an ordinary start. */
        fun linkFrom(intent: Intent?): SubmissionLink? {
            val className = intent?.getStringExtra(EXTRA_CLASS)?.trim().orEmpty()
            val studentNo = intent?.getStringExtra(EXTRA_STUDENT)?.trim().orEmpty()
            if (className.isEmpty() || studentNo.isEmpty()) return null
            return SubmissionLink(className, studentNo)
        }
    }
}
