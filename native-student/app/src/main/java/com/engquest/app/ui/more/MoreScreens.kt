package com.engquest.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engquest.app.AppGraph
import com.engquest.app.data.Fresh
import com.engquest.app.domain.Activeness
import com.engquest.app.domain.Profile
import com.engquest.app.domain.levelFromXp
import com.engquest.app.domain.levelTitle
import com.engquest.app.ui.ROUTE_TITLES
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.IconDot
import com.engquest.app.ui.common.Loading
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.Pill
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.home.OfflineNote
import com.engquest.app.ui.home.prettyDate
import com.engquest.app.ui.home.statusColors
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The More tab: every other page, grouped. */
@Composable
fun MoreScreen(profile: Profile, onOpen: (String) -> Unit, onLogOut: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "More", subtitle = "${profile.name} · ${profile.classId} · No. ${profile.studentNo}")
        SectionTitle("My progress")
        Group {
            Item("⭐", "Level / XP", Tint.amber) { onOpen("levelxp") }
            Item("📊", "My Statistics", Tint.blue) { onOpen("stats") }
            Item("📋", "Task Status", Tint.green) { onOpen("taskstatus") }
            Item("📅", "Attendance", Tint.violet) { onOpen("attendance") }
            Item("⚠️", "My Strikes", Tint.red) { onOpen("strikes") }
        }
        SectionTitle("My class")
        Group { Item("👥", "Student List", Tint.slate) { onOpen("students") } }
        SectionTitle("App")
        Group {
            Item("⚙️", "Settings", Tint.slate) { onOpen("settings") }
            Item("🚪", "Log out", Tint.slate) { confirm = true }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll pick your class and name again next time.") },
            confirmButton = { TextButton(onClick = { confirm = false; onLogOut() }) { Text("Yes, log out") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    EqCard(Modifier.padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun Item(emoji: String, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDot(emoji, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = LocalEq.current.muted)
    }
}

/** One page opened from More (or Home / the bell). */
@Composable
fun MorePage(graph: AppGraph, profile: Profile, route: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = ROUTE_TITLES[route] ?: route, onBack = onBack)
        when (route) {
            "levelxp" -> LevelXp(graph, profile)
            "attendance" -> AttendancePage(graph, profile)
            "strikes" -> StrikesPage(graph, profile)
            "taskstatus" -> TaskStatusPage(graph, profile)
            "students" -> StudentsPage(graph, profile)
            "stats" -> StatsPage(graph, profile)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Loads one thing for a page; shows a spinner, an error with "Try again", or [content]. */
@Composable
private fun <T> Loaded(load: suspend () -> Fresh<T>, content: @Composable (T) -> Unit) {
    var value by remember { mutableStateOf<Fresh<T>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { value = run { load() } } catch (e: Exception) { error = (e as? com.engquest.app.data.ApiError)?.userMessage ?: "Please try again." }
    }
    val v = value
    when {
        v != null -> { if (v.fromCache) OfflineNote(v.savedAt); content(v.value) }
        error != null -> Message("Couldn't load this. $error", onRetry = { attempt++ })
        else -> Loading()
    }
}

private val XP_ROWS = listOf(
    Triple("Vocabulary Box", 2) { a: Activeness -> a.vocabularyBox },
    Triple("Answering", 5) { a: Activeness -> a.answering },
    Triple("Presenting", 20) { a: Activeness -> a.presenting },
    Triple("Doing Task", 20) { a: Activeness -> a.doingTask },
    Triple("Helping Hand", 20) { a: Activeness -> a.helpingHand },
    Triple("Quiz", 10) { a: Activeness -> a.quiz },
)

@Composable
private fun LevelXp(graph: AppGraph, p: Profile) = Loaded({ graph.repo.activeness(p.classId) }) { list ->
    val me = list.firstOrNull { it.no == p.studentNo }
    if (me == null) { Message("No XP yet."); return@Loaded }
    val lv = levelFromXp(me.total)
    val eq = LocalEq.current
    EqCard(Modifier.padding(14.dp)) {
        Text("Level ${lv.level} · ${levelTitle(lv.level)}", style = MaterialTheme.typography.titleLarge)
        Text("${me.total} XP · ${lv.toNext} XP to level ${lv.level + 1}", style = MaterialTheme.typography.bodySmall, color = eq.muted)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(eq.line)) {
            Box(Modifier.fillMaxWidth(lv.progress.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(99.dp)).background(eq.act1))
        }
        if (me.indicator.isNotBlank()) Pill(me.indicator, Tint.amber, Tint.amberInk, Modifier.padding(top = 10.dp))
    }
    SectionTitle("Where your XP comes from")
    EqCard(Modifier.padding(horizontal = 14.dp)) {
        XP_ROWS.forEach { (label, pts, get) ->
            val n = get(me)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("$n × $pts XP", style = MaterialTheme.typography.bodySmall, color = eq.muted)
                }
                Text("${n * pts} XP", style = MaterialTheme.typography.titleMedium, color = eq.accent)
            }
        }
        me.customAwards.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(a.reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${a.points} XP", style = MaterialTheme.typography.titleMedium, color = eq.accent)
            }
        }
    }
}

@Composable
private fun AttendancePage(graph: AppGraph, p: Profile) = Loaded({ graph.repo.classAttendance(p) }) { ca ->
    val mine = ca.byStudent[p.studentNo].orEmpty()
    val rows = ca.meetings.sortedByDescending { it.number }.map { m -> m to mine["m${m.number}"].orEmpty() }.filter { it.second.isNotBlank() }
    if (rows.isEmpty()) { Message("No attendance yet."); return@Loaded }
    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Present", "Absent", "Sick", "Permit").forEach { s ->
            val (bg, fg) = statusColors(s)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(bg).padding(vertical = 10.dp).testTag("count-$s"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${rows.count { it.second == s }}", style = MaterialTheme.typography.titleLarge, color = fg)
                Text(s, style = MaterialTheme.typography.labelSmall, color = fg)
            }
        }
    }
    EqCard(Modifier.padding(horizontal = 14.dp)) {
        rows.forEach { (m, s) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Meeting ${m.number}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                Text(prettyDate(m.date), style = MaterialTheme.typography.bodySmall, color = LocalEq.current.muted, modifier = Modifier.weight(1f))
                val (bg, fg) = statusColors(s)
                Pill(s, bg, fg)
            }
        }
    }
}

@Composable
private fun StrikesPage(graph: AppGraph, p: Profile) = Loaded({ graph.repo.strikeCount(p) }) { n ->
    EqCard(Modifier.padding(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (n == 0) "🎉" else "⚠️", style = MaterialTheme.typography.headlineMedium)
            Text(if (n == 0) "No strikes" else "$n strike${if (n == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge, color = if (n == 0) Tint.greenInk else Tint.redInk)
            Text(if (n == 0) "Keep it up!" else "The reasons appear in What's new when your teacher gives a strike.", style = MaterialTheme.typography.bodySmall, color = LocalEq.current.muted)
        }
    }
}

@Composable
private fun TaskStatusPage(graph: AppGraph, p: Profile) = Loaded({ graph.repo.taskStatus(p) }) { tasks ->
    val done = tasks.count { it.value }
    Text("$done of ${tasks.size} tasks done", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(18.dp))
    tasks.keys.groupBy { Regex("^C(\\d+)").find(it)?.groupValues?.get(1) ?: "?" }.toSortedMap().forEach { (ch, keys) ->
        SectionTitle("Chapter $ch")
        EqCard(Modifier.padding(horizontal = 14.dp)) {
            keys.sorted().forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k.replace(Regex("^C\\d+T"), "Task "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (tasks[k] == true) Pill("Done", Tint.green, Tint.greenInk) else Pill("Not yet", Tint.slate, Tint.slateInk)
                }
            }
        }
    }
}

@Composable
private fun StudentsPage(graph: AppGraph, p: Profile) = Loaded({ graph.repo.activeness(p.classId) }) { list ->
    EqCard(Modifier.padding(14.dp)) {
        list.sortedBy { it.no }.forEach { s ->
            val me = s.no == p.studentNo
            val lv = levelFromXp(s.total)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (me) LocalEq.current.accentSoft else Color.Transparent).padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${s.no}", style = MaterialTheme.typography.labelLarge, color = LocalEq.current.accent, modifier = Modifier.width(30.dp))
                Text(s.name + if (me) " (you)" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Lv ${lv.level}", style = MaterialTheme.typography.labelLarge, color = LocalEq.current.muted)
            }
        }
    }
}

@Composable
private fun StatsPage(graph: AppGraph, p: Profile) {
    var stats by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(p) {
        stats = run {
            val xp = runCatching { graph.repo.activeness(p.classId).value.firstOrNull { it.no == p.studentNo }?.total }.getOrNull()
            val fin = runCatching { graph.repo.finalScores(p.classId).value.rows.firstOrNull { it.no == p.studentNo }?.calc?.get("Final Score") }.getOrNull()
            val att = runCatching { graph.repo.classAttendance(p).value.byStudent[p.studentNo].orEmpty().values.filter { it.isNotBlank() } }.getOrNull()
            val tasks = runCatching { graph.repo.taskStatus(p).value }.getOrNull()
            val strikes = runCatching { graph.repo.strikeCount(p).value }.getOrNull()
            listOf(
                "Level" to (xp?.let { "Level ${levelFromXp(it).level} · $it XP" } ?: "-"),
                "Final score" to (fin?.ifBlank { null } ?: "-"),
                "Attendance" to (att?.takeIf { it.isNotEmpty() }?.let { a -> "${a.count { it == "Present" } * 100 / a.size}% present (${a.size} meetings)" } ?: "-"),
                "Tasks done" to (tasks?.let { t -> "${t.count { it.value }} of ${t.size}" } ?: "-"),
                "Strikes" to (strikes?.toString() ?: "-"),
            )
        }
    }
    val s = stats
    if (s == null) { Loading(); return }
    EqCard(Modifier.padding(14.dp)) {
        s.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalEq.current.muted, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
