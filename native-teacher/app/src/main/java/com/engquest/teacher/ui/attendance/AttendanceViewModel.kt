package com.engquest.teacher.ui.attendance

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.engquest.teacher.AppGraph
import com.engquest.teacher.data.Repo
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import com.engquest.teacher.domain.Meeting
import com.engquest.teacher.domain.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today on the phone, as yyyy-MM-dd. */
fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

/** The students of a `getClassData` reply, as the attendance screen reads them. */
internal fun parseStudents(raw: JsonObject): List<Student> =
    (raw["students"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val attendance = (o["attendance"] as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> v.text()?.let { k to it } }.toMap()
        Student(o["no"].text().orEmpty(), o["name"].text().orEmpty(), o["nickname"].text().orEmpty(), attendance)
    }

internal fun parseMeetings(raw: JsonObject): List<Meeting> =
    (raw["meetings"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val number = o["number"].text()?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
        Meeting(number, o["date"].text().orEmpty())
    }

private fun meetingJson(number: Int, date: String) =
    JsonObject(mapOf("number" to JsonPrimitive(number), "date" to JsonPrimitive(date)))

/** [raw] as if the server had saved [records] for [date]; the meeting is new if the date had none. */
internal fun withSavedAttendance(raw: JsonObject, date: String, records: List<Pair<String, String>>): JsonObject {
    val meetings = parseMeetings(raw)
    val existing = meetings.lastOrNull { it.date == date }
    val number = existing?.number ?: ((meetings.maxOfOrNull { it.number } ?: 0) + 1)
    val status = records.toMap()
    val students = (raw["students"] as? JsonArray).orEmpty().map { el ->
        val o = el as? JsonObject ?: return@map el
        val mark = status[o["no"].text().orEmpty()] ?: return@map el
        val attendance = (o["attendance"] as? JsonObject).orEmpty() + ("m$number" to JsonPrimitive(mark))
        JsonObject(o + ("attendance" to JsonObject(attendance)))
    }
    val oldMeetings = (raw["meetings"] as? JsonArray).orEmpty()
    val newMeetings = if (existing != null) oldMeetings else oldMeetings + meetingJson(number, date)
    return JsonObject(raw + mapOf("students" to JsonArray(students), "meetings" to JsonArray(newMeetings)))
}

/** [raw] as if the server had removed the meeting held on [date]. */
internal fun withoutMeeting(raw: JsonObject, date: String): JsonObject {
    val gone = parseMeetings(raw).filter { it.date == date }.map { "m${it.number}" }.toSet()
    if (gone.isEmpty()) return raw
    val students = (raw["students"] as? JsonArray).orEmpty().map { el ->
        val o = el as? JsonObject ?: return@map el
        val attendance = (o["attendance"] as? JsonObject).orEmpty().filterKeys { it !in gone }
        JsonObject(o + ("attendance" to JsonObject(attendance)))
    }
    val meetings = (raw["meetings"] as? JsonArray).orEmpty().filterNot { (it as? JsonObject)?.get("date").text() == date }
    return JsonObject(raw + mapOf("students" to JsonArray(students), "meetings" to JsonArray(meetings)))
}

/**
 * What the attendance screen shows and does for one class. Saving and removing only go into the
 * outbox; the screen (and the saved copy of the class) change at once, and the server's own
 * answer replaces them when it next arrives.
 */
class AttendanceViewModel(
    private val graph: AppGraph,
    val classInfo: ClassInfo,
    private val scope: CoroutineScope,
    today: String = todayIso(),
) {
    private val cacheKey = Repo.cacheKey("getClassData", classInfo.id)
    private var raw by mutableStateOf<JsonObject?>(null)

    /** The teacher's taps on top of the stored marks for [date]. */
    private var edits by mutableStateOf<Map<String, String>>(emptyMap())
    private var adoptedAt: Long? = null
    private var loader: Job? = null

    var date by mutableStateOf(today)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    val loaded: Boolean get() = raw != null

    private val view by derivedStateOf {
        val r = raw
        val students = r?.let(::parseStudents).orEmpty()
        val meetings = r?.let(::parseMeetings).orEmpty()
        val (meeting, initial) = Core.attendanceInitial(students, meetings, date)
        Triple(students, meeting, initial)
    }

    val students: List<Student> get() = view.first

    /** The meeting already held on [date], or null for a new one. */
    val meeting: Meeting? get() = view.second

    val marks: Map<String, String> get() = view.third.mapValues { (no, start) -> edits[no] ?: start }

    val counts: Map<String, Int> get() = Core.attendanceCounts(marks)

    val dirty: Boolean get() = Core.attendanceDirty(view.third, marks)

    val changes: Int get() = marks.count { (no, status) -> status != view.third[no] }

    /** True when there is something to save: a new meeting, or any changed mark. */
    val canSave: Boolean get() = loaded && !saving && students.isNotEmpty() && (meeting == null || dirty)

    init {
        start()
    }

    /** (Re)starts reading the class; also what "Try again" does. */
    fun start() {
        loader?.cancel()
        loading = raw == null
        error = null
        loader = scope.launch {
            graph.repo.load("getClassData", classInfo.id).collect { c ->
                val json = c.json
                // A reply held back while changes are unsent repeats the old copy; skip it, or it
                // would undo what the teacher just did.
                if (json != null && c.savedAt != adoptedAt) {
                    adoptedAt = c.savedAt
                    raw = json
                }
                loading = raw == null && c.refreshing
                error = if (raw == null) c.error else null
            }
        }
    }

    fun showDate(iso: String) {
        if (iso == date) return
        date = iso
        edits = emptyMap()
    }

    fun mark(no: String, status: String) {
        if (saving) return
        edits = edits + (no to status)
    }

    fun markEveryonePresent() {
        if (saving) return
        edits = students.associate { it.no to "Present" }
    }

    fun save() {
        val current = raw ?: return
        if (!canSave) return
        saving = true
        val records = Core.attendanceRecords(students, marks)
        val atDate = date
        graph.scope.launch {
            try {
                val json = JsonArray(
                    records.map { (no, status) ->
                        JsonObject(mapOf("no" to JsonPrimitive(no), "status" to JsonPrimitive(status)))
                    },
                )
                graph.outbox.enqueue(
                    "saveAttendance",
                    classInfo.id,
                    mapOf("className" to classInfo.id, "date" to atDate, "records" to json.toString()),
                )
                val updated = withSavedAttendance(current, atDate, records)
                graph.repo.putStored(cacheKey, updated)
                if (date == atDate) edits = emptyMap()
                raw = updated
            } finally {
                saving = false
            }
        }
    }

    /** Removes the meeting held on the shown date, if there is one. */
    fun removeMeeting() {
        val current = raw ?: return
        if (meeting == null || saving) return
        saving = true
        val atDate = date
        graph.scope.launch {
            try {
                graph.outbox.enqueue(
                    "removeAttendanceMeeting",
                    classInfo.id,
                    mapOf("className" to classInfo.id, "date" to atDate),
                )
                val updated = withoutMeeting(current, atDate)
                graph.repo.putStored(cacheKey, updated)
                edits = emptyMap()
                raw = updated
            } finally {
                saving = false
            }
        }
    }

    fun close() {
        loader?.cancel()
    }
}
