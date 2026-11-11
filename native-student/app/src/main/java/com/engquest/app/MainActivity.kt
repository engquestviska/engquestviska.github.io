package com.engquest.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.app.ui.ROUTE_TITLES
import com.engquest.app.ui.StudentApp
import com.engquest.app.ui.TABS

class MainActivity : ComponentActivity() {
    /** A notification's page not yet opened (it waits here through the login screen). */
    private var link by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // White status-bar icons over the coloured header band.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) link = linkFrom(intent)
        val graph = (application as GraphOwner).graph
        setContent { StudentApp(graph, link, onLinkUsed = { link = null }) }
    }

    /** A notification tapped while the app is already open. */
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkFrom(intent)?.let { link = it }
    }

    companion object {
        const val EXTRA_LINK = "link"

        /** The app page a notification points to, or null (only known pages are accepted). */
        fun linkFrom(intent: Intent?): String? {
            val l = intent?.getStringExtra(EXTRA_LINK)?.trim().orEmpty()
            return l.takeIf { it in TABS || it in ROUTE_TITLES }
        }
    }
}
