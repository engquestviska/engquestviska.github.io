package com.engquest.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.engquest.app.AppGraph
import com.engquest.app.data.ApiError
import com.engquest.app.data.RosterResult
import com.engquest.app.data.checkRoster
import com.engquest.app.domain.Profile
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.home.HomeScreen
import com.engquest.app.ui.login.LoginScreen
import com.engquest.app.ui.learn.LearnScreen
import com.engquest.app.ui.learn.MaterialKind
import com.engquest.app.ui.learn.MaterialsScreen
import com.engquest.app.ui.scores.ScoresScreen
import com.engquest.app.ui.send.SendScreen
import com.engquest.app.ui.shell.BottomBar
import com.engquest.app.ui.theme.EqTheme
import com.engquest.app.ui.theme.LocalEq

/** The five bottom-bar tabs, in order. Every other route opens on top of one of them. */
val TABS = listOf("home", "scores", "send", "learn", "more")

/** Screen titles for routes reached from Learn / More / the bell. */
val ROUTE_TITLES = mapOf(
    "bell" to "What's new", "settings" to "Settings", "levelxp" to "Level / XP", "stats" to "My Statistics",
    "taskstatus" to "Task Status", "attendance" to "Attendance", "strikes" to "My Strikes", "students" to "Student List",
    "materials" to "Materials", "assignments" to "Assignments", "reading" to "Reading", "vocab" to "My Vocabulary",
)

/** Login first; once a student is saved, checks the class list (renumbered or left) and shows the app. */
@Composable
fun StudentApp(graph: AppGraph) {
    var profile by remember { mutableStateOf(graph.profiles.get()) }
    var note by rememberSaveable { mutableStateOf<String?>(null) }
    val p = profile
    if (p == null) {
        LoginScreen(graph.repo, graph.legacyGrade(), note, onDone = {
            graph.profiles.save(it)
            note = null
            profile = it
        })
        return
    }
    LaunchedEffect(p.classId, p.studentNo, p.name) {
        val roster = try { graph.repo.classmates(p.classId).value } catch (e: ApiError) { return@LaunchedEffect }
        when (val r = checkRoster(p, roster)) {
            RosterResult.Same -> Unit
            is RosterResult.Moved -> p.copy(studentNo = r.newNo, name = r.name).also { graph.profiles.save(it); profile = it }
            RosterResult.Gone -> {
                graph.profiles.clear()
                note = "We couldn't find your name in ${p.classId} any more. Please pick it again."
                profile = null
            }
        }
    }
    EqTheme(p.grade) {
        Shell(graph, p, onLogOut = {
            graph.profiles.clear()
            profile = null
        })
    }
}

/**
 * The tabs and a simple back stack. Back from an opened page returns to its tab; back from a tab
 * other than Home goes Home; on Home, a second back within 2 seconds closes the app.
 */
@Composable
fun Shell(graph: AppGraph, profile: Profile, onLogOut: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current

    fun open(route: String) {
        if (route in TABS) { tab = route; page = null } else page = route
    }

    BackHandler {
        when {
            page != null -> page = null
            tab != "home" -> tab = "home"
            System.currentTimeMillis() - lastBackAt < 2000 -> (context as? android.app.Activity)?.finish()
            else -> {
                lastBackAt = System.currentTimeMillis()
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(LocalEq.current.bg)) {
        Box(Modifier.weight(1f)) {
            val current = page ?: tab
            Box(Modifier.fillMaxSize().testTag("screen-$current")) {
                when (current) {
                    "home" -> HomeScreen(graph, profile, onOpen = ::open)
                    "send" -> SendScreen(graph, profile)
                    "scores" -> ScoresScreen(graph, profile)
                    "learn" -> LearnScreen(onOpen = ::open)
                    "materials", "assignments", "reading" -> MaterialsScreen(
                        graph, profile,
                        when (current) { "materials" -> MaterialKind.MATERIALS; "assignments" -> MaterialKind.ASSIGNMENTS; else -> MaterialKind.READING },
                        onBack = { page = null },
                        openLink = { link ->
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
                                .onFailure { Toast.makeText(context, "No app on this phone can open this link.", Toast.LENGTH_SHORT).show() }
                        },
                    )
                    else -> Placeholder(current, onBack = if (page != null) ({ page = null }) else null)
                }
            }
        }
        if (page == null) BottomBar(tab, onTab = { tab = it; page = null }, modifier = Modifier.navigationBarsPadding())
    }
}

/** Screens not built yet (internal builds only; 2.0 ships with every screen built). */
@Composable
private fun Placeholder(route: String, onBack: (() -> Unit)?) {
    val title = when (route) {
        "scores" -> "Scores"; "send" -> "Send a task"; "learn" -> "Learn"; "more" -> "More"
        else -> ROUTE_TITLES[route] ?: route
    }
    Column(Modifier.fillMaxSize()) {
        QuestHeader(title = title, onBack = onBack)
        Message("This screen is being built.")
    }
}
