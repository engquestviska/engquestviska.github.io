package com.engquest.teacher.ui.tasks

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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class TaskStudent(val no: String, val name: String, val done: Map<String, Boolean>)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

/** C{chapter}T1–T3 plus any other C{chapter}T… keys the server knows, in order. */
internal fun taskKeysFor(chapter: Int, serverKeys: List<String>): List<String> =
    ((1..3).map { "C${chapter}T$it" } + serverKeys.map { it.uppercase() }.filter { it.startsWith("C${chapter}T") })
        .distinct()
        .sortedBy { it.substringAfter('T').toIntOrNull() ?: Int.MAX_VALUE }

/**
 * Every student of the class (from the roster), with their task ticks from getAllTasks, which
 * only lists students who already have at least one tick.
 */
internal fun mergeTasks(roster: JsonObject?, tasks: JsonObject?): List<TaskStudent> {
    val ticks = HashMap<String, Pair<String, Map<String, Boolean>>>()
    (tasks?.get("students") as? JsonArray).orEmpty().forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        val no = o["no"].text() ?: return@forEach
        val done = (o["tasks"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.booleanOrNull?.let { k.uppercase() to it }
        }.toMap()
        ticks[no] = o["name"].text().orEmpty() to done
    }
    val names = LinkedHashMap<String, String>()
    (roster?.get("students") as? JsonArray).orEmpty().forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        o["no"].text()?.let { names[it] = o["name"].text().orEmpty() }
    }
    ticks.forEach { (no, t) -> names.putIfAbsent(no, t.first) }
    return names.map { (no, name) -> TaskStudent(no, name, ticks[no]?.second.orEmpty()) }
        .sortedBy { it.no.toIntOrNull() ?: Int.MAX_VALUE }
}

/** [raw] (a getAllTasks reply) with student [no]'s [key] set to [done], adding the student if missing. */
internal fun withTick(raw: JsonObject, no: String, name: String, key: String, done: Boolean): JsonObject {
    val list = (raw["students"] as? JsonArray).orEmpty()
    var found = false
    val students = list.map { el ->
        val o = el as? JsonObject ?: return@map el
        if (o["no"].text() != no) return@map el
        found = true
        JsonObject(o + ("tasks" to JsonObject((o["tasks"] as? JsonObject).orEmpty() + (key to JsonPrimitive(done)))))
    }
    val added = if (found) students else students + JsonObject(
        mapOf("no" to JsonPrimitive(no), "name" to JsonPrimitive(name), "tasks" to JsonObject(mapOf(key to JsonPrimitive(done)))),
    )
    return JsonObject(raw + ("students" to JsonArray(added)))
}

/** Task ticks for one class: each tap shows at once and goes into the outbox in order. */
class TasksViewModel(
    private val graph: AppGraph,
    val classInfo: ClassInfo,
    private val scope: CoroutineScope,
) {
    private val cacheKey = Repo.cacheKey("getAllTasks", classInfo.id)
    private var raw by mutableStateOf<JsonObject?>(null)
    private var roster by mutableStateOf<JsonObject?>(null)
    private var adoptedAt: Long? = null
    private var loaders: List<Job> = emptyList()
    private var inFlight = 0
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    var chapter by mutableStateOf(graph.prefs.getInt(KEY_CHAPTER, 1).coerceIn(1, 3))
        private set
    var onlyMissing by mutableStateOf(false)

    val students: List<TaskStudent> by derivedStateOf { mergeTasks(roster, raw) }
    val keys: List<String> by derivedStateOf {
        taskKeysFor(chapter, (raw?.get("taskKeys") as? JsonArray).orEmpty().mapNotNull { it.text() })
    }

    init {
        graph.scope.launch { for (job in work) if (graph.session.hasLogin()) job() }
        start()
    }

    fun start() {
        loaders.forEach { it.cancel() }
        loading = raw == null
        error = null
        loaders = listOf(
            scope.launch {
                graph.repo.load("getAllTasks", classInfo.id).collect { c ->
                    val json = c.json
                    if (json != null && c.savedAt != adoptedAt && inFlight == 0) {
                        adoptedAt = c.savedAt
                        raw = json
                    }
                    loading = raw == null && c.refreshing
                    error = if (raw == null) c.error else null
                }
            },
            scope.launch {
                graph.repo.load("getStudents", classInfo.id).collect { c -> c.json?.let { roster = it } }
            },
        )
    }

    fun pickChapter(ch: Int) {
        chapter = ch
        graph.prefs.edit().putInt(KEY_CHAPTER, ch).apply()
    }

    fun toggle(no: String, key: String) {
        val current = raw ?: return
        val student = students.firstOrNull { it.no == no } ?: return
        val done = !(student.done[key] ?: false)
        raw = withTick(current, no, student.name, key, done)
        note = null
        inFlight++
        work.trySend {
            try {
                graph.outbox.enqueue(
                    "saveTaskStatus", classInfo.id,
                    mapOf("className" to classInfo.id, "studentNo" to no, "tasks" to """{"$key":$done}"""),
                )
                scope.onMain { raw }?.let { graph.repo.putStored(cacheKey, it) }
            } catch (e: Throwable) {
                scope.onMain {
                    raw = raw?.let { withTick(it, no, student.name, key, !done) }
                    note = "Could not save that. Please try again."
                }
            } finally {
                scope.onMain { inFlight-- }
            }
        }
    }

    fun close() {
        loaders.forEach { it.cancel() }
        work.close()
    }

    companion object {
        const val KEY_CHAPTER = "tasksChapter"
    }
}
