package com.engquest.app.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.engquest.app.AppGraph
import com.engquest.app.data.ApiError
import com.engquest.app.domain.ChapterScores
import com.engquest.app.domain.FinalScores
import com.engquest.app.domain.Profile
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.Loading
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.home.OfflineNote
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** The score book numbers the three chapters 4, 5, 6 (website: CHAPTERS = [4, 5, 6]). */
private val BOOK_CHAPTERS = listOf(4, 5, 6)

private data class ScoresData(
    val chapters: List<ChapterScores?> = emptyList(),
    val final: FinalScores? = null,
    val offlineSince: Long? = null,
    val error: String? = null,
    val loaded: Boolean = false,
)

private suspend fun loadScores(graph: AppGraph, p: Profile): ScoresData = coroutineScope {
    val chs = BOOK_CHAPTERS.map { ch -> async { runCatching { graph.repo.chapterScores(p.classId, ch) }.getOrNull() } }
    val fin = async { runCatching { graph.repo.finalScores(p.classId) }.getOrNull() }
    val got = chs.awaitAll()
    val f = fin.await()
    val all = got + f
    if (all.all { it == null }) {
        val err = runCatching { graph.repo.finalScores(p.classId) }.exceptionOrNull()
        return@coroutineScope ScoresData(error = (err as? ApiError)?.userMessage ?: "Please try again.", loaded = true)
    }
    ScoresData(
        chapters = got.map { it?.value },
        final = f?.value,
        offlineSince = all.filterNotNull().filter { it.fromCache }.minOfOrNull { it.savedAt },
        loaded = true,
    )
}

private fun one(d: Double?) = if (d == null) "-" else String.format(java.util.Locale.US, "%.1f", d)
private fun raw(d: Double?) = if (d == null) "-" else if (d == Math.floor(d)) d.toInt().toString() else d.toString()

/** "Safe" green, "Not Safe" red, anything else neutral. */
private fun gradeColors(ind: String): Pair<Color, Color> = when {
    ind.startsWith("Not", ignoreCase = true) -> Tint.red to Tint.redInk
    ind.startsWith("Safe", ignoreCase = true) -> Tint.green to Tint.greenInk
    else -> Tint.slate to Tint.slateInk
}

@Composable
fun ScoresScreen(graph: AppGraph, profile: Profile) {
    var data by remember(profile) { mutableStateOf(ScoresData()) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0..2 chapters, 3 final
    LaunchedEffect(profile, reload) { data = run { loadScores(graph, profile) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "Scores", subtitle = "${profile.classId} · No. ${profile.studentNo}")
        when {
            !data.loaded -> Loading()
            data.error != null -> Message("Couldn't load your scores. ${data.error}", onRetry = { reload++ })
            else -> {
                data.offlineSince?.let { OfflineNote(it) }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { i -> Tab("Chapter ${i + 1}", tab == i, Modifier.testTag("tab-ch${i + 1}")) { tab = i } }
                    Tab("Final Score", tab == 3, Modifier.testTag("tab-final")) { tab = 3 }
                }
                if (tab < 3) ChapterBlock(data.chapters.getOrNull(tab), profile.studentNo) else FinalBlock(data.final, profile.studentNo)
                Leaderboard(data.chapters, profile.studentNo)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Tab(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val eq = LocalEq.current
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else eq.ink,
        modifier = modifier.clip(RoundedCornerShape(99.dp)).background(if (on) eq.accent else Color.White)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(items: List<Pair<String, String>>, highlight: Boolean = false) {
    val eq = LocalEq.current
    FlowRow(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        items.forEach { (label, value) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (highlight) eq.accentSoft else Color.White)
                    .padding(vertical = 12.dp, horizontal = 8.dp).testTag("tile-$label"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = eq.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.titleLarge, color = if (value == "-") eq.muted else eq.ink)
            }
        }
    }
}

@Composable
private fun ResultRow(final: String, indicator: String) {
    val eq = LocalEq.current
    EqCard(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Final Score", style = MaterialTheme.typography.labelMedium, color = eq.muted)
                Text(final, style = MaterialTheme.typography.headlineMedium, color = eq.accent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Grade", style = MaterialTheme.typography.labelMedium, color = eq.muted)
                val (bg, fg) = gradeColors(indicator)
                Text(indicator.ifBlank { "-" }, style = MaterialTheme.typography.labelLarge, color = fg,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ChapterBlock(ch: ChapterScores?, no: Int) {
    val row = ch?.rows?.firstOrNull { it.no == no }
    if (ch == null || row == null) {
        Message("No scores yet for this chapter.")
        return
    }
    Tiles(ch.columns.map { it to raw(row.scores[it]) })
    ResultRow(one(row.finalScore), row.indicator)
}

@Composable
private fun FinalBlock(fin: FinalScores?, no: Int) {
    val row = fin?.rows?.firstOrNull { it.no == no }
    if (fin == null || row == null) {
        Message("No scores yet. Final scores haven't been entered.")
        return
    }
    val calc = fin.calcColumns.filterNot { it.contains("Indicator", true) || it.equals("Final Score", true) }
    Tiles(fin.inputColumns.map { it to raw(row.inputs[it]) } + calc.map { it to (row.calc[it]?.toDoubleOrNull()?.let(::one) ?: row.calc[it]?.ifBlank { "-" } ?: "-") }, highlight = false)
    ResultRow(row.calc["Final Score"]?.toDoubleOrNull()?.let(::one) ?: "-", row.calc["Indicator"].orEmpty())
}

/** Top 5 by chapter average, like the website's class leaderboard ("Overall" = mean of the chapters entered). */
@Composable
private fun Leaderboard(chapters: List<ChapterScores?>, myNo: Int) {
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 overall, 1..3 chapter
    val eq = LocalEq.current
    val names = mutableMapOf<Int, String>()
    val avgs = mutableMapOf<Int, MutableMap<Int, Double>>()
    chapters.forEachIndexed { i, ch ->
        ch?.rows?.forEach { r ->
            names[r.no] = r.name
            r.average?.let { avgs.getOrPut(r.no) { mutableMapOf() }[i + 1] = it }
        }
    }
    val ranked = avgs.mapNotNull { (no, m) ->
        val score = if (filter == 0) m.values.takeIf { it.isNotEmpty() }?.average() else m[filter]
        score?.let { Triple(no, names[no].orEmpty(), it) }
    }.sortedByDescending { it.third }

    SectionTitle("Class leaderboard")
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tab("Overall", filter == 0, Modifier) { filter = 0 }
        (1..3).forEach { c -> Tab("Ch $c", filter == c, Modifier) { filter = c } }
    }
    Spacer(Modifier.height(8.dp))
    EqCard(Modifier.padding(horizontal = 14.dp)) {
        if (ranked.isEmpty()) Text("No scores yet.", color = eq.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        ranked.take(5).forEachIndexed { i, (no, name, score) ->
            val me = no == myNo
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (me) eq.accentSoft else Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 8.dp).testTag("rank-row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val medal = listOf(Color(0xFFF59E0B), Color(0xFF94A3B8), Color(0xFFB45309)).getOrNull(i)
                Box(Modifier.size(28.dp).clip(CircleShape).background(medal ?: Tint.slate), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (medal != null) Color.White else Tint.slateInk)
                }
                Spacer(Modifier.width(10.dp))
                Text(name + if (me) " (you)" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(one(score), style = MaterialTheme.typography.titleMedium, color = eq.accent)
            }
        }
        val mine = ranked.indexOfFirst { it.first == myNo }
        if (mine >= 5) Text("You are #${mine + 1} of ${ranked.size}", style = MaterialTheme.typography.bodySmall, color = eq.muted, modifier = Modifier.padding(6.dp))
    }
}
