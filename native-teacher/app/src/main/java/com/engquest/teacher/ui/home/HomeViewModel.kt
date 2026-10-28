package com.engquest.teacher.ui.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.domain.SubmissionRow
import com.engquest.teacher.ui.submissions.PENDING
import com.engquest.teacher.ui.submissions.REJECTED
import com.engquest.teacher.ui.submissions.parseSubmissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Calendar

/** One line of the activity list: a student's photos for one task on one day. */
data class Activity(
    val className: String,
    val studentNo: String,
    val studentName: String,
    val task: String,
    val day: String,
    val photos: Int,
    val status: String,
)

data class Champion(val name: String, val className: String, val xp: Int)

/** The class label ("X E-1") for an id, or the id itself. */
internal fun classLabel(id: String): String = Classes.ALL.firstOrNull { it.id == id }?.label ?: id

/**
 * Newest first, one line per student + task + day (the server sends one row per photo, newest
 * first). A line is waiting while any of its photos is; sent back only when all of them are.
 */
internal fun recentActivity(rows: List<SubmissionRow>, limit: Int = 30): List<Activity> {
    val lines = LinkedHashMap<String, MutableList<SubmissionRow>>()
    for (r in rows) {
        lines.getOrPut("${r.className}|${r.studentNo}|${r.task}|${r.timestamp.take(10)}") { mutableListOf() }.add(r)
    }
    return lines.values.take(limit).map { photos ->
        val first = photos.first()
        Activity(
            className = first.className,
            studentNo = first.studentNo,
            studentName = first.studentName,
            task = first.task,
            day = first.timestamp.take(10),
            photos = photos.size,
            status = when {
                photos.any { it.status == PENDING } -> PENDING
                photos.all { it.status == REJECTED } -> REJECTED
                else -> "reviewed"
            },
        )
    }
}

/** "Today", "Yesterday" or "21 Oct" for a `yyyy-MM-dd` day, counted from [today]. */
internal fun dayLabel(day: String, today: Calendar = Calendar.getInstance()): String {
    val parts = day.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return day
    val then = Calendar.getInstance().apply {
        clear()
        set(parts[0], parts[1] - 1, parts[2])
    }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(then, today) -> "Today"
        sameDay(then, yesterday) -> "Yesterday"
        else -> "${parts[2]} ${MONTHS[parts[1] - 1]}"
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun parseChampions(raw: JsonObject): List<Champion> =
    (raw["top"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        Champion(
            name = o["name"].text() ?: return@mapNotNull null,
            className = o["className"].text().orEmpty(),
            xp = o["xp"].text()?.toDoubleOrNull()?.toInt() ?: 0,
        )
    }

/** Home: top students of the semester and the latest submissions from every class. */
class HomeViewModel(private val graph: AppGraph, private val scope: CoroutineScope) {
    private var subs by mutableStateOf<JsonObject?>(null)
    private var top by mutableStateOf<JsonObject?>(null)
    private var jobs: List<Job> = emptyList()

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val activity: List<Activity> by derivedStateOf { subs?.let { recentActivity(parseSubmissions(it)) }.orEmpty() }
    val champions: List<Champion> by derivedStateOf { top?.let(::parseChampions).orEmpty() }
    val waiting: Int by derivedStateOf { subs?.let { parseSubmissions(it).count { r -> r.status == PENDING } } ?: 0 }

    /** (Re)loads both lists; also what "Try again" does. */
    fun start() {
        jobs.forEach { it.cancel() }
        error = null
        loading = subs == null
        jobs = listOf(
            scope.launch {
                graph.repo.load("getTaskSubmissions", ALL).collect { c ->
                    c.json?.let { subs = it }
                    loading = subs == null && c.refreshing
                    if (subs == null) error = c.error
                }
            },
            scope.launch {
                graph.repo.load("getLeaderboard", ALL, mapOf("limit" to "3")).collect { c ->
                    c.json?.let { top = it }
                }
            },
        )
    }

    private companion object {
        /** The server sends every class for these reads; this only names the saved copy. */
        const val ALL = "ALL"
    }
}
