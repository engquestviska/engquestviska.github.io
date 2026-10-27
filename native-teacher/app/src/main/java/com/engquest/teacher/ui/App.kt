package com.engquest.teacher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.ui.attendance.AttendanceScreen
import com.engquest.teacher.ui.xp.XpScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.engquest.teacher.ui.theme.Brand
import kotlinx.coroutines.launch

private val PageBackground = Color(0xFFF4F5FB)
private val TabOff = Color(0xFF94A3B8)

/** The four bottom-bar tabs; [key] is what the `tab` pref stores. */
enum class Tab(val key: String, val title: String, val label: String, val icon: ImageVector) {
    ATTENDANCE("attendance", "Attendance", "Attendance", AppIcons.Attendance),
    SUBMISSIONS("submissions", "Submissions", "Submissions", AppIcons.Submissions),
    XP("xp", "Give XP", "XP", AppIcons.Xp),
    SCORES("scores", "Scores", "Scores", AppIcons.Scores),
    ;

    companion object {
        fun fromKey(key: String?): Tab = entries.firstOrNull { it.key == key } ?: ATTENDANCE
    }
}

private enum class ShellSheet { CLASS, FAILED }

/**
 * Lets a tab's screen ask the shell to check before the teacher leaves it (another tab,
 * another class, logging out). The screen sets [unsaved] to a plain sentence while it holds
 * changes that would be lost, and back to null when it holds none.
 */
@Stable
class LeaveGuard {
    var unsaved by mutableStateOf<String?>(null)
}

/**
 * The whole app: the login screen while nobody is logged in (or the saved password stopped
 * working), otherwise the top bar, status line, the selected tab's screen and the bottom bar.
 */
@Composable
fun TeacherApp(graph: AppGraph) {
    val user by graph.session.user.collectAsState()
    val expired by graph.session.expired.collectAsState()
    var loggedOutHere by rememberSaveable { mutableStateOf(false) }

    if (user == null) {
        val note = when {
            expired -> "Please log in again — your unsent changes are kept."
            loggedOutHere -> "You are logged out."
            else -> "Log in once. The app remembers you on this phone."
        }
        key(expired) { LoginScreen(graph.session, note, graph.session.savedUsername().orEmpty()) }
    } else {
        AppShell(graph, onLoggedOut = { loggedOutHere = true })
    }
}

@Composable
private fun AppShell(graph: AppGraph, onLoggedOut: () -> Unit) {
    val prefs = graph.prefs
    var classId by rememberSaveable {
        mutableStateOf(
            prefs.getString(AppGraph.KEY_CLASS, null)?.takeIf { id -> Classes.ALL.any { it.id == id } }
                ?: Classes.ALL.first().id,
        )
    }
    var tab by rememberSaveable { mutableStateOf(Tab.fromKey(prefs.getString(AppGraph.KEY_TAB, null))) }
    val pending by graph.outbox.pending.collectAsState(initial = 0)
    val failed by graph.outbox.failed.collectAsState(initial = emptyList())
    val online by graph.online.collectAsState()
    var sheet by remember { mutableStateOf<ShellSheet?>(null) }
    var askLogout by remember { mutableStateOf(false) }
    val guard = remember { LeaveGuard() }
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    /** Runs [go] now, or after the teacher agrees to leave unsaved changes behind. */
    fun leaveThen(go: () -> Unit) {
        if (guard.unsaved == null) go() else leaving = go
    }
    val scope = rememberCoroutineScope()
    val classInfo = Classes.ALL.first { it.id == classId }

    // Android Back with unsaved work asks first; with nothing unsaved it behaves as usual.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var backConfirmed by remember { mutableStateOf(false) }
    BackHandler(enabled = guard.unsaved != null && !backConfirmed) {
        leaving = { backConfirmed = true }
    }
    LaunchedEffect(backConfirmed) {
        if (backConfirmed) backDispatcher?.onBackPressed()
    }

    // Everything retried or discarded: nothing left to show.
    LaunchedEffect(failed.isEmpty()) {
        if (failed.isEmpty() && sheet == ShellSheet.FAILED) sheet = null
    }

    Box(Modifier.fillMaxSize().background(PageBackground)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = tab.title,
                classLabel = classInfo.label,
                onClassClick = { sheet = ShellSheet.CLASS },
                onLogOutClick = { leaveThen { askLogout = true } },
            )
            StatusBar(pending, failed.size, online, onOpenFailed = { sheet = ShellSheet.FAILED })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TabContent(tab, graph, classInfo, guard)
            }
            TabBar(tab) { picked ->
                if (picked != tab) {
                    leaveThen {
                        tab = picked
                        prefs.edit().putString(AppGraph.KEY_TAB, picked.key).apply()
                    }
                }
            }
        }
        when (sheet) {
            ShellSheet.CLASS -> ClassPicker(
                selectedId = classId,
                onPick = { picked ->
                    if (picked.id == classId) {
                        sheet = null
                    } else {
                        leaveThen {
                            classId = picked.id
                            prefs.edit().putString(AppGraph.KEY_CLASS, picked.id).apply()
                            sheet = null
                        }
                    }
                },
                onDismiss = { sheet = null },
            )
            ShellSheet.FAILED -> FailedSheet(
                items = failed,
                onRetry = { graph.outbox.retry(it.id) },
                onDiscard = { graph.outbox.discard(it.id) },
                onDismiss = { sheet = null },
            )
            null -> Unit
        }
    }

    leaving?.let { go ->
        ConfirmDialog(
            title = "Leave without saving?",
            text = guard.unsaved.orEmpty(),
            confirmLabel = "Leave",
            confirmTag = "confirmLeave",
            onConfirm = {
                leaving = null
                guard.unsaved = null
                go()
            },
            onDismiss = { leaving = null },
        )
    }

    if (askLogout) {
        val unsent = pending + failed.size
        AlertDialog(
            onDismissRequest = { askLogout = false },
            title = { Text("Log out?") },
            text = {
                Text(
                    if (unsent > 0) {
                        val changes = if (unsent == 1) "1 change that is not sent yet" else "$unsent changes that are not sent yet"
                        "You have $changes. They will be lost if you log out now."
                    } else {
                        "You'll need your username and password to log in again."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askLogout = false
                        sheet = null
                        scope.launch {
                            graph.logOut()
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.testTag("confirmLogout"),
                ) { Text("Log out", color = Bad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { askLogout = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The selected tab's screen. Each tab is one slot taking the graph and the selected class;
 * Tasks 10–12 replace the placeholders. [guard] is how a screen holds back leaving it.
 */
@Composable
fun TabContent(tab: Tab, graph: AppGraph, classInfo: ClassInfo, guard: LeaveGuard) {
    when (tab) {
        Tab.ATTENDANCE -> AttendanceScreen(graph, classInfo, guard)
        Tab.SUBMISSIONS -> SubmissionsTab(graph, classInfo)
        Tab.XP -> XpScreen(graph, classInfo)
        Tab.SCORES -> ScoresTab(graph, classInfo)
    }
}

// ---- Placeholders until Tasks 9–12 ---------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
@Composable
fun SubmissionsTab(graph: AppGraph, classInfo: ClassInfo) = ComingNext("Submissions", classInfo)

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScoresTab(graph: AppGraph, classInfo: ClassInfo) = ComingNext("Scores", classInfo)

@Composable
private fun ComingNext(screen: String, classInfo: ClassInfo) {
    Column(
        Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Coming next", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
        Text(
            "$screen for ${classInfo.label} arrives in the next update.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---- Frame ---------------------------------------------------------------------------------

@Composable
private fun TopBar(title: String, classLabel: String, onClassClick: () -> Unit, onLogOutClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brand)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("screenTitle"),
        )
        ClassChip(classLabel, onClassClick)
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(AppIcons.MoreVertical, contentDescription = "More options", tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Log out") },
                    onClick = {
                        menuOpen = false
                        onLogOutClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().height(64.dp).testTag("tabBar")) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                val color = if (on) Brand else TabOff
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                        .drawBehind {
                            if (on) {
                                val w = size.width * 0.4f
                                drawRoundRect(
                                    color = Brand,
                                    topLeft = Offset((size.width - w) / 2, 0f),
                                    size = Size(w, 3.dp.toPx()),
                                    cornerRadius = CornerRadius(3.dp.toPx()),
                                )
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
