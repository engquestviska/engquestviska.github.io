package com.engquest.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun fixture(name: String): JsonObject =
    Json.parseToJsonElement(
        checkNotNull(DomainTest::class.java.getResource("/fixtures/$name.json")) { "no fixture $name" }.readText(),
    ).jsonObject

class DomainTest {
    @Test fun levelCurveMatchesWebsite() {
        assertEquals(LevelInfo(1, 0, 10), levelFromXp(0))
        assertEquals(1, levelFromXp(9).level)
        assertEquals(2, levelFromXp(10).level)
        assertEquals(3, levelFromXp(25).level)
        assertEquals(LevelInfo(21, 90, 110), levelFromXp(1240))
        assertEquals(LevelInfo(6, 19, 35), levelFromXp(119))
        assertEquals(LevelInfo(1, 0, 10), levelFromXp(-4))
    }

    @Test fun levelTitlesMatchWebsite() {
        assertEquals("Beginner", levelTitle(1))
        assertEquals("Explorer", levelTitle(2))
        assertEquals("Legend", levelTitle(6))
        assertEquals("Legend", levelTitle(21))
    }

    @Test fun gradesKnowTheirClasses() {
        assertEquals(Grade.G10, Grade.ofClass("xe2"))
        assertEquals(Grade.G11, Grade.ofClass("XIF9"))
        assertNull(Grade.ofClass("XYZ"))
        assertEquals(Grade.G11, Grade.of("11"))
    }

    @Test fun parsesEveryFixture() {
        val mates = Parse.classmates(fixture("getStudents"))
        assertEquals(36, mates.size)
        assertEquals(1, mates[0].no)

        val act = Parse.activeness(fixture("getAllActiveness")).first { it.no == 1 }
        assertEquals(119, act.total)
        assertEquals(3, act.answering)

        val ch = Parse.chapterScores(fixture("getChapterScores"))
        assertEquals(100.0, ch.rows.first { it.no == 1 }.scores["Task 1"])
        assertEquals(76.0, ch.rows.first { it.no == 1 }.finalScore)
        assertEquals(5, ch.columns.size)

        val fin = Parse.finalScores(fixture("getFinalScores")).rows.first { it.no == 1 }
        assertEquals("38", fin.calc["Final Score"])
        assertEquals("Not Safe", fin.calc["Indicator"])

        val att = Parse.classAttendance(fixture("getClassData"))
        assertEquals(21, att.meetings.size)
        assertEquals("Present", att.byStudent[1]?.get("m1"))

        assertEquals(false, Parse.taskStatus(fixture("getTaskStatus"))["C3T1"])
        assertEquals(listOf("Descriptive Text", "Recount", "Procedure"), Parse.unitNames(fixture("getUnitNames")))

        val subs = Parse.mySubmissions(fixture("getMySubmissions"))
        assertEquals("approved", subs.statuses["C1T1"])
        assertTrue(subs.items.isNotEmpty())

        assertEquals("pdf", Parse.materials(fixture("getMaterials"))[0].fileType)
        val vocab = Parse.vocabulary(fixture("getVocabulary"))
        assertEquals(30, vocab.weeklyCap)
        assertEquals(30, vocab.weeklyRemaining)
        assertEquals(0, Parse.strikeCount(fixture("getStudentStrikes")))
        assertNull(Parse.announcement(fixture("getAnnouncement")))
    }

    @Test fun oddValuesNeverCrash() {
        val j = Json.parseToJsonElement("""{"students":[{"no":"3","scores":{"Task 1":""},"average":"","finalScore":null}]}""").jsonObject
        val row = Parse.chapterScores(j).rows.single()
        assertEquals(3, row.no)
        assertNull(row.scores["Task 1"])
        assertNull(row.average)
        assertEquals(emptyList<Event>(), Parse.events(JsonObject(emptyMap())))
    }
}
