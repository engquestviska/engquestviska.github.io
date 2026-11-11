package com.engquest.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Readers for the server's replies. Missing or odd fields become empty values, never a crash. */
object Parse {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.arr(k: String) = (this[k] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.contentOrNull ?: ""
    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.let { p ->
        if (p.isString && p.content.isBlank()) null else p.doubleOrNull ?: p.content.trim().toDoubleOrNull()
    }
    private fun JsonElement?.int(): Int = num()?.toInt() ?: 0
    private fun JsonObject.s(k: String) = this[k].str()
    private fun JsonObject.i(k: String) = this[k].int()
    private fun JsonObject.strings(k: String) = (this[k] as? JsonArray).orEmpty().map { it.str() }

    fun classmates(j: JsonObject) = j.arr("students").map { Classmate(it.i("no"), it.s("name"), it.s("nickname")) }

    fun activeness(j: JsonObject) = j.arr("students").map { s ->
        Activeness(
            no = s.i("no"), name = s.s("name"),
            vocabularyBox = s.i("VocabularyBox"), answering = s.i("Answering"), presenting = s.i("Presenting"),
            doingTask = s.i("DoingTask"), helpingHand = s.i("HelpingHand"), quiz = s.i("Quiz"),
            custom = s.i("Custom"),
            customAwards = s.arr("CustomAwards").map { CustomAward(it.s("reason"), it.i("points")) },
            total = s.i("Total"), indicator = s.s("Indicator"),
        )
    }

    fun chapterScores(j: JsonObject) = ChapterScores(
        columns = j.strings("inputCols"),
        rows = j.arr("students").map { s ->
            ChapterRow(
                no = s.i("no"),
                scores = s.obj("scores").mapValues { it.value.num() },
                average = s["average"].num(), finalScore = s["finalScore"].num(), indicator = s.s("indicator"),
                name = s.s("name"),
            )
        },
    )

    fun finalScores(j: JsonObject) = FinalScores(
        inputColumns = j.strings("inputCols"),
        calcColumns = j.strings("calcCols"),
        rows = j.arr("students").map { s ->
            FinalRow(s.i("no"), s.obj("scores").mapValues { it.value.num() }, s.obj("calc").mapValues { it.value.str() })
        },
    )

    fun classAttendance(j: JsonObject) = ClassAttendance(
        meetings = j.arr("meetings").map { Meeting(it.i("number"), it.s("date")) },
        byStudent = j.arr("students").associate { s -> s.i("no") to s.obj("attendance").mapValues { it.value.str() } },
    )

    fun taskStatus(j: JsonObject): Map<String, Boolean> =
        j.obj("tasks").mapValues { (it.value as? JsonPrimitive)?.booleanOrNull ?: false }

    fun unitNames(j: JsonObject) = j.strings("names")

    fun mySubmissions(j: JsonObject) = MySubmissions(
        statuses = j.obj("statuses").mapValues { it.value.str() },
        items = j.arr("items").map {
            MySubmission(it["id"].num()?.toLong() ?: 0, it.s("task"), it.s("status"), it.s("date"), it.s("reviewedAt"))
        },
    )

    fun materials(j: JsonObject) = j.arr("materials").map { Material(it.s("title"), it.s("link"), it.s("fileType"), it.s("addedAt")) }

    fun vocabulary(j: JsonObject) = Vocabulary(
        words = j.arr("words").map { VocabWord(it.s("english"), it.s("indonesian"), it.s("type"), it.s("at"), it.s("status")) },
        total = j.i("total"), weekCount = j.i("weekCount"), weeklyCap = j.i("weeklyCap").takeIf { it > 0 } ?: 30,
        weeklyRemaining = j.i("weeklyRemaining"), nextSlotAt = j["nextSlotAt"].num()?.toLong() ?: 0,
    )

    fun strikeCount(j: JsonObject) = j.i("total")

    /** Null when there is no announcement (the server sends {}). */
    fun announcement(j: JsonObject): Announcement? =
        if (j.s("title").isBlank() && j.s("body").isBlank()) null
        else Announcement(j.s("title"), j.s("body"), j.s("type").ifBlank { "info" }, j.s("audience").ifBlank { "both" })

    fun events(j: JsonObject) = j.arr("events").map {
        Event(it["id"].num()?.toLong() ?: 0, it.s("kind"), it.s("title"), it.s("detail"), it.s("link"), it.s("at"))
    }
}
