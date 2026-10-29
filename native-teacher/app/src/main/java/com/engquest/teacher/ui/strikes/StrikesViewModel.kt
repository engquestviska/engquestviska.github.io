package com.engquest.teacher.ui.strikes

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.Repo
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.ui.onMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Strike(val reason: String, val date: String)

data class StrikeStudent(val no: String, val name: String, val strikes: List<Strike>) {
    val total: Int get() = strikes.size
}

/** The quick reasons the website offers (teacher/strikes.html). */
val QUICK_REASONS = listOf(
    "Not bringing the paper", "Too much time taking notes", "Late to class",
    "Disruptive behavior", "Not paying attention", "Cheating",
)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

/** getAllStrikes → students sorted by number; each strike list newest first. */
internal fun parseStrikes(raw: JsonObject): List<StrikeStudent> =
    (raw["students"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val no = o["no"].text() ?: return@mapNotNull null
        val strikes = (o["strikes"] as? JsonArray).orEmpty().mapNotNull { s ->
            (s as? JsonObject)?.let { Strike(it["reason"].text().orEmpty(), it["date"].text().orEmpty()) }
        }
        StrikeStudent(no, o["name"].text().orEmpty(), strikes)
    }.sortedBy { it.no.toIntOrNull() ?: Int.MAX_VALUE }

/** [raw] with a strike added (newest first) to, or the newest removed from, student [no]. */
internal fun withStrike(raw: JsonObject, no: String, add: Strike?): JsonObject {
    val students = (raw["students"] as? JsonArray).orEmpty().map { el ->
        val o = el as? JsonObject ?: return@map el
        if (o["no"].text() != no) return@map el
        val list = (o["strikes"] as? JsonArray).orEmpty()
        val next = if (add != null) {
            listOf(JsonObject(mapOf("reason" to JsonPrimitive(add.reason), "date" to JsonPrimitive(add.date)))) + list
        } else {
            list.drop(1)
        }
        val latest = next.firstOrNull() as? JsonObject
        JsonObject(
            o + mapOf(
                "strikes" to JsonArray(next),
                "total" to JsonPrimitive(next.size),
                "reason" to JsonPrimitive(latest?.get("reason").text().orEmpty()),
                "date" to JsonPrimitive(latest?.get("date").text().orEmpty()),
            ),
        )
    }
    return JsonObject(raw + ("students" to JsonArray(students)))
}

/**
 * Strikes for one class. Adding or removing shows at once and goes into the outbox in order;
 * the saved copy is updated too, so it is right when reopened offline.
 */
class StrikesViewModel(
    private val graph: AppGraph,
    val classInfo: ClassInfo,
    private val scope: CoroutineScope,
) {
    private val cacheKey = Repo.cacheKey("getAllStrikes", classInfo.id)
    private var raw by mutableStateOf<JsonObject?>(null)
    private var adoptedAt: Long? = null
    private var loader: Job? = null
    private var inFlight = 0
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    var onlyWithStrikes by mutableStateOf(false)

    val students: List<StrikeStudent> by derivedStateOf { raw?.let(::parseStrikes).orEmpty() }

    init {
        graph.scope.launch { for (job in work) if (graph.session.hasLogin()) job() }
        start()
    }

    fun start() {
        loader?.cancel()
        loading = raw == null
        error = null
        loader = scope.launch {
            graph.repo.load("getAllStrikes", classInfo.id).collect { c ->
                val json = c.json
                if (json != null && c.savedAt != adoptedAt && inFlight == 0) {
                    adoptedAt = c.savedAt
                    raw = json
                }
                loading = raw == null && c.refreshing
                error = if (raw == null) c.error else null
            }
        }
    }

    fun add(no: String, reason: String) {
        val current = raw ?: return
        val text = reason.trim().ifEmpty { return }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        raw = withStrike(current, no, Strike(text, today))
        note = "Strike added"
        queue(
            undoOnFail = { r -> withStrike(r, no, null) },
            action = "addStrike",
            params = mapOf("className" to classInfo.id, "studentNo" to no, "reason" to text),
        )
    }

    fun removeLatest(no: String) {
        val current = raw ?: return
        val removed = students.firstOrNull { it.no == no }?.strikes?.firstOrNull() ?: return
        raw = withStrike(current, no, null)
        note = "Latest strike removed"
        queue(
            undoOnFail = { r -> withStrike(r, no, removed) },
            action = "removeStrike",
            params = mapOf("className" to classInfo.id, "studentNo" to no),
        )
    }

    private fun queue(undoOnFail: (JsonObject) -> JsonObject, action: String, params: Map<String, String>) {
        inFlight++
        work.trySend {
            try {
                graph.outbox.enqueue(action, classInfo.id, params)
                scope.onMain { raw }?.let { graph.repo.putStored(cacheKey, it) }
            } catch (e: Throwable) {
                scope.onMain {
                    raw = raw?.let(undoOnFail)
                    note = "Could not save that. Please try again."
                }
            } finally {
                scope.onMain { inFlight-- }
            }
        }
    }

    fun close() {
        loader?.cancel()
        work.close()
    }
}
