package com.engquest.teacher.data

/**
 * Clears one student's EngQuest Chronicle PIN so they can make a new one. Online only (no outbox):
 * the teacher sees straight away whether it worked. Returns null on success, else a message.
 */
class PinReset(private val api: Api, private val creds: () -> Creds?) {
    suspend fun reset(className: String, studentNo: String): String? {
        val no = studentNo.trim()
        if (no.toIntOrNull() == null) return "Type the student's number."
        val login = creds() ?: return "Please log in again."
        return try {
            val reply = api.write(
                "resetChroniclePin",
                mapOf("className" to className, "studentNo" to no, "username" to login.username, "password" to login.password),
            )
            if (reply["success"] != null) null else "Couldn't reset the PIN. Try again."
        } catch (e: ApiError) {
            if (e.message == "Student not found") "No student with that number in this class." else e.userMessage
        }
    }
}
