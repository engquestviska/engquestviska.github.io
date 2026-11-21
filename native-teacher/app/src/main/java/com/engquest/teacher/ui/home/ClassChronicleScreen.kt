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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.theme.Brand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** One student's line in the class Chronicle. [final] is "" when there is no Final Score yet. */
data class ChronicleRow(val no: Int, val name: String, val rank: Int?, val final: String, val present: Int, val done: Int, val words: Int, val pin: Boolean)
data class ClassChronicle(val meetings: Int, val tasks: Int, val rows: List<ChronicleRow>)

fun parseClassChronicle(j: JsonObject): ClassChronicle {
    fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    val rows = (j["students"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().map {
        ChronicleRow(it.int("no") ?: 0, it.str("name"), it.int("rank"), it.str("final"), it.int("present") ?: 0, it.int("done") ?: 0,
            it.int("words") ?: 0, (it["pin"] as? JsonPrimitive)?.booleanOrNull == true)
    }
    return ClassChronicle(j.int("meetings") ?: 0, j.int("tasks") ?: 0, rows)
}

/** Present days as a percent of the class's meetings: "94%", or "–" before any meeting. */
fun presentText(present: Int, meetings: Int) = if (meetings == 0) "–" else "${Math.round(present * 100.0 / meetings)}%"

/**
 * The teacher's view of EngQuest Chronicle: one class, every student's semester in one line, best
 * Final Score first, and who has made their Chronicle PIN. Online only (the server checks the login).
 */
@Composable
fun ClassChronicleScreen(startClass: String, load: suspend (className: String) -> JsonObject) {
    var cls by rememberSaveable { mutableStateOf(startClass) }
    var data by remember(cls) { mutableStateOf<ClassChronicle?>(null) }
    var error by remember(cls) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(cls, attempt) {
        error = null
        runCatching { parseClassChronicle(load(cls)) }
            .onSuccess { data = it }
            .onFailure { error = (it as? com.engquest.teacher.data.ApiError)?.userMessage ?: "Couldn't load this class." }
    }

    Column(Modifier.fillMaxSize().testTag("classChronicle")) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Classes.ALL.forEach { c ->
                val on = c.id == cls
                Text(
                    c.label, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.background(if (on) Brand else Color.White, RoundedCornerShape(99.dp))
                        .clickable { cls = c.id }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("chron-class-${c.id}"),
                )
            }
        }
        val d = data
        when {
            d == null && error != null -> Message("Couldn't load the class", error, "Try again" to { attempt++ })
            d == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            d.rows.isEmpty() -> Message("No students in this class", null, null)
            else -> {
                Text(
                    "PIN made: ${d.rows.count { it.pin }} of ${d.rows.size}", color = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp).testTag("pinCount"),
                )
                Header()
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(d.rows, key = { it.no }) { r -> Line(r, d) }
                    item { Box(Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

private val Cols = listOf("Final" to 44.dp, "Present" to 56.dp, "Tasks" to 44.dp, "Words" to 46.dp, "PIN" to 30.dp)

@Composable
private fun Header() {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Student", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Cols.forEach { (t, w) -> Text(t, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(w)) }
    }
}

@Composable
private fun Line(r: ChronicleRow, d: ClassChronicle) {
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp).testTag("chronRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(Color(0xFFF1F5F9), CircleShape), contentAlignment = Alignment.Center) {
            Text(r.rank?.toString() ?: "–", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(r.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("No. ${r.no}", color = Muted, fontSize = 11.sp)
        }
        val cells = listOf(
            r.final.ifEmpty { "–" }, presentText(r.present, d.meetings),
            if (d.tasks == 0) "–" else "${r.done}/${d.tasks}", r.words.toString(), if (r.pin) "✓" else "–",
        )
        cells.zip(Cols).forEach { (v, col) ->
            Text(v, color = if (col.first == "PIN" && r.pin) Color(0xFF15803D) else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(col.second))
        }
    }
}
