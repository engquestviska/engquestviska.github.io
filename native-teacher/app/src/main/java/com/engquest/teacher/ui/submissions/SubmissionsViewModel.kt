package com.engquest.teacher.ui.submissions

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.Repo
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import com.engquest.teacher.domain.SubmissionGroup
import com.engquest.teacher.domain.SubmissionRow
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

/** Where a notification points: one student's submissions in one class. */
data class SubmissionLink(val className: String, val studentNo: String)

/** The line under the photos: what just happened, or what is wrong. */
data class SubNote(val text: String, val isError: Boolean)

const val PENDING = "pending"
const val REVIEWED = "reviewed"
const val REJECTED = "rejected"

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun parseSubmissions(raw: JsonObject): List<SubmissionRow> =
    (raw["submissions"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val subId = o["subId"].text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        SubmissionRow(
            subId = subId,
            className = o["className"].text().orEmpty(),
            studentNo = o["studentNo"].text().orEmpty(),
            studentName = o["studentName"].text().orEmpty(),
            task = o["task"].text().orEmpty(),
            timestamp = o["timestamp"].text().orEmpty(),
            status = o["status"].text() ?: PENDING,
        )
    }

/** [raw] with the status of each photo in [statuses] (by subId) changed. */
internal fun withStatus(raw: JsonObject, statuses: Map<String, String>): JsonObject {
    val rows = (raw["submissions"] as? JsonArray).orEmpty().map { el ->
        val o = el as? JsonObject ?: return@map el
        val status = statuses[o["subId"].text()] ?: return@map el
        JsonObject(o + ("status" to JsonPrimitive(status)))
    }
    return JsonObject(raw + ("submissions" to JsonArray(rows)))
}

/**
 * Submissions for one class: one student at a time, their photos, an optional score per photo,
 * then Approve (or Send back for one photo). The decision shows at once and goes into the
 * outbox, one at a time and in the order made; the saved copy of the list is updated too, so the
 * queue and the tab badge stay right after a reopen while offline.
 */
class SubmissionsViewModel(
    private val graph: AppGraph,
    val classInfo: ClassInfo,
    private val scope: CoroutineScope,
) {
    private val cacheKey = Repo.cacheKey("getTaskSubmissions", classInfo.id)
    private var raw by mutableStateOf<JsonObject?>(null)
    private var adoptedAt: Long? = null
    private var loader: Job? = null
    private var inFlight = 0

    /** A student a notification asked for, until the list holding them has arrived. */
    private var linkedNo: String? = null

    /** Work for the outbox, run one after another so decisions keep their order. */
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** Scores typed and not yet approved, by subId. */
    private val scores = mutableStateMapOf<String, String>()
    private var selectedNo by mutableStateOf<String?>(null)

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var showAll by mutableStateOf(false)
        private set
    /** The photo shown for the current student. */
    var slide by mutableIntStateOf(0)
        private set
    var note by mutableStateOf<SubNote?>(null)
        private set

    val loaded: Boolean get() = raw != null

    private val rows: List<SubmissionRow> by derivedStateOf { raw?.let(::parseSubmissions).orEmpty() }

    val groups: List<SubmissionGroup> by derivedStateOf { Core.groupSubmissions(rows, classInfo.id, showAll) }

    /** Photos of this class still waiting to be checked (the tab badge). */
    val waiting: Int by derivedStateOf { rows.count { it.className == classInfo.id && it.status == PENDING } }

    val waitingStudents: Int by derivedStateOf { Core.groupSubmissions(rows, classInfo.id, false).size }

    val current: SubmissionGroup? by derivedStateOf {
        groups.firstOrNull { it.studentNo == selectedNo } ?: groups.firstOrNull()
    }

    val position: Int get() = current?.let { c -> groups.indexOfFirst { it.studentNo == c.studentNo } } ?: -1

    val currentItem: SubmissionRow? get() = current?.items?.let { it.getOrNull(slide) ?: it.firstOrNull() }

    /** True while a typed score would be lost by leaving. */
    val hasTypedScores: Boolean by derivedStateOf { scores.values.any { it.isNotBlank() } }

    /** Starts sending queued decisions and reading the list (from an effect, never during composition). */
    fun open() {
        graph.scope.launch { for (job in work) job() }
        start()
    }

    /** (Re)starts reading the list; also what "Try again" does. */
    fun start() {
        loader?.cancel()
        loading = raw == null
        error = null
        loader = scope.launch {
            graph.repo.load("getTaskSubmissions", classInfo.id).collect { c ->
                val json = c.json
                // A reply that arrives while decisions are still being queued would undo them on screen.
                if (json != null && c.savedAt != adoptedAt && inFlight == 0) {
                    adoptedAt = c.savedAt
                    raw = json
                }
                loading = raw == null && c.refreshing
                error = if (raw == null) c.error else null
                applyLink(finalTry = !c.refreshing)
            }
        }
    }

    /** Opens [studentNo]'s submissions (from a notification), under All if nothing of theirs is waiting. */
    fun focus(studentNo: String) {
        linkedNo = studentNo
        applyLink(finalTry = false)
    }

    private fun applyLink(finalTry: Boolean) {
        val no = linkedNo ?: return
        val waitingHere = Core.groupSubmissions(rows, classInfo.id, false).any { it.studentNo == no }
        val anyHere = waitingHere || Core.groupSubmissions(rows, classInfo.id, true).any { it.studentNo == no }
        if (anyHere) {
            if (!waitingHere) showAll = true
            select(no)
            linkedNo = null
        } else if (finalTry) {
            linkedNo = null
        }
    }

    fun chooseFilter(all: Boolean) {
        if (all == showAll) return
        showAll = all
        selectedNo = null
        slide = 0
        note = null
    }

    fun choose(studentNo: String) {
        select(studentNo)
        note = null
    }

    private fun select(studentNo: String?) {
        selectedNo = studentNo
        slide = 0
    }

    fun showSlide(i: Int) {
        slide = i
    }

    fun next() {
        groups.getOrNull(position + 1)?.let { choose(it.studentNo) }
    }

    fun typed(subId: String): String = scores[subId].orEmpty()

    fun type(subId: String, text: String) {
        scores[subId] = text
        note = null
    }

    fun scoreOk(subId: String): Boolean = Core.validateScore(typed(subId)).ok

    /** Drops typed scores (the teacher chose to leave without approving). */
    fun forgetTyped() {
        scores.clear()
    }

    /**
     * Approves every waiting photo of the current student. For each photo, in order: its score
     * (if one was typed and the task has a score column), the review, then the task tick.
     */
    fun approve() {
        val g = current ?: return
        val before = raw ?: return
        val pend = g.items.filter { it.status == PENDING }
        if (pend.isEmpty()) return
        for (item in pend) {
            val check = Core.validateScore(typed(item.subId))
            if (!check.ok) {
                slide = g.items.indexOf(item)
                note = SubNote("${check.error} (${Core.taskLabel(item.task)})", isError = true)
                return
            }
        }
        val plan = pend.map { it to Core.validateScore(typed(it.subId)).value }
        val first = Core.firstName(g.studentName)
        val oldGroups = groups
        raw = withStatus(before, pend.associate { it.subId to REVIEWED })
        pend.forEach { scores.remove(it.subId) }
        moveOnFrom(g, oldGroups)
        note = SubNote("Approved ${pend.size} · $first", isError = false)
        inFlight++
        work.trySend {
            var done = 0
            try {
                try {
                    for ((item, score) in plan) {
                        enqueueApproval(item, score)
                        done++
                    }
                } catch (e: Throwable) {
                    // The rest never reached the outbox: put them back as waiting, with their scores.
                    val left = plan.drop(done)
                    scope.onMain {
                        raw = raw?.let { r -> withStatus(r, left.associate { it.first.subId to PENDING }) }
                        left.forEach { (item, score) -> if (score.isNotEmpty()) scores[item.subId] = score }
                        note = SubNote("Could not approve $first's work. Please try again.", isError = true)
                    }
                }
                runCatching { store() }
            } finally {
                scope.onMain { inFlight-- }
            }
        }
    }

    /** Sends one photo back so the student can upload it again. */
    fun sendBack(item: SubmissionRow) {
        val g = current ?: return
        val before = raw ?: return
        if (item.status != PENDING) return
        val oldGroups = groups
        raw = withStatus(before, mapOf(item.subId to REJECTED))
        scores.remove(item.subId)
        if (g.items.none { it.status == PENDING && it.subId != item.subId }) moveOnFrom(g, oldGroups)
        slide = slide.coerceAtMost((current?.items?.size ?: 1) - 1).coerceAtLeast(0)
        val label = Core.taskLabel(item.task).ifBlank { "photo" }
        note = SubNote("Sent back · $label", isError = false)
        inFlight++
        work.trySend {
            try {
                try {
                    graph.outbox.enqueue(
                        "reviewTaskSubmission",
                        classInfo.id,
                        mapOf("subId" to item.subId, "decision" to "rejected"),
                    )
                } catch (e: Throwable) {
                    // Nothing was queued: the photo is waiting again.
                    scope.onMain {
                        raw = raw?.let { withStatus(it, mapOf(item.subId to PENDING)) }
                        note = SubNote("Could not send back $label. Please try again.", isError = true)
                    }
                }
                runCatching { store() }
            } finally {
                scope.onMain { inFlight-- }
            }
        }
    }

    /** After finishing [g]: the next student (under Waiting the finished one drops out of the list). */
    private fun moveOnFrom(g: SubmissionGroup, oldGroups: List<SubmissionGroup>) {
        val oldPos = oldGroups.indexOfFirst { it.studentNo == g.studentNo }
        val now = groups
        val still = now.indexOfFirst { it.studentNo == g.studentNo }
        val idx = if (still >= 0) minOf(still + 1, now.lastIndex) else minOf(oldPos, now.lastIndex)
        select(now.getOrNull(idx)?.studentNo)
    }

    private suspend fun enqueueApproval(item: SubmissionRow, score: String) {
        val map = Core.taskToChapterCol(item.task)
        if (score.isNotEmpty() && map != null) {
            graph.outbox.enqueue(
                "saveChapterScore",
                classInfo.id,
                mapOf(
                    "className" to classInfo.id,
                    "chapter" to map.chapter,
                    "studentNo" to item.studentNo,
                    "column" to map.column,
                    "value" to score,
                ),
            )
        }
        graph.outbox.enqueue("reviewTaskSubmission", classInfo.id, mapOf("subId" to item.subId, "decision" to "approved"))
        val task = item.task.trim()
        if (task.isNotEmpty()) {
            graph.outbox.enqueue(
                "saveTaskStatus",
                classInfo.id,
                mapOf(
                    "className" to classInfo.id,
                    "studentNo" to item.studentNo,
                    "tasks" to JsonObject(mapOf(task to JsonPrimitive(true))).toString(),
                ),
            )
        }
    }

    private suspend fun store() {
        scope.onMain { raw }?.let { graph.repo.putStored(cacheKey, it) }
    }

    fun close() {
        loader?.cancel()
        work.close() // decisions already queued still reach the outbox
    }
}
