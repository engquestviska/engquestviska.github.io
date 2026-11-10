package com.engquest.app.data

import android.content.SharedPreferences
import com.engquest.app.domain.Classmate
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile

/** Who uses this phone (class, absen number, name). No password: students pick their name. */
class ProfileStore(private val prefs: SharedPreferences) {
    fun get(): Profile? {
        val grade = Grade.of(prefs.getString(GRADE, null)) ?: return null
        val classId = prefs.getString(CLASS, null)?.takeIf { it.isNotBlank() } ?: return null
        val no = prefs.getInt(NO, 0).takeIf { it > 0 } ?: return null
        return Profile(grade, classId, no, prefs.getString(NAME, "").orEmpty())
    }

    fun save(p: Profile) {
        prefs.edit().putString(GRADE, p.grade.number).putString(CLASS, p.classId)
            .putInt(NO, p.studentNo).putString(NAME, p.name).apply()
    }

    fun clear() {
        prefs.edit().remove(GRADE).remove(CLASS).remove(NO).remove(NAME).apply()
    }

    private companion object {
        const val GRADE = "grade"
        const val CLASS = "classId"
        const val NO = "studentNo"
        const val NAME = "name"
    }
}

sealed interface RosterResult {
    data object Same : RosterResult
    data class Moved(val newNo: Int, val name: String) : RosterResult
    data object Gone : RosterResult
}

private fun sameName(a: String, b: String) = a.trim().uppercase() == b.trim().uppercase()

/**
 * Same rule as the website (shared/js/student.js checkRoster): the saved number still has the saved
 * name → nothing to do; the name is at another number → follow it; the name is gone → pick again.
 * An empty roster (no answer) changes nothing.
 */
fun checkRoster(p: Profile, roster: List<Classmate>): RosterResult {
    if (roster.isEmpty()) return RosterResult.Same
    val here = roster.firstOrNull { it.no == p.studentNo }
    if (here != null && sameName(here.name, p.name)) return RosterResult.Same
    val moved = roster.firstOrNull { sameName(it.name, p.name) } ?: return RosterResult.Gone
    return RosterResult.Moved(moved.no, moved.name)
}
