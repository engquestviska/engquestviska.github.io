package com.engquest.teacher.domain

import kotlin.math.floor

data class ChapterCol(val chapter: String, val column: String)

data class ScoreCheck(val ok: Boolean, val value: String, val error: String? = null)

/** average/final are null when there is nothing to average (core.js returns ''). */
data class FinalCalc(val average: Double?, val final: Int?, val indicator: String)

/** Pure teacher-app rules, ported 1:1 from teacher-app/js/core.js. */
object Core {
    val STATUSES = listOf("Present", "Absent", "Sick", "Permit")

    private val TASK_CODE = Regex("^C([1-3])T([1-3])$")
    private val EXAM_CODE = Regex("^C([1-3])([FS])$")
    private val SCORE = Regex("^\\d+(\\.\\d+)?$")
    private val SAFE_MIME = Regex("^image/(png|jpeg|jpg|webp|gif|heic|heif)$", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
    private val ANY_TASK = Regex("^C(\\d+)T(\\d+)$")
    private val ANY_FORMATIVE = Regex("^C(\\d+)F$")
    private val ANY_SUMMATIVE = Regex("^C(\\d+)S$")

    // UI "Chapter n" lives in backend chapter n+3. Only Chapters 1-3 and Tasks 1-3
    // exist on the backend; anything else gets no score box.
    fun taskToChapterCol(code: String?): ChapterCol? {
        val s = code.orEmpty().trim().uppercase()
        TASK_CODE.matchEntire(s)?.let { m ->
            return ChapterCol((m.groupValues[1].toInt() + 3).toString(), "Task " + m.groupValues[2].toInt())
        }
        EXAM_CODE.matchEntire(s)?.let { m ->
            val column = if (m.groupValues[2] == "F") "Formative" else "Summative"
            return ChapterCol((m.groupValues[1].toInt() + 3).toString(), column)
        }
        return null
    }

    /** The mime type comes from a student's upload, so only whitelisted image types pass. */
    fun safeMime(mime: String?): String =
        if (mime != null && SAFE_MIME.matches(mime)) mime.lowercase() else "image/jpeg"

    // Same formula as the backend's getFinalScores: plain average of the chapter
    // finals that exist plus the exam score, rounded; 75 or more is Safe.
    fun finalScore(calc: Map<String, String?>, exam: String?): FinalCalc {
        val vals = (listOf("Chapter 1", "Chapter 2", "Chapter 3").map { calc[it] } + exam)
            .mapNotNull { number(it) }
        if (vals.isEmpty()) return FinalCalc(null, null, "")
        val avg = jsRound(vals.sum() / vals.size * 100) / 100.0
        val fin = jsRound(avg).toInt()
        return FinalCalc(avg, fin, if (fin >= 75) "Safe" else "Not Safe")
    }

    // The backend column is named ASAT all year; semester 1's exam is called ASAS.
    fun colLabel(col: String): String = if (col == "ASAT") "ASAS" else col

    fun validateScore(v: String?): ScoreCheck {
        val s = v.orEmpty().trim()
        if (s.isEmpty()) return ScoreCheck(true, "")
        if (!SCORE.matches(s)) return ScoreCheck(false, s, "Score must be a number")
        if (s.toDouble() > 100) return ScoreCheck(false, s, "Score must be between 0 and 100")
        return ScoreCheck(true, s)
    }

    fun isLowScore(v: String?): Boolean {
        val n = number(v.orEmpty().trim()) ?: return false
        return n < 75
    }

    /** The meeting held on [date] (last one wins, as in core.js) and everyone's starting mark. */
    fun attendanceInitial(
        students: List<Student>,
        meetings: List<Meeting>,
        date: String,
    ): Pair<Meeting?, Map<String, String>> {
        val meeting = meetings.lastOrNull { it.date == date }
        val marks = LinkedHashMap<String, String>()
        for (s in students) {
            val stored = meeting?.let { s.attendance["m" + it.number] }
            marks[s.no] = if (stored != null && stored in STATUSES) stored else "Present"
        }
        return meeting to marks
    }

    fun attendanceCounts(marks: Map<String, String>): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        STATUSES.forEach { counts[it] = 0 }
        marks.values.forEach { status -> counts[status]?.let { counts[status] = it + 1 } }
        return counts
    }

    fun attendanceDirty(initialMarks: Map<String, String>, marks: Map<String, String>): Boolean =
        marks.any { (no, status) -> status != initialMarks[no] }

    /** One (student no, status) per student, in roster order; unmarked counts as Present. */
    fun attendanceRecords(students: List<Student>, marks: Map<String, String>): List<Pair<String, String>> =
        students.map { it.no to (marks[it.no]?.takeIf { s -> s.isNotEmpty() } ?: "Present") }

    fun initials(name: String?): String {
        val out = name.orEmpty().split(WHITESPACE).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
        return out.ifEmpty { "EQ" }
    }

    fun firstName(name: String?): String = name.orEmpty().trim().split(WHITESPACE).first()

    fun taskLabel(code: String): String {
        val s = code.trim().uppercase()
        ANY_TASK.matchEntire(s)?.let { return "C${it.groupValues[1]} Task ${it.groupValues[2]}" }
        ANY_FORMATIVE.matchEntire(s)?.let { return "C${it.groupValues[1]} Formative" }
        ANY_SUMMATIVE.matchEntire(s)?.let { return "C${it.groupValues[1]} Summative" }
        return code
    }

    /**
     * Groups submissions per student of [className] (null/empty = every class), only
     * pending ones unless [filterAll]. Students sorted by number, tasks in teaching order.
     */
    fun groupSubmissions(rows: List<SubmissionRow>, className: String?, filterAll: Boolean): List<SubmissionGroup> {
        val byStudent = LinkedHashMap<String, MutableList<SubmissionRow>>()
        for (row in rows) {
            if (!className.isNullOrEmpty() && row.className != className) continue
            if (!filterAll && row.status != "pending") continue
            byStudent.getOrPut(row.className + "|" + row.studentNo) { mutableListOf() }.add(row)
        }
        return byStudent.values
            .map { items ->
                val first = items.first()
                SubmissionGroup(first.studentNo, first.studentName, first.className, items.sortedWith(TASK_ORDER))
            }
            .sortedBy { it.studentNo.trim().toDoubleOrNull() ?: Double.MAX_VALUE }
    }

    // Same order as teacher/submissions.html: tasks, then Formative, then Summative, per chapter.
    private fun taskSortKey(task: String): List<Int> {
        val s = task.uppercase()
        fun chapter(m: MatchResult) = m.groupValues[1].toIntOrNull() ?: 999
        ANY_TASK.matchEntire(s)?.let { return listOf(chapter(it), 0, it.groupValues[2].toIntOrNull() ?: 0) }
        ANY_FORMATIVE.matchEntire(s)?.let { return listOf(chapter(it), 1, 0) }
        ANY_SUMMATIVE.matchEntire(s)?.let { return listOf(chapter(it), 2, 0) }
        return listOf(999, 9, 0)
    }

    private val TASK_ORDER = Comparator<SubmissionRow> { a, b ->
        val ka = taskSortKey(a.task)
        val kb = taskSortKey(b.task)
        ka.indices.map { ka[it].compareTo(kb[it]) }.firstOrNull { it != 0 } ?: 0
    }

    /** A non-blank numeric string as a number, like core.js's `v !== '' && !isNaN(Number(v))`. */
    private fun number(v: String?): Double? = v?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { !it.isNaN() }

    /** JavaScript Math.round: halves round up (toward +infinity). */
    private fun jsRound(x: Double): Long = floor(x + 0.5).toLong()
}
