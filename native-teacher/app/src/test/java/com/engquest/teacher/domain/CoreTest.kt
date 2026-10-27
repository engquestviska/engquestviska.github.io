package com.engquest.teacher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported case by case from teacher-app/tests/core.test.mjs.
class CoreTest {

    @Test
    fun taskToChapterCol_mapsTaskCodesToBackendChapterPlus3() {
        assertEquals(ChapterCol("4", "Task 2"), Core.taskToChapterCol("C1T2"))
        assertEquals(ChapterCol("6", "Formative"), Core.taskToChapterCol("c3f"))
        assertEquals(ChapterCol("5", "Summative"), Core.taskToChapterCol("C2S"))
    }

    @Test
    fun taskToChapterCol_returnsNullForCodesItCannotMap() {
        assertNull(Core.taskToChapterCol("XYZ"))
        assertNull(Core.taskToChapterCol(""))
        assertNull(Core.taskToChapterCol(null))
    }

    @Test
    fun taskToChapterCol_onlyMapsChapters1to3AndTasks1to3() {
        assertNull(Core.taskToChapterCol("C1T4"))
        assertNull(Core.taskToChapterCol("C4T1"))
        assertNull(Core.taskToChapterCol("C0F"))
        assertEquals(ChapterCol("6", "Task 3"), Core.taskToChapterCol("C3T3"))
    }

    // core.js photoSrc builds a data URL; the native app only needs its mime whitelist.
    @Test
    fun safeMime_onlyKeepsWhitelistedImageTypes() {
        assertEquals("image/png", Core.safeMime("image/png"))
        assertEquals("image/jpeg", Core.safeMime("image/png\" onerror=\"alert(1)"))
        assertEquals("image/jpeg", Core.safeMime(null))
        assertEquals("image/heic", Core.safeMime("IMAGE/HEIC"))
        assertEquals("image/jpeg", Core.safeMime("text/html"))
    }

    @Test
    fun finalScore_averagesChapterFinalsAndExamLikeTheBackend() {
        assertEquals(
            FinalCalc(80.0, 80, "Safe"),
            Core.finalScore(mapOf("Chapter 1" to "80", "Chapter 2" to "", "Chapter 3" to "90"), "70"),
        )
        assertEquals(
            FinalCalc(85.5, 86, "Safe"),
            Core.finalScore(mapOf("Chapter 1" to "80", "Chapter 2" to "91"), ""),
        )
        assertEquals(
            FinalCalc(65.0, 65, "Not Safe"),
            Core.finalScore(mapOf("Chapter 1" to "60"), "70"),
        )
        assertEquals(FinalCalc(null, null, ""), Core.finalScore(emptyMap(), ""))
    }

    @Test
    fun colLabel_showsSemester1ExamAsASAS() {
        assertEquals("ASAS", Core.colLabel("ASAT"))
        assertEquals("Task 1", Core.colLabel("Task 1"))
    }

    @Test
    fun validateScore_acceptsEmptyOr0to100AndTrims() {
        assertTrue(Core.validateScore("85").ok)
        assertTrue(Core.validateScore("").ok)
        assertEquals("70", Core.validateScore(" 70 ").value)
        assertFalse(Core.validateScore("101").ok)
        assertFalse(Core.validateScore("-1").ok)
        assertFalse(Core.validateScore("abc").ok)
    }

    @Test
    fun isLowScore_flagsNonEmptyScoresUnder75() {
        assertTrue(Core.isLowScore("74"))
        assertFalse(Core.isLowScore("75"))
        assertFalse(Core.isLowScore(""))
    }

    private val students = listOf(
        Student(no = "1", name = "Adit", nickname = "", attendance = mapOf("m3" to "Sick")),
        Student(no = "2", name = "Bella", nickname = "", attendance = emptyMap()),
    )

    @Test
    fun attendanceInitial_startsEveryonePresentWhenTheDateHasNoMeeting() {
        val (meeting, marks) = Core.attendanceInitial(students, listOf(Meeting(3, "2026-10-20")), "2026-10-26")
        assertNull(meeting)
        assertEquals(mapOf("1" to "Present", "2" to "Present"), marks)
    }

    @Test
    fun attendanceInitial_loadsStoredMarksForAnExistingMeeting() {
        val (meeting, marks) = Core.attendanceInitial(students, listOf(Meeting(3, "2026-10-26")), "2026-10-26")
        assertEquals(3, meeting?.number)
        assertEquals(mapOf("1" to "Sick", "2" to "Present"), marks)
    }

    @Test
    fun attendanceCounts_attendanceDirty_andAttendanceRecords() {
        val initial = mapOf("1" to "Present", "2" to "Present")
        val marks = mapOf("1" to "Absent", "2" to "Present")
        assertEquals(
            mapOf("Present" to 1, "Absent" to 1, "Sick" to 0, "Permit" to 0),
            Core.attendanceCounts(marks),
        )
        assertFalse(Core.attendanceDirty(initial, initial.toMap()))
        assertTrue(Core.attendanceDirty(initial, marks))
        assertEquals(listOf("1" to "Absent", "2" to "Present"), Core.attendanceRecords(students, marks))
    }

    @Test
    fun initialsAndFirstName() {
        assertEquals("AP", Core.initials("Adit Pratama"))
        assertEquals("EQ", Core.initials(""))
        assertEquals("Adit", Core.firstName("Adit Pratama"))
    }

    @Test
    fun groupSubmissions_filtersByClassAndStatusAndSortsTasks() {
        fun row(subId: String, cls: String, no: String, name: String, task: String, status: String) =
            SubmissionRow(subId, cls, no, name, task, timestamp = "", status = status)
        val rows = listOf(
            row("a", "XE1", "2", "B", "C2T1", "pending"),
            row("b", "XE1", "2", "B", "C1S", "pending"),
            row("c", "XE1", "2", "B", "C1T2", "pending"),
            row("d", "XE1", "2", "B", "C1F", "pending"),
            row("e", "XE1", "2", "B", "C1T1", "pending"),
            row("f", "XE1", "1", "A", "C1T1", "reviewed"),
            row("g", "XE2", "1", "Z", "C1T1", "pending"),
        )
        val groups = Core.groupSubmissions(rows, className = "XE1", filterAll = false)
        assertEquals(1, groups.size)
        assertEquals(listOf("C1T1", "C1T2", "C1F", "C1S", "C2T1"), groups[0].items.map { it.task })
        val all = Core.groupSubmissions(rows, className = "XE1", filterAll = true)
        assertEquals(listOf("1", "2"), all.map { it.studentNo })
    }

    @Test
    fun taskLabel_spellsOutTaskCodes() {
        assertEquals("C1 Task 2", Core.taskLabel("C1T2"))
        assertEquals("C2 Formative", Core.taskLabel("C2F"))
        assertEquals("C3 Summative", Core.taskLabel("C3S"))
        assertEquals("ZZ9", Core.taskLabel("ZZ9"))
    }

    @Test
    fun classes_hasAllEightClasses() {
        assertEquals(8, Classes.ALL.size)
        assertEquals(ClassInfo("XE1", "X E-1", "10"), Classes.ALL.first())
        assertEquals(ClassInfo("XIF9", "XI F-9", "11"), Classes.ALL.last())
    }
}
