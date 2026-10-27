package com.engquest.teacher.ui.xp

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.Repo
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One reason XP can be given for: [key] is what the server takes, [prop] the counter it returns. */
data class Reason(val key: String, val prop: String, val label: String, val xp: Int)

object XpReasons {
    val ALL = listOf(
        Reason("Answering", "Answering", "Answering", 5),
        Reason("Presenting", "Presenting", "Presenting", 20),
        Reason("Helping Hand", "HelpingHand", "Helping Hand", 20),
        Reason("Doing Task On Time", "DoingTaskOnTime", "Task On Time", 20),
        Reason("Quiz", "Quiz", "Quiz", 10),
        Reason("Vocabulary Box", "VocabularyBox", "Vocab Box", 2),
    )

    fun byKey(key: String?): Reason = ALL.firstOrNull { it.key == key } ?: ALL.first()
}

/** A student as the XP grid shows them. */
data class XpStudent(val no: String, val name: String, val total: Int)

/** A message under the grid; [undo] is set for a tap that can still be reversed. */
data class XpToast(val text: String, val isError: Boolean = false, val undo: (() -> Unit)? = null)

/** The short "+20" that floats up from a tile. */
data class XpPop(val text: String, val negative: Boolean, val seq: Int)

private const val MINUS = "−"

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.number(): Int? = text()?.toDoubleOrNull()?.toInt()

internal fun parseXpStudents(raw: JsonObject): List<XpStudent> =
    (raw["students"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val no = o["no"].text() ?: return@mapNotNull null
        val name = o["nickname"].text().orEmpty().ifBlank { o["name"].text().orEmpty() }
        XpStudent(no, name, o["Total"].number() ?: 0)
    }

/** The student's count for [reason], or null when the server did not send one. */
internal fun reasonCount(raw: JsonObject, no: String, reason: Reason): Int? =
    (raw["students"] as? JsonArray).orEmpty()
        .firstOrNull { (it as? JsonObject)?.get("no").text() == no }
        .let { (it as? JsonObject)?.get(reason.prop).number() }

/** [raw] as if the server had changed [reason] by [delta] for student [no]. */
internal fun withXp(raw: JsonObject, no: String, reason: Reason, delta: Int): JsonObject {
    val students = (raw["students"] as? JsonArray).orEmpty().map { el ->
        val o = el as? JsonObject ?: return@map el
        if (o["no"].text() != no) return@map el
        val changed = mutableMapOf<String, JsonElement>("Total" to JsonPrimitive((o["Total"].number() ?: 0) + reason.xp * delta))
        o[reason.prop].number()?.let { changed[reason.prop] = JsonPrimitive(maxOf(0, it + delta)) }
        JsonObject(o + changed)
    }
    return JsonObject(raw + ("students" to JsonArray(students)))
}

/**
 * Give XP for one class. Each tap changes the shown totals at once and goes into the outbox, one
 * at a time and in the order made. The saved copy of the class is updated too, so the totals
 * are still right when the screen is reopened offline. A new instance always starts in Give mode.
 */
class XpViewModel(
    private val graph: AppGraph,
    val classInfo: ClassInfo,
    private val scope: CoroutineScope,
) {
    private val cacheKey = Repo.cacheKey("getAllActiveness", classInfo.id)
    private var raw by mutableStateOf<JsonObject?>(null)
    private var adoptedAt: Long? = null
    private var loader: Job? = null
    private var popSeq = 0
    private var inFlight = 0

    /** Work for the outbox, run one after another so taps (and undos) keep their order. */
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var takeBack by mutableStateOf(false)
        private set
    var reason by mutableStateOf(XpReasons.byKey(graph.prefs.getString(KEY_REASON, null)))
        private set
    var toast by mutableStateOf<XpToast?>(null)
        private set
    var pops by mutableStateOf<Map<String, XpPop>>(emptyMap())
        private set

    val loaded: Boolean get() = raw != null

    val students: List<XpStudent> by derivedStateOf {
        raw?.let(::parseXpStudents).orEmpty().sortedBy { it.no.toIntOrNull() ?: Int.MAX_VALUE }
    }

    init {
        graph.scope.launch { for (job in work) job() }
        start()
    }

    /** (Re)starts reading the class; also what "Try again" does. */
    fun start() {
        loader?.cancel()
        loading = raw == null
        error = null
        loader = scope.launch {
            graph.repo.load("getAllActiveness", classInfo.id).collect { c ->
                val json = c.json
                // A reply that arrives while taps are still being queued would undo them on screen.
                if (json != null && c.savedAt != adoptedAt && inFlight == 0) {
                    adoptedAt = c.savedAt
                    raw = json
                }
                loading = raw == null && c.refreshing
                error = if (raw == null) c.error else null
            }
        }
    }

    fun chooseTakeBack(on: Boolean) {
        takeBack = on
    }

    fun pickReason(r: Reason) {
        reason = r
        graph.prefs.edit().putString(KEY_REASON, r.key).apply()
    }

    fun clearToast(shown: XpToast) {
        if (toast === shown) toast = null
    }

    fun tap(no: String) {
        val current = raw ?: return
        val student = students.firstOrNull { it.no == no } ?: return
        val r = reason
        val delta = if (takeBack) -1 else 1
        val first = Core.firstName(student.name)
        if (delta < 0 && (reasonCount(current, no, r) ?: 1) <= 0) {
            toast = XpToast("$first has no ${r.label} XP to take back", isError = true)
            return
        }
        raw = withXp(current, no, r, delta)
        val sign = if (delta < 0) MINUS else "+"
        showPop(no, "$sign${r.xp}", delta < 0)
        val id = CompletableDeferred<Long>()
        toast = XpToast("$sign${r.xp} XP · $first · ${r.label}", undo = { undo(no, first, r, delta, id) })
        inFlight++
        work.trySend {
            try {
                id.complete(send(no, r, delta))
                store()
            } catch (e: Throwable) {
                id.completeExceptionally(e)
            } finally {
                inFlight--
            }
        }
    }

    /** Reverses a tap: dropped from the outbox if still waiting there, otherwise cancelled out by an opposite write. */
    private fun undo(no: String, first: String, r: Reason, delta: Int, id: CompletableDeferred<Long>) {
        val current = raw ?: return
        raw = withXp(current, no, r, -delta)
        toast = XpToast("Undone · $first")
        inFlight++
        work.trySend {
            try {
                if (!graph.outbox.cancelIfPending(id.await())) send(no, r, -delta)
                store()
            } catch (e: Throwable) {
                // The tap itself never reached the outbox, so there is nothing to reverse.
            } finally {
                inFlight--
            }
        }
    }

    private suspend fun send(no: String, r: Reason, delta: Int): Long = graph.outbox.enqueue(
        "incrementActiveness",
        classInfo.id,
        mapOf("className" to classInfo.id, "studentNo" to no, "column" to r.key, "delta" to delta.toString()),
    )

    private suspend fun store() {
        raw?.let { graph.repo.putStored(cacheKey, it) }
    }

    private fun showPop(no: String, text: String, negative: Boolean) {
        val pop = XpPop(text, negative, ++popSeq)
        pops = pops + (no to pop)
        scope.launch {
            delay(800)
            if (pops[no]?.seq == pop.seq) pops = pops - no
        }
    }

    fun close() {
        loader?.cancel()
        work.close() // taps already queued still reach the outbox
    }

    companion object {
        const val KEY_REASON = "xpReason"
    }
}
