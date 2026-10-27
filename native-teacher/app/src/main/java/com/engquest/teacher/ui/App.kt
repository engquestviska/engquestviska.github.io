package com.engquest.teacher.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.ui.attendance.AttendanceScreen
import com.engquest.teacher.ui.scores.ScoresScreen
import com.engquest.teacher.ui.submissions.SubmissionLink
import com.engquest.teacher.ui.submissions.SubmissionsScreen
import com.engquest.teacher.ui.submissions.SubmissionsViewModel
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
fun TeacherApp(graph: AppGraph, link: SubmissionLink? = null, onLinkUsed: () -> Unit = {}) {
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
        AppShell(graph, link, onLinkUsed, onLoggedOut = { loggedOutHere = true })
    }
}

@Composable
private fun AppShell(graph: AppGraph, link: SubmissionLink?, onLinkUsed: () -> Unit, onLoggedOut: () -> Unit) {
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

    // Submissions load here, not only on their tab, so the tab badge shows what is waiting.
    val submissions = remember(classId) { SubmissionsViewModel(graph, classInfo, scope) }
    DisposableEffect(submissions) {
        submissions.open()
        onDispose { submissions.close() }
    }

    // A notification's link: that class, the Submissions tab, that student.
    var linkedStudent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        onLinkUsed()
        val target = Classes.ALL.firstOrNull { it.id == l.className } ?: return@LaunchedEffect
        leaveThen {
            sheet = null
            classId = target.id
            prefs.edit().putString(AppGraph.KEY_CLASS, target.id).apply()
            tab = Tab.SUBMISSIONS
            prefs.edit().putString(AppGraph.KEY_TAB, Tab.SUBMISSIONS.key).apply()
            linkedStudent = l.studentNo
        }
    }
    LaunchedEffect(submissions, linkedStudent) {
        linkedStudent?.let {
            submissions.focus(it)
            linkedStudent = null
        }
    }

    // Notifications: register this phone after login, and ask once to allow them.
    graph.push?.let { push ->
        LaunchedEffect(push) {
            push.current { token -> graph.scope.launch { graph.registerDevice(token) } }
        }
        NotificationQuestion(prefs)
    }

    // Android Back with unsaved work asks first; with nothing unsaved it behaves as usual.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var backConfirmed by remember { mutableStateOf(false) }
    var keptNote by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = guard.unsaved != null && !backConfirmed) {
        val note = guard.unsaved
        leaving = {
            keptNote = note
            backConfirmed = true
        }
    }
    LaunchedEffect(backConfirmed) {
        if (backConfirmed) {
            backDispatcher?.onBackPressed()
            // Back may only send the app to the background: the work is still unsaved, so ask again next time.
            guard.unsaved = keptNote
            backConfirmed = false
        }
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
            graph.updates?.let { checker ->
                val context = LocalContext.current
                UpdateBanner(checker) { url, onFailed -> UpdateInstaller(context).start(url, onFailed) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TabContent(tab, graph, classInfo, guard, submissions)
            }
            TabBar(tab, badges = mapOf(Tab.SUBMISSIONS to submissions.waiting)) { picked ->
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
 * Asks once (Android 13+, where notifications need the teacher's OK) whether to allow them.
 * Either answer is remembered; "Yes" then shows the phone's own permission question.
 */
@Composable
private fun NotificationQuestion(prefs: SharedPreferences) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var ask by remember {
        mutableStateOf(
            !prefs.getBoolean(AppGraph.KEY_ASKED_NOTIFICATIONS, false) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun answer(yes: Boolean) {
        ask = false
        prefs.edit().putBoolean(AppGraph.KEY_ASKED_NOTIFICATIONS, true).apply()
        if (yes) request.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { answer(false) },
            text = { Text("Get a notification when a student sends a submission?") },
            confirmButton = { TextButton(onClick = { answer(true) }) { Text("Yes", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { answer(false) }) { Text("Not now") } },
        )
    }
}

/**
 * The selected tab's screen. Each tab is one slot taking the graph and the selected class;
 * [guard] is how a screen holds back leaving it. [submissions] is made by the shell (it feeds
 * the tab badge too).
 */
@Composable
fun TabContent(tab: Tab, graph: AppGraph, classInfo: ClassInfo, guard: LeaveGuard, submissions: SubmissionsViewModel) {
    when (tab) {
        Tab.ATTENDANCE -> AttendanceScreen(graph, classInfo, guard)
        Tab.SUBMISSIONS -> SubmissionsScreen(submissions, graph.photos, classInfo, guard)
        Tab.XP -> XpScreen(graph, classInfo)
        Tab.SCORES -> ScoresScreen(graph, classInfo)
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
private fun TabBar(selected: Tab, badges: Map<Tab, Int>, onSelect: (Tab) -> Unit) {
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
                    Box {
                        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        val count = badges[tab] ?: 0
                        if (count > 0) {
                            Text(
                                if (count > 99) "99+" else count.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = 14.dp, y = (-6).dp)
                                    .background(Bad, RoundedCornerShape(999.dp))
                                    .widthIn(min = 18.dp)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                    .semantics { contentDescription = "$count waiting" }
                                    .testTag("tabBadge-${tab.key}"),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
