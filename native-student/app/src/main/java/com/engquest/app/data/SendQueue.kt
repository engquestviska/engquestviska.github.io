package com.engquest.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import com.engquest.app.domain.Profile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Most photos per task, the same limit as the website and the server. */
const val MAX_PHOTOS = 10

/**
 * Task photo sends that survive no signal and app restarts. Each send keeps one [PhotoSend.rid]
 * for life, so a resend after a lost reply is replayed by the server, never saved twice.
 * [kick] asks the background job (WorkManager) to run [drain] when there is a connection.
 */
class SendQueue(
    private val db: AppDb,
    private val api: Api,
    /** Where compressed photos wait until they are sent. */
    private val dir: File,
    private val kick: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.photoSendDao()

    fun observe() = dao.observeAll()

    /** Queues [photos] (already compressed files) for [taskKey]; they are moved into the queue's folder. */
    suspend fun enqueue(p: Profile, taskKey: String, photos: List<File>) {
        val rid = UUID.randomUUID().toString()
        dir.mkdirs()
        val kept = photos.mapIndexed { i, f ->
            val dest = File(dir, "send_${rid}_$i.jpg")
            if (!f.renameTo(dest)) { f.copyTo(dest, overwrite = true); f.delete() }
            dest.absolutePath
        }
        dao.insert(
            PhotoSend(
                rid = rid, classId = p.classId, studentNo = p.studentNo, taskKey = taskKey,
                photoPaths = JsonArray(kept.map { JsonPrimitive(it) }).toString(), createdAt = clock(),
            ),
        )
        kick()
    }

    /**
     * Sends everything waiting. Returns false when it stopped because there is no signal or the
     * server is busy (try again later), true when nothing is left to try now.
     */
    suspend fun drain(): Boolean {
        while (true) {
            val item = dao.oldestPending() ?: return true
            if (dao.claim(item.id) == 0) continue
            val paths = Json.parseToJsonElement(item.photoPaths).jsonArray.map { it.jsonPrimitive.content }
            val images = buildJsonArray {
                paths.map(::File).filter { it.exists() }.forEach { add(JsonPrimitive(Base64.encodeToString(it.readBytes(), Base64.NO_WRAP))) }
            }
            try {
                api.writeJson(
                    "submitTaskPhoto",
                    JsonObject(
                        mapOf(
                            "className" to JsonPrimitive(item.classId),
                            "studentNo" to JsonPrimitive(item.studentNo.toString()),
                            "taskKey" to JsonPrimitive(item.taskKey),
                            "images" to images,
                            "mimeType" to JsonPrimitive("image/jpeg"),
                            "rid" to JsonPrimitive(item.rid),
                        ),
                    ),
                )
                dao.delete(item.id)
                paths.forEach { File(it).delete() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopped mid-upload (signal lost, job cancelled): back in line with the same rid.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { dao.setState(item.id, SendState.PENDING, null, item.attempts) }
                throw e
            } catch (e: ApiError) {
                when (e) {
                    is ApiError.Offline, is ApiError.RetryLater -> {
                        dao.setState(item.id, SendState.PENDING, null, item.attempts + 1)
                        return false
                    }
                    else -> dao.setState(item.id, SendState.FAILED, e.userMessage, item.attempts + 1)
                }
            }
        }
    }

    /** "Retry" on a send the server refused. */
    suspend fun retry(id: Long) {
        val item = dao.get(id) ?: return
        dao.setState(id, SendState.PENDING, null, 0)
        if (item.state == SendState.FAILED) kick()
    }

    /** "Remove" on a waiting or refused send (not one going out right now). */
    suspend fun remove(id: Long) {
        val item = dao.get(id) ?: return
        if (item.state == SendState.SENDING) return
        dao.delete(id)
        Json.parseToJsonElement(item.photoPaths).jsonArray.forEach { File(it.jsonPrimitive.content).delete() }
    }

    /** At app start: a send cut off by the app closing goes back in line (same rid). */
    suspend fun recover() {
        dao.resetSending()
        kick()
    }

    suspend fun renumber(classId: String, oldNo: Int, newNo: Int) = dao.renumber(classId, oldNo, newNo)

    suspend fun clear() {
        dao.all().forEach { remove(it.id) }
    }
}

/** The photos a student has picked but not sent yet (max [MAX_PHOTOS]). Items are Uri strings. */
class PhotoPicks {
    val items = mutableStateListOf<String>()
    val full: Boolean get() = items.size >= MAX_PHOTOS

    /** Adds what fits; returns how many were left out because of the limit. */
    fun add(uris: List<String>): Int {
        val room = (MAX_PHOTOS - items.size).coerceAtLeast(0)
        val fresh = uris.filter { it !in items }
        items.addAll(fresh.take(room))
        return (fresh.size - room).coerceAtLeast(0)
    }

    fun remove(uri: String) { items.remove(uri) }
    fun clear() = items.clear()
}

/** The size a photo is scaled to so its longest side is at most [max] (never scaled up). */
fun shrinkTo(w: Int, h: Int, max: Int): Pair<Int, Int> {
    if (w <= max && h <= max) return w to h
    return if (w >= h) max to (h * max.toLong() / w).toInt() else (w * max.toLong() / h).toInt() to max
}

/**
 * Reads the photo at [uri], scales it to 1000 px on its longest side and saves it as JPEG
 * quality 50 (the website's settings) in [out]. Returns false when the photo cannot be read.
 */
fun compressPhoto(context: Context, uri: Uri, out: File): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1000 && bounds.outHeight / (sample * 2) >= 1000) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return false
    // Re-saving as JPEG drops the orientation tag, so turn the photo upright first.
    val degrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    val (w, h) = shrinkTo(bmp.width, bmp.height, 1000)
    val sized = if (w == bmp.width && h == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
    val scaled = if (degrees == 0f) sized
        else Bitmap.createBitmap(sized, 0, 0, sized.width, sized.height, android.graphics.Matrix().apply { postRotate(degrees) }, true)
            .also { if (sized !== bmp) sized.recycle() }
    out.parentFile?.mkdirs()
    out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 50, it) }
    if (scaled !== bmp) scaled.recycle()
    bmp.recycle()
    true
}.getOrDefault(false)
