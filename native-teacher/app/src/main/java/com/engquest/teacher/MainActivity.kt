package com.engquest.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.engquest.teacher.ui.TeacherApp
import com.engquest.teacher.ui.theme.Brand
import com.engquest.teacher.ui.theme.EngQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // White status-bar icons on the brand top bar.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Brand.toArgb()))
        super.onCreate(savedInstanceState)
        val graph = (application as GraphOwner).graph
        setContent {
            EngQuestTheme {
                TeacherApp(graph)
            }
        }
    }
}
