package com.engquest.teacher.ui.scores

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.Repo
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.floor

/** A chip above the list; [id] is the backend chapter ('4'..'6') or [FINAL]. */
data class ScoreChapter(val id: String, val label: String)

const val FINAL = "final"

val SCORE_CHAPTERS = listOf(
    ScoreChapter("4", "Ch 1"),
    ScoreChapter("5", "Ch 2"),
    ScoreChapter("6", "Ch 3"),
    ScoreChapter(FINAL, "Final"),
)

/** A student as the score list shows them; [calc] is only sent for the Final tab. */
data class ScoreStudent(
    val no: String,
    val name: String,
    val scores: Map<String, String>,
    val calc: Map<String, String>,
)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject?.strings(): Map<String, String> =
    this?.mapNotNull { (k, v) -> v.text()?.let { k to it } }.orEmpty().toMap()

internal fun parseScoreColumns(raw: JsonObject): List<String> =
    (raw["inputCols"] as? JsonArray).orEmpty().mapNotNull { it.text() }

internal fun parseScoreStudents(raw: JsonObject): List<ScoreStudent> =
    (raw["students"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val no = o["no"].text() ?: return@mapNotNull null
        val name = o["nickname"].text().orEmpty().ifBlank { o["name"].text().orEmpty() }
        ScoreStudent(no, name, (o["scores"] as? JsonObject).strings(), (o["calc"] as? JsonObject).strings())
    }

/** Whole numbers without a decimal point, anything else as it is ("82.5"); blank when not a number. */
internal fun plainNumber(v: String?): String {
    val n = v?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return ""
    return if (n == floor(n)) n.toLong().toString() else n.toString()
}

/** Plain average of the entered scores, one decimal; blank when nothing is entered. */
internal fun chapterAverage(scores: Map<String, String>, columns: List<String>): String {
    val nums = columns.mapNotNull { scores[it]?.trim()?.takeIf { s -> s.isNotEmpty() }?.toDoubleOrNull() }
    if (nums.isEmpty()) return ""
    return plainNumber((floor(nums.sum() / nums.size * 10 + 0.5) / 10).toString())
}

/** [raw] as if the server had saved [value] in [column] for student [no] (the Final tab also refreshes the totals). */
internal fun withScore(raw: JsonObject, no: String, column: String, value: String, final: Boolean): JsonObject {
    val students = (raw["students"] as? JsonArray).orEmpty().map { el ->
        val o = el as? JsonObject ?: return@map el
        if (o["no"].text() != no) return@map el
        val scores = JsonObject(((o["scores"] as? JsonObject) ?: JsonObject(emptyMap())) + (column to JsonPrimitive(value)))
        var changed = o + ("scores" to scores)
        if (final) {
            val calc = (o["calc"] as? JsonObject) ?: JsonObject(emptyMap())
            val f = Core.finalScore(calc.strings(), value)
            val totals = mapOf(
                "Average" to JsonPrimitive(f.average?.toString().orEmpty()),
                "Final Score" to JsonPrimitive(f.final?.toString().orEmpty()),
                "Indicator" to JsonPrimitive(f.indicator),
            )
            changed = changed + ("calc" to JsonObject(calc + totals))
        }
        JsonObject(changed)
    }
    return JsonObject(raw + ("students" to JsonArray(students)))
}

/**
 * Scores for one class: a chapter (or Final) and a column at a time. Typing only changes a draft;
 * the draft is checked and saved when the teacher moves on (Next, Done, another box, another
 * chapter, leaving the tab). A changed value changes the shown totals at once and goes into the
 * outbox, one at a time and in the order made; the saved copy of the sheet is updated too.
 */
class ScoresViewModel(
    private val graph: AppGraph,
    val classInfo: ClassInfo,
    private val scope: CoroutineScope,
) {
    private var raw by mutableStateOf<JsonObject?>(null)
    private var adoptedAt: Long? = null
    private var loader: Job? = null
    private var inFlight = 0

    /** Work for the outbox, run one after another so saves keep their order. */
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** What the teacher typed and has not saved yet, by student number (this chapter and column only). */
    private val drafts = mutableStateMapOf<String, String>()
    private var note by mutableStateOf<ScoreNote?>(null)

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var chapter by mutableStateOf(
        graph.prefs.getString(KEY_CHAPTER, null)?.takeIf { id -> SCORE_CHAPTERS.any { it.id == id } } ?: SCORE_CHAPTERS.first().id,
    )
        private set
    private var chosenColumn by mutableStateOf<String?>(null)

    val loaded: Boolean get() = raw != null

    val isFinal: Boolean get() = chapter == FINAL

    val columns: List<String> by derivedStateOf { raw?.let(::parseScoreColumns).orEmpty() }

    /** The column being typed into: the remembered one if this chapter still has it, else the first. */
    val column: String? by derivedStateOf {
        val saved = chosenColumn ?: graph.prefs.getString(KEY_COLUMN + chapter, null)
        columns.firstOrNull { it == saved } ?: columns.firstOrNull()
    }

    val students: List<ScoreStudent> by derivedStateOf {
        raw?.let(::parseScoreStudents).orEmpty().sortedBy { it.no.toIntOrNull() ?: Int.MAX_VALUE }
    }

    /** "Chapter 1 · Task 1", "Final scores · ASAS". */
    val heading: String by derivedStateOf {
        val place = if (isFinal) "Final scores" else "Chapter ${(chapter.toIntOrNull() ?: 4) - 3}"
        column?.let { "$place · ${Core.colLabel(it)}" } ?: place
    }

    /** The line above the list: a problem with what is typed, otherwise the last thing that happened. */
    val status: ScoreNote by derivedStateOf {
        val bad = students.firstNotNullOfOrNull { s ->
            val check = Core.validateScore(drafts[s.no] ?: return@firstNotNullOfOrNull null)
            if (check.ok) null else "${check.error} · ${Core.firstName(s.name)}"
        }
        if (bad != null) ScoreNote(bad, isError = true) else note ?: ScoreNote(HINT, isError = false)
    }

    init {
        graph.scope.launch { for (job in work) job() }
        start()
    }

    /** What the box for [no] shows: the draft while typing, else the saved score. */
    fun shown(no: String): String = drafts[no] ?: savedValue(no)

    fun isInvalid(no: String): Boolean = drafts[no]?.let { !Core.validateScore(it).ok } == true

    fun isLow(no: String): Boolean = !isInvalid(no) && Core.isLowScore(shown(no))

    /** The right-hand number: the average of the entered scores, or the final score on the Final tab. */
    fun total(no: String): String {
        val s = students.firstOrNull { it.no == no } ?: return ""
        return if (isFinal) plainNumber(s.calc["Final Score"]) else chapterAverage(s.scores, columns)
    }

    fun type(no: String, text: String) {
        drafts[no] = text
        note = null
    }

    /** (Re)starts reading the current chapter; also what "Try again" does. */
    fun start() {
        loader?.cancel()
        val id = chapter
        loading = raw == null
        error = null
        loader = scope.launch {
            val flow = if (id == FINAL) {
                graph.repo.load("getFinalScores", classInfo.id)
            } else {
                graph.repo.load("getChapterScores", classInfo.id, mapOf("chapter" to id), chapter = id)
            }
            flow.collect { c ->
                val json = c.json
                // A reply that arrives while saves are still being queued would undo them on screen.
                if (json != null && c.savedAt != adoptedAt && inFlight == 0) {
                    adoptedAt = c.savedAt
                    raw = json
                }
                loading = raw == null && c.refreshing
                error = if (raw == null) c.error else null
            }
        }
    }

    fun chooseChapter(id: String) {
        if (id == chapter) return
        commitAll()
        drafts.clear()
        chapter = id
        chosenColumn = null
        raw = null
        adoptedAt = null
        note = null
        graph.prefs.edit().putString(KEY_CHAPTER, id).apply()
        start()
    }

    fun chooseColumn(col: String) {
        if (col == column) return
        commitAll()
        drafts.clear()
        chosenColumn = col
        note = null
        graph.prefs.edit().putString(KEY_COLUMN + chapter, col).apply()
    }

    /** Saves every valid draft (the teacher is leaving this box, column, chapter or tab). */
    fun commitAll() {
        drafts.keys.toList().forEach(::commit)
    }

    /** Checks the draft for [no] and, if it is a changed, valid score, shows it and queues the save. */
    fun commit(no: String) {
        val draft = drafts[no] ?: return
        val col = column ?: return
        val student = students.firstOrNull { it.no == no } ?: return
        val check = Core.validateScore(draft)
        if (!check.ok) return // stays red, with its message, until fixed
        drafts.remove(no)
        val before = student.scores[col].orEmpty()
        if (check.value == before) return
        val current = raw ?: return
        val id = chapter
        val final = isFinal
        val first = Core.firstName(student.name)
        val key = cacheKey(id)
        val updated = withScore(current, no, col, check.value, final)
        raw = updated
        note = ScoreNote("Saved · $first · ${Core.colLabel(col)}" + if (check.value.isEmpty()) " cleared" else " = ${check.value}", isError = false)
        inFlight++
        work.trySend {
            try {
                enqueue(id, no, col, check.value)
                graph.repo.putStored(key, if (cacheKey(chapter) == key) raw ?: updated else updated)
            } catch (e: Throwable) {
                // Nothing was queued: put the old score back on screen and say so.
                if (chapter == id) raw = raw?.let { withScore(it, no, col, before, final) }
                note = ScoreNote("Could not save that for $first. Please try again.", isError = true)
            } finally {
                inFlight--
            }
        }
    }

    private fun cacheKey(id: String): String =
        if (id == FINAL) Repo.cacheKey("getFinalScores", classInfo.id) else Repo.cacheKey("getChapterScores", classInfo.id, id)

    private suspend fun enqueue(id: String, no: String, col: String, value: String) {
        val common = mapOf("className" to classInfo.id, "studentNo" to no, "column" to col, "value" to value)
        if (id == FINAL) {
            graph.outbox.enqueue("saveFinalScore", classInfo.id, common)
        } else {
            graph.outbox.enqueue("saveChapterScore", classInfo.id, common + ("chapter" to id))
        }
    }

    private fun savedValue(no: String): String {
        val col = column ?: return ""
        return students.firstOrNull { it.no == no }?.scores?.get(col).orEmpty()
    }

    fun close() {
        commitAll()
        loader?.cancel()
        work.close() // saves already queued still reach the outbox
    }

    companion object {
        const val KEY_CHAPTER = "scoreChapter"
        const val KEY_COLUMN = "scoreCol_"
        const val HINT = "Type a score, then tap Next. Each score saves by itself."
    }
}

/** The line above the score list. */
data class ScoreNote(val text: String, val isError: Boolean)
