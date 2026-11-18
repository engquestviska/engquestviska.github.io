package com.engquest.teacher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.data.Cached
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.domain.Leaderboard
import com.engquest.teacher.domain.RankRow
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.theme.Brand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

enum class Board(val title: String, val action: String) { SCORE("Score", "getFinalScores"), LEVEL("Level", "getAllActiveness") }

private val GROUPS = listOf("G10" to "All Grade X", "G11" to "All Grade XI") + Classes.ALL.map { it.id to it.label }
private val Medals = listOf(Color(0xFFF59E0B), Color(0xFF94A3B8), Color(0xFFB45309))

/** Which classes a choice covers: a whole grade, or one class. */
internal fun classesFor(choice: String): List<String> = when (choice) {
    "G10" -> Classes.ALL.filter { it.grade == "10" }.map { it.id }
    "G11" -> Classes.ALL.filter { it.grade == "11" }.map { it.id }
    else -> listOf(choice)
}

/**
 * Score or Level ranking for a whole grade or one class, 1 to last. Each class is read on its own
 * (saved copy first, so it works offline); a grade is its classes put together.
 */
@Composable
fun LeaderboardScreen(start: Board, load: (action: String, className: String) -> Flow<Cached>) {
    var board by rememberSaveable { mutableStateOf(start) }
    var choice by rememberSaveable { mutableStateOf("G10") }
    var data by remember(board, choice) { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    var error by remember(board, choice) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val classes = classesFor(choice)

    LaunchedEffect(board, choice, attempt) {
        classes.forEach { cls ->
            launch {
                load(board.action, cls).collect { c ->
                    c.json?.let { data = data + (cls to it) }
                    if (c.json == null && c.error != null) error = c.error
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().testTag("leaderboard")) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(4.dp)) {
            Board.entries.forEach { b ->
                val on = b == board
                Text(
                    (if (b == Board.SCORE) "🏆 " else "⭐ ") + b.title,
                    color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).background(if (on) Brand else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { board = b }.padding(vertical = 10.dp).testTag("board-${b.name}"),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GROUPS.forEach { (id, label) ->
                val on = id == choice
                Text(
                    label, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.background(if (on) Brand else Color.White, RoundedCornerShape(99.dp))
                        .clickable { choice = id }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("group-$id"),
                )
            }
        }
        val rows = if (board == Board.SCORE) Leaderboard.scores(data) else Leaderboard.levels(data)
        when {
            data.isEmpty() && error != null -> Message("Couldn't load the leaderboard", error, "Try again" to { error = null; attempt++ })
            data.size < classes.size && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rows.isEmpty() -> Message(if (board == Board.SCORE) "No Final Scores yet" else "No XP yet", "They show here once there are some.", null)
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { "${it.classId}|${it.no}|${it.name}" }) { r -> RankLine(r, showClass = classes.size > 1) }
                item { Box(Modifier.padding(8.dp)) }
            }
        }
    }
}

@Composable
private fun RankLine(r: RankRow, showClass: Boolean) {
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp).testTag("rankRow"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val medal = Medals.getOrNull(r.rank - 1)
        Box(Modifier.size(32.dp).background(medal ?: Color(0xFFF1F5F9), CircleShape), contentAlignment = Alignment.Center) {
            Text("${r.rank}", color = if (medal != null) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(r.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showClass) Text(classLabel(r.classId), color = Muted, fontSize = 12.sp)
        }
        Text(r.value, color = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

/** Home's Leaderboard card: just two buttons, so Home stays short. */
@Composable
fun LeaderboardButtons(onOpen: (Board) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Board.entries.forEach { b ->
            Text(
                (if (b == Board.SCORE) "🏆 " else "⭐ ") + b.title,
                color = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp))
                    .clickable { onOpen(b) }.padding(vertical = 14.dp).testTag("open-${b.name}"),
            )
        }
    }
}
