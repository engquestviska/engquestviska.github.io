package com.engquest.app.data

import com.engquest.app.domain.Grade
import com.engquest.app.domain.Parse
import com.engquest.app.domain.Profile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A reply plus where it came from: [fromCache] means no signal, showing what was saved at [savedAt]. */
data class Fresh<T>(val value: T, val fromCache: Boolean, val savedAt: Long)

/**
 * Every read the app makes. Network first; with no signal (or a busy server) the last saved reply
 * for the same question is used instead, so screens still show something offline.
 */
class Repo(
    private val api: Api,
    private val db: AppDb,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun <T> cached(action: String, params: Map<String, String>, parse: (JsonObject) -> T): Fresh<T> {
        val key = action + "?" + params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        return try {
            val json = api.read(action, params)
            val now = clock()
            db.cacheDao().put(CacheEntry(key, json.toString(), now))
            Fresh(parse(json), fromCache = false, savedAt = now)
        } catch (e: ApiError) {
            if (e !is ApiError.Offline && e !is ApiError.RetryLater) throw e
            val saved = db.cacheDao().get(key) ?: throw e
            Fresh(parse(Json.parseToJsonElement(saved.json).jsonObject), fromCache = true, savedAt = saved.savedAt)
        }
    }

    private fun me(p: Profile) = mapOf("className" to p.classId, "studentNo" to p.studentNo.toString())

    suspend fun classmates(classId: String) = cached("getStudents", mapOf("className" to classId), Parse::classmates)
    suspend fun activeness(classId: String) = cached("getAllActiveness", mapOf("className" to classId), Parse::activeness)
    suspend fun chapterScores(classId: String, chapter: Int) =
        cached("getChapterScores", mapOf("className" to classId, "chapter" to chapter.toString()), Parse::chapterScores)
    suspend fun finalScores(classId: String) = cached("getFinalScores", mapOf("className" to classId), Parse::finalScores)
    suspend fun classAttendance(p: Profile) = cached("getClassData", me(p), Parse::classAttendance)
    suspend fun taskStatus(p: Profile) =
        cached("getTaskStatus", me(p) + ("grade" to p.grade.number), Parse::taskStatus)
    suspend fun unitNames(grade: Grade) = cached("getUnitNames", mapOf("grade" to grade.number), Parse::unitNames)
    suspend fun mySubmissions(p: Profile) = cached("getMySubmissions", me(p), Parse::mySubmissions)
    /** [chapter] is "1".."3" for materials, "assign1".. for assignments, "reading" for Reading. */
    suspend fun materials(grade: Grade, chapter: String) =
        cached("getMaterials", mapOf("grade" to grade.number, "chapter" to chapter), Parse::materials)
    suspend fun vocabulary(p: Profile) = cached("getVocabulary", me(p), Parse::vocabulary)
    suspend fun strikeCount(p: Profile) =
        cached("getStudentStrikes", me(p) + ("grade" to p.grade.number), Parse::strikeCount)
    suspend fun announcement(p: Profile) = cached("getAnnouncement", mapOf("className" to p.classId), Parse::announcement)
    suspend fun myEvents(p: Profile, beforeId: Long) =
        cached("getMyEvents", me(p) + (if (beforeId > 0) mapOf("beforeId" to beforeId.toString()) else emptyMap()), Parse::events)

    /** Writes go straight to the server (no cache); they throw [ApiError] when they could not be saved. */
    suspend fun write(action: String, params: Map<String, String>): JsonObject = api.write(action, params)

    suspend fun forgetAll() = db.cacheDao().clear()
}
