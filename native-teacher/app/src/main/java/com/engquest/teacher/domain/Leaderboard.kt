package com.engquest.teacher.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One line of a leaderboard: [value] is what the teacher reads ("92" or "Level 6 · 119 XP"). */
data class RankRow(val rank: Int, val name: String, val classId: String, val no: String, val value: String)

/**
 * Leaderboards built from each class's own replies, so a whole grade is just its classes put
 * together: Score = getFinalScores "Final Score"; Level = getAllActiveness "Total" XP.
 * Equal values share a place (1, 1, 3), the same rule as the students' Class Rank.
 */
object Leaderboard {
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun students(j: JsonObject) = (j["students"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private data class Entry(val name: String, val classId: String, val no: String, val sort: Double, val value: String)

    private fun rank(entries: List<Entry>): List<RankRow> {
        val sorted = entries.sortedWith(compareByDescending<Entry> { it.sort }.thenBy { it.name })
        var place = 0
        return sorted.mapIndexed { i, e ->
            if (i == 0 || e.sort != sorted[i - 1].sort) place = i + 1
            RankRow(place, e.name, e.classId, e.no, e.value)
        }
    }

    private fun num(d: Double) = if (d == Math.floor(d)) d.toInt().toString() else d.toString()

    /** Final Score, best first; students without a Final Score yet are left out. */
    fun scores(byClass: Map<String, JsonObject>): List<RankRow> = rank(
        byClass.flatMap { (cls, j) ->
            students(j).mapNotNull { s ->
                val calc = s["calc"] as? JsonObject
                val v = calc?.get("Final Score").text()?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
                Entry(s["name"].text().orEmpty(), cls, s["no"].text().orEmpty(), v, num(v))
            }
        },
    )

    /** Total XP, most first, shown with its level. */
    fun levels(byClass: Map<String, JsonObject>): List<RankRow> = rank(
        byClass.flatMap { (cls, j) ->
            students(j).map { s ->
                val xp = s["Total"].text()?.toDoubleOrNull()?.toInt() ?: 0
                Entry(s["name"].text().orEmpty(), cls, s["no"].text().orEmpty(), xp.toDouble(), "Level ${level(xp)} · $xp XP")
            }
        },
    )

    /** Same curve as the website and the student app: level N→N+1 needs 5 + 5N XP. */
    fun level(xp: Int): Int {
        var level = 1
        var need = 10
        var floor = 0
        while (xp >= floor + need) {
            floor += need
            level++
            need += 5
        }
        return level
    }
}
