package com.engquest.app.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engquest.app.AppGraph
import com.engquest.app.data.ApiError
import com.engquest.app.data.Fresh
import com.engquest.app.domain.Event
import com.engquest.app.domain.Profile
import com.engquest.app.domain.levelFromXp
import com.engquest.app.domain.levelTitle
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.IconDot
import com.engquest.app.ui.common.Pill
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** What Home shows. Each part is null until loaded (or when that part could not be loaded). */
data class HomeData(
    val totalXp: Int? = null,
    val finalScore: String? = null,
    val tasksDone: Int? = null,
    val tasksTotal: Int? = null,
    val strikes: Int? = null,
    val events: List<Event> = emptyList(),
    val attendance: List<Pair<String, String>> = emptyList(),
    /** Set when some of it came from the phone's saved copy (no signal). */
    val offlineSince: Long? = null,
    val loaded: Boolean = false,
)

private suspend fun <T> soft(block: suspend () -> Fresh<T>): Fresh<T>? = try { block() } catch (e: ApiError) { null }

suspend fun loadHome(graph: AppGraph, p: Profile): HomeData = coroutineScope {
    val repo = graph.repo
    val act = async { soft { repo.activeness(p.classId) } }
    val fin = async { soft { repo.finalScores(p.classId) } }
    val tasks = async { soft { repo.taskStatus(p) } }
    val strikes = async { soft { repo.strikeCount(p) } }
    val events = async { soft { repo.myEvents(p, 0) } }
    val att = async { soft { repo.classAttendance(p) } }
    val all = listOf(act, fin, tasks, strikes, events, att).map { it.await() }
    val a = act.await()?.value?.firstOrNull { it.no == p.studentNo }
    val f = fin.await()?.value?.rows?.firstOrNull { it.no == p.studentNo }
    val t = tasks.await()?.value
    val ca = att.await()?.value
    val mine = ca?.byStudent?.get(p.studentNo).orEmpty()
    val recent = ca?.meetings.orEmpty().sortedBy { it.number }
        .mapNotNull { m -> mine["m${m.number}"]?.takeIf { it.isNotBlank() }?.let { m.date to it } }
        .takeLast(3).reversed()
    HomeData(
        totalXp = a?.total,
        finalScore = f?.calc?.get("Final Score")?.takeIf { it.isNotBlank() },
        tasksDone = t?.count { it.value },
        tasksTotal = t?.size,
        strikes = strikes.await()?.value,
        events = events.await()?.value.orEmpty().take(3),
        attendance = recent,
        offlineSince = all.filterNotNull().filter { it.fromCache }.minOfOrNull { it.savedAt },
        loaded = true,
    )
}

fun greeting(now: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))): String =
    when (now.get(Calendar.HOUR_OF_DAY)) {
        in 4..10 -> "Good morning 👋"
        in 11..14 -> "Good afternoon 👋"
        in 15..17 -> "Good afternoon 👋"
        else -> "Good evening 👋"
    }

@Composable
fun HomeScreen(graph: AppGraph, profile: Profile, onOpen: (String) -> Unit) {
    var data by remember(profile) { mutableStateOf(HomeData()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(profile, refresh) { data = run { loadHome(graph, profile) } }
    val eq = LocalEq.current
    val lv = levelFromXp(data.totalXp ?: 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(
            eyebrow = greeting(), title = profile.name, extraBottom = 56.dp,
            trailing = { Bell(unread = data.events.isNotEmpty() && graph.bellUnread(data.events.first().id), onClick = { onOpen("bell") }) },
            below = {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${profile.classId} · No. ${profile.studentNo}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            },
        )
        // Level card, overlapping the header band.
        Row(
            Modifier.offset(y = (-56).dp).padding(horizontal = 14.dp).fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White)
                .clickable { onOpen("levelxp") }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelRing(lv.level, lv.progress)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(levelTitle(lv.level), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (data.totalXp == null) (if (data.loaded) "XP not available" else "Loading…")
                    else "${data.totalXp} XP · ${lv.toNext} XP to level ${lv.level + 1}",
                    style = MaterialTheme.typography.bodySmall, color = eq.muted,
                )
            }
        }
        Column(Modifier.offset(y = (-42).dp)) {
            if (data.offlineSince != null) OfflineNote(data.offlineSince!!)
            graph.updates?.let { checker ->
                val ctx = androidx.compose.ui.platform.LocalContext.current
                com.engquest.app.ui.UpdateBanner(checker) { url, onFailed -> com.engquest.app.ui.UpdateInstaller(ctx).start(url, onFailed) }
            }
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(data.finalScore ?: "–", "Score", Tint.blue, Tint.blueInk, Modifier.weight(1f)) { onOpen("scores") }
                Chip(if (data.tasksDone != null) "${data.tasksDone}/${data.tasksTotal}" else "–", "Tasks", Tint.green, Tint.greenInk, Modifier.weight(1f)) { onOpen("taskstatus") }
                Chip(data.strikes?.toString() ?: "–", "Strikes", Tint.red, Tint.redInk, Modifier.weight(1f)) { onOpen("strikes") }
            }
            SectionTitle("What's new", action = "See all", onAction = { onOpen("bell") })
            EqCard(Modifier.padding(horizontal = 14.dp)) {
                if (!data.loaded) Text("Loading…", color = eq.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                else if (data.events.isEmpty()) Text("Nothing new yet", color = eq.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                else data.events.forEach { EventRow(it) { onOpen(it.link.ifBlank { "bell" }) } }
            }
            if (data.attendance.isNotEmpty()) {
                SectionTitle("Recent attendance", action = "See all", onAction = { onOpen("attendance") })
                EqCard(Modifier.padding(horizontal = 14.dp)) {
                    data.attendance.forEach { (date, status) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(prettyDate(date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            val (bg, fg) = statusColors(status)
                            Pill(status, bg, fg)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Bell(unread: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
            .semantics { contentDescription = if (unread) "What's new, unread items" else "What's new" }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text("🔔", style = MaterialTheme.typography.titleMedium)
        if (unread) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
    }
}

@Composable
private fun LevelRing(level: Int, progress: Float) {
    val eq = LocalEq.current
    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(74.dp)) {
            val w = 8.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(eq.line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(eq.act1, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LVL", style = MaterialTheme.typography.labelSmall, color = eq.muted)
            Text("$level", style = MaterialTheme.typography.headlineMedium, color = eq.accent)
        }
    }
}

@Composable
private fun Chip(value: String, label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = fg)
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun EventRow(e: Event, onClick: () -> Unit) {
    val (emoji, bg) = eventLook(e.kind)
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconDot(emoji, bg)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.detail.isNotBlank()) Text(e.detail, style = MaterialTheme.typography.bodySmall, color = LocalEq.current.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Icon and tint per event kind (same in the bell). */
fun eventLook(kind: String): Pair<String, Color> = when (kind) {
    "approved" -> "✅" to Tint.green
    "sent_back" -> "↩️" to Tint.amber
    "xp" -> "⭐" to Tint.amber
    "level" -> "🏆" to Tint.violet
    "attendance" -> "📅" to Tint.blue
    "strike" -> "⚠️" to Tint.red
    "material" -> "📘" to Tint.blue
    "announcement" -> "📣" to Tint.violet
    else -> "•" to Tint.slate
}

fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "Present" -> Tint.green to Tint.greenInk
    "Absent" -> Tint.red to Tint.redInk
    "Sick", "Permit" -> Tint.amber to Tint.amberInk
    else -> Tint.slate to Tint.slateInk
}

/** "2026-11-07" -> "Fri, 7 Nov". */
fun prettyDate(date: String): String = try {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date.take(10))!!
    SimpleDateFormat("EEE, d MMM", Locale.US).format(d)
} catch (e: Exception) { date }

@Composable
fun OfflineNote(since: Long) {
    val time = SimpleDateFormat("d MMM, HH:mm", Locale.US).format(since)
    Text(
        "Offline · showing what was saved $time", style = MaterialTheme.typography.labelMedium, color = Tint.amberInk,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(Tint.amber).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
