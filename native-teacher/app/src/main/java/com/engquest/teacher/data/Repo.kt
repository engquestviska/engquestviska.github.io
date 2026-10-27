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

    /**
     * Reads [action] for [className]; [chapter] only splits the cache key. Photos are always
     * fetched fresh and never saved here (too big for a Room row; Task 12 keeps its own cache).
     */
    fun load(
        action: String,
        className: String,
        params: Map<String, String> = emptyMap(),
        chapter: String? = null,
    ): Flow<Cached> = readFlow(cacheKey(action, className, chapter, params), store = action !in NOT_STORED) {
        api.read(action, withLogin(action, params + ("className" to className)))
    }

    /** Emits the saved entry for [key], then the result of [fetch]; re-fetches whenever the key's class drains. */
    fun cached(key: String, fetch: suspend () -> JsonObject): Flow<Cached> = readFlow(key, store = true, fetch)

    private fun readFlow(key: String, store: Boolean, fetch: suspend () -> JsonObject): Flow<Cached> = channelFlow {
        val className = key.split('|').getOrNull(1).orEmpty()
        val rereads = Channel<Unit>(Channel.CONFLATED)
        launch(start = CoroutineStart.UNDISPATCHED) {
            outboxDrained.filter { it == className }.collect { rereads.send(Unit) }
        }
        var current = if (store) stored(key) else Cached(null, null, refreshing = false, error = null)
        while (true) {
            send(current.copy(refreshing = true, error = null))
            current = refresh(key, className, current, store, fetch)
            send(current)
            rereads.receive()
        }
    }

    private suspend fun refresh(
        key: String,
        className: String,
        current: Cached,
        store: Boolean,
        fetch: suspend () -> JsonObject,
    ): Cached = try {
        val fresh = fetch()
        if (db.outboxDao().countUnsent(className) > 0) {
            current.copy(refreshing = false, error = null)
        } else {
            val now = clock()
            if (store) db.cacheDao().put(CacheEntry(key, fresh.toString(), now))
            Cached(fresh, now, refreshing = false, error = null)
        }
    } catch (e: ApiError) {
        // The saved password stopped working (changed on the website): ask for a new login.
        if (e is ApiError.Unauthorized) session.markExpired()
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
        private val NOT_STORED = setOf("getSubmissionPhoto")
        private val NOT_IN_KEY = setOf("action", "className", "chapter", "username", "password")

        /**
         * `action|className`, then `|chapter` and `|k=v&k=v` (other params, sorted by name) when
         * present, e.g. `getSubmissionPhoto|XE1||fileId=12`. Credentials never go in a key.
         */
        fun cacheKey(
            action: String,
            className: String,
            chapter: String? = null,
            params: Map<String, String> = emptyMap(),
        ): String {
            val extra = params.filterKeys { it !in NOT_IN_KEY }.toSortedMap()
                .entries.joinToString("&") { "${it.key}=${it.value}" }
            return when {
                extra.isNotEmpty() -> "$action|$className|${chapter.orEmpty()}|$extra"
                chapter != null -> "$action|$className|$chapter"
                else -> "$action|$className"
            }
        }
    }
}
