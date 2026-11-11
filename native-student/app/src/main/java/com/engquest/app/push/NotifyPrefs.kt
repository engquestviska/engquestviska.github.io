package com.engquest.app.push

import android.content.SharedPreferences

/** The 8 kinds of news a student can get a buzz for, with their Settings labels. */
val NOTIFY_KINDS = listOf(
    "approved" to "Task approved",
    "sent_back" to "Task sent back",
    "xp" to "XP from teacher",
    "level" to "New level",
    "attendance" to "Attendance",
    "strike" to "Strikes",
    "material" to "New materials",
    "announcement" to "Announcements",
)

/**
 * The student's notification switches, kept on the phone and sent to the server (which only
 * sends the kinds that are on). Off until the student says yes.
 */
class NotifyPrefs(private val prefs: SharedPreferences) {
    var on: Boolean
        get() = prefs.getBoolean(KEY_ON, false)
        set(v) { prefs.edit().putBoolean(KEY_ON, v).apply() }

    /** True once the student answered the "Want a buzz…?" question (never asked again). */
    var asked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(v) { prefs.edit().putBoolean(KEY_ASKED, v).apply() }

    fun wants(kind: String) = prefs.getBoolean(KEY_KIND + kind, true)
    fun set(kind: String, value: Boolean) { prefs.edit().putBoolean(KEY_KIND + kind, value).apply() }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)
    fun saveToken(t: String) { prefs.edit().putString(KEY_TOKEN, t).apply() }

    /** What the server stores: {"on":…, and one true/false per kind}. */
    fun json(): String = buildString {
        append("{\"on\":").append(on)
        NOTIFY_KINDS.forEach { (k, _) -> append(",\"").append(k).append("\":").append(wants(k)) }
        append('}')
    }

    private companion object {
        const val KEY_ON = "notifyOn"
        const val KEY_ASKED = "notifyAsked"
        const val KEY_KIND = "notify_"
        const val KEY_TOKEN = "pushToken"
    }
}
