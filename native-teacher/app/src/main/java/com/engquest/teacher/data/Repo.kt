package com.engquest.teacher.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** What a screen shows: the saved reply (if any), whether a fresh one is loading, and any problem. */
data class Cached(
    val json: JsonObject?,
    val savedAt: Long?,
    val refreshing: Boolean,
    val error: String?,
)

/**
 * Screen data: saved copy first, then the server's. While the outbox still holds unsent writes
 * for a class, a server reply for that class is older than what the teacher just did, so it is
 * neither shown nor saved; the screen re-reads once the outbox drains for that class.
 */
class Repo(
    private val api: Api,
    private val session: Session,
    private val db: AppDb,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val drained = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Class names whose outbox just became empty (emitted by the outbox worker). */
    val outboxDrained: SharedFlow<String> = drained.asSharedFlow()

    suspend fun notifyDrained(className: String) = drained.emit(className)

    /** Reads [action] for [className]; [chapter] only splits the cache key. */
    fun load(
        action: String,
        className: String,
        params: Map<String, String> = emptyMap(),
        chapter: String? = null,
    ): Flow<Cached> = cached(cacheKey(action, className, chapter)) {
        api.read(action, withLogin(action, params + ("className" to className)))
    }

    /** Emits the saved entry for [key], then the result of [fetch]; re-fetches whenever the key's class drains. */
    fun cached(key: String, fetch: suspend () -> JsonObject): Flow<Cached> = channelFlow {
        val className = key.split('|').getOrNull(1).orEmpty()
        val rereads = Channel<Unit>(Channel.CONFLATED)
        launch(start = CoroutineStart.UNDISPATCHED) {
            outboxDrained.filter { it == className }.collect { rereads.send(Unit) }
        }
        var current = stored(key)
        while (true) {
            send(current.copy(refreshing = true, error = null))
            current = refresh(key, className, current, fetch)
            send(current)
            rereads.receive()
        }
    }

    private suspend fun refresh(
        key: String,
        className: String,
        current: Cached,
        fetch: suspend () -> JsonObject,
    ): Cached = try {
        val fresh = fetch()
        if (db.outboxDao().countUnsent(className) > 0) {
            current.copy(refreshing = false, error = null)
        } else {
            val now = clock()
            db.cacheDao().put(CacheEntry(key, fresh.toString(), now))
            Cached(fresh, now, refreshing = false, error = null)
        }
    } catch (e: ApiError) {
        current.copy(refreshing = false, error = e.userMessage)
    }

    private suspend fun stored(key: String): Cached {
        val entry = db.cacheDao().get(key) ?: return Cached(null, null, refreshing = false, error = null)
        val json = runCatching { Json.parseToJsonElement(entry.json) as? JsonObject }.getOrNull()
        return Cached(json, if (json != null) entry.savedAt else null, refreshing = false, error = null)
    }

    private fun withLogin(action: String, params: Map<String, String>): Map<String, String> {
        if (action !in NEEDS_LOGIN) return params
        val creds = session.creds() ?: return params
        return params + mapOf("username" to creds.username, "password" to creds.password)
    }

    companion object {
        private val NEEDS_LOGIN = setOf("getTaskSubmissions", "getSubmissionPhoto")

        fun cacheKey(action: String, className: String, chapter: String? = null): String =
            if (chapter == null) "$action|$className" else "$action|$className|$chapter"
    }
}
