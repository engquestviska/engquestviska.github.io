package com.engquest.teacher.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardTest {
    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val xe1Final = json("""{"students":[
        {"no":1,"name":"Ani","calc":{"Final Score":80}},
        {"no":2,"name":"Budi","calc":{"Final Score":92}},
        {"no":3,"name":"Citra","calc":{"Final Score":""}}]}""")
    private val xe2Final = json("""{"students":[
        {"no":1,"name":"Dewi","calc":{"Final Score":92}},
        {"no":2,"name":"Eka","calc":{"Final Score":70}}]}""")

    @Test fun scoresRankAcrossClassesBestFirstAndTiesSharePlace() {
        val rows = Leaderboard.scores(mapOf("XE1" to xe1Final, "XE2" to xe2Final))
        assertEquals(listOf("Budi", "Dewi", "Ani", "Eka"), rows.map { it.name })
        assertEquals(listOf(1, 1, 3, 4), rows.map { it.rank })
        assertEquals(listOf("XE1", "XE2", "XE1", "XE2"), rows.map { it.classId })
        assertEquals("92", rows[0].value)
    }

    @Test fun aStudentWithNoFinalScoreIsLeftOut() {
        val rows = Leaderboard.scores(mapOf("XE1" to xe1Final))
        assertEquals(listOf("Budi", "Ani"), rows.map { it.name })
    }

    @Test fun levelsRankByTotalXpWithTheLevel() {
        val act = json("""{"students":[
            {"no":1,"name":"Ani","Total":119},
            {"no":2,"name":"Budi","Total":10},
            {"no":3,"name":"Citra","Total":119},
            {"no":4,"name":"Dodi","Total":0}]}""")
        val rows = Leaderboard.levels(mapOf("XIF7" to act))
        assertEquals(listOf("Ani", "Citra", "Budi", "Dodi"), rows.map { it.name })
        assertEquals(listOf(1, 1, 3, 4), rows.map { it.rank })
        assertEquals("Level 6 · 119 XP", rows[0].value)
        assertEquals("Level 1 · 0 XP", rows[3].value)
    }

    @Test fun levelCurveMatchesTheStudentApp() {
        assertEquals(1, Leaderboard.level(0))
        assertEquals(2, Leaderboard.level(10))
        assertEquals(6, Leaderboard.level(119))
        assertEquals(21, Leaderboard.level(1240))
    }
}
