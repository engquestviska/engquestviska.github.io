package com.engquest.app.domain

/** Who is using this phone: saved after the student picks their class and name. */
data class Profile(val grade: Grade, val classId: String, val studentNo: Int, val name: String)

data class Classmate(val no: Int, val name: String, val nickname: String)

data class CustomAward(val reason: String, val points: Int)

/** One student's XP counters (from getAllActiveness). [total] is the server's, never recomputed. */
data class Activeness(
    val no: Int,
    val name: String,
    val vocabularyBox: Int,
    val answering: Int,
    val presenting: Int,
    val doingTask: Int,
    val helpingHand: Int,
    val quiz: Int,
    val custom: Int,
    val customAwards: List<CustomAward>,
    val total: Int,
    val indicator: String,
)

/** One student's row of one chapter (getChapterScores). Null score = not entered yet. */
data class ChapterRow(
    val no: Int,
    val scores: Map<String, Double?>,
    val average: Double?,
    val finalScore: Double?,
    val indicator: String,
)

data class ChapterScores(val columns: List<String>, val rows: List<ChapterRow>)

/** getFinalScores: typed inputs (ASAT) and computed columns (Chapter 1…, Average, Final Score, Indicator). */
data class FinalRow(val no: Int, val inputs: Map<String, Double?>, val calc: Map<String, String>)

data class FinalScores(val inputColumns: List<String>, val calcColumns: List<String>, val rows: List<FinalRow>)

data class Meeting(val number: Int, val date: String)

/** getClassData: the meetings and each student's status per meeting ("m1" -> "Present"). */
data class ClassAttendance(val meetings: List<Meeting>, val byStudent: Map<Int, Map<String, String>>)

data class MySubmission(val id: Long, val task: String, val status: String, val date: String, val reviewedAt: String)

data class MySubmissions(val statuses: Map<String, String>, val items: List<MySubmission>)

data class Material(val title: String, val link: String, val fileType: String, val addedAt: String)

data class VocabWord(val english: String, val indonesian: String, val type: String, val at: String, val status: String)

data class Vocabulary(
    val words: List<VocabWord>,
    val total: Int,
    val weekCount: Int,
    val weeklyCap: Int,
    val weeklyRemaining: Int,
    /** When the next word slot opens (epoch ms); 0 when a slot is free now. */
    val nextSlotAt: Long,
)

data class Announcement(val title: String, val body: String, val type: String, val audience: String)

/** One bell item (getMyEvents). [link] is an app route: send, levelxp, attendance, strikes, materials… */
data class Event(val id: Long, val kind: String, val title: String, val detail: String, val link: String, val at: String)
