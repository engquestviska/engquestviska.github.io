package com.engquest.teacher.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Submission photos. Each is asked for with the login as it is now, straight from the [Api]
 * (not through [Repo], which holds replies back while the class has unsent writes), shrunk so
 * its long side is at most [MAX_SIDE] px, and kept in [dir] so a photo seen once opens offline.
 * Everything heavy runs on [io], never on the main thread.
 *
 * The photo is a student's upload: its mime type is never trusted (the bytes decide), and bytes
 * that are not valid base64 or not a picture give null ("Photo can't be shown"), never a crash.
 */
class Photos(
    private val api: Api,
    private val session: Session,
    private val dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /**
     * The photo for [fileId], or null when what the server sent cannot be shown.
     * Throws [ApiError] when it could not be fetched (no internet, server busy, login refused).
     */
    suspend fun load(fileId: String): ImageBitmap? {
        memory.get(fileId)?.let { return it }
        return withContext(io) {
            val file = File(dir, fileName(fileId))
            val saved = runCatching { if (file.isFile) decode(file.readBytes()) else null }.getOrNull()
            val bitmap = saved ?: fetch(fileId)?.also { save(file, it) }
            bitmap?.asImageBitmap()?.also { memory.put(fileId, it) }
        }
    }

    private suspend fun fetch(fileId: String): Bitmap? {
        val creds = session.creds() ?: throw ApiError.Unauthorized()
        val reply = try {
            api.read(
                "getSubmissionPhoto",
                mapOf("fileId" to fileId, "username" to creds.username, "password" to creds.password),
            )
        } catch (e: ApiError.Unauthorized) {
            session.markExpired()
            throw e
        }
        val bytes = fromBase64((reply["dataBase64"] as? JsonPrimitive)?.contentOrNull) ?: return null
        return decode(bytes)
    }

    /** Keeps the shrunk picture (not the upload) so the disk copy is small and known to open. */
    private fun save(file: File, bitmap: Bitmap) {
        runCatching {
            dir.mkdirs()
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeBytes(out.toByteArray())
            if (!tmp.renameTo(file)) tmp.delete()
            trim()
        }
    }

    /** Keeps the folder to the newest [MAX_FILES] photos. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    companion object {
        const val MAX_SIDE = 2048
        private const val MAX_FILES = 300
        private const val MEMORY_BYTES = 48 * 1024 * 1024
        private val SAFE_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")
        private val BASE64 = Regex("^[A-Za-z0-9+/=\\s]+$")

        /** A file name made only from safe characters, so an id can never point outside [dir]. */
        internal fun fileName(fileId: String): String =
            if (SAFE_NAME.matches(fileId)) {
                fileId
            } else {
                MessageDigest.getInstance("SHA-256").digest(fileId.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }

        /** Android's decoder quietly skips stray characters, so anything outside base64 is refused here first. */
        internal fun fromBase64(data: String?): ByteArray? {
            if (data.isNullOrEmpty() || !BASE64.matches(data)) return null
            return try {
                Base64.decode(data, Base64.DEFAULT).takeIf { it.isNotEmpty() }
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /**
         * Decodes [bytes] with the long side at most [maxSide]: the size is read first, then the
         * picture is decoded at 1/2, 1/4… of it, so a huge photo never needs its full size in memory.
         */
        internal fun decode(bytes: ByteArray, maxSide: Int = MAX_SIDE): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (maxOf(w, h) / sample > maxSide) sample *= 2
            val bitmap = try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (e: OutOfMemoryError) {
                null
            } ?: return null
            val long = maxOf(bitmap.width, bitmap.height)
            if (long <= maxSide) return bitmap
            // A decoder that ignored the sample size: scale down here instead.
            val scale = maxSide.toFloat() / long
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            return scaled
        }
    }
}
