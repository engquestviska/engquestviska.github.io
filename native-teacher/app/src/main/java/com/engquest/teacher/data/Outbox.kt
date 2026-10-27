package com.engquest.teacher.data

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Teacher writes that wait in the phone's database until [OutboxWorker] gets them to the
 * server, oldest first. Each item gets its own rid when made; the server applies a rid at
 * most once, so a resend after a lost reply never doubles a write.
 *
 * [scope] runs [retry] / [discard] and watches [Session.user]: after a (re)login, unsent
 * items are scheduled again (the worker stops on an expired password and waits for this).
 */
class Outbox(
    private val db: AppDb,
    session: Session,
    private val work: WorkManager,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.outboxDao()

    /** Writes not yet on the server (waiting or on their way). */
    val pending: Flow<Int> = dao.observeUnsentCount()

    /** Writes the server refused; each waits for the teacher's Retry or Discard. */
    val failed: Flow<List<OutboxItem>> = dao.observeFailed()

    init {
        scope.launch {
            session.user.filterNotNull().collect { if (dao.countUnsent() > 0) schedule() }
        }
    }

    /** Saves the write for [className] and schedules sending; returns the item id. */
    suspend fun enqueue(action: String, className: String, params: Map<String, String>): Long {
        val now = clock()
        val stored = params.filterKeys { it !in NOT_STORED } +
            (if (action in TAKES_CLIENT_DATE) mapOf("clientDate" to localDate(now)) else emptyMap())
        val id = dao.insert(
            OutboxItem(
                rid = UUID.randomUUID().toString(),
                action = action,
                className = className,
                paramsJson = JsonObject(stored.mapValues { JsonPrimitive(it.value) }).toString(),
                createdAt = now,
            ),
        )
        schedule()
        return id
    }

    /** Puts a failed item back in line with its old rid. */
    fun retry(id: Long): Job = scope.launch {
        if (dao.requeueFailed(id) > 0) schedule()
    }

    /** Drops an item for good (unless it is being sent right now). */
    fun discard(id: Long): Job = scope.launch { dao.deleteUnlessSending(id) }

    /** Removes the item unless the worker is sending it right now (XP Undo). */
    suspend fun cancelIfPending(id: Long): Boolean = dao.deleteUnlessSending(id) == 1

    /** Log out: stops sending and drops every item, failed ones too. */
    suspend fun clear() {
        work.cancelUniqueWork(WORK_NAME)
        dao.deleteAll()
    }

    private fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // Append, not keep: a run that is just finishing may already have looked for items, so a
        // new item needs a run of its own after it. A run with nothing left ends at once, and
        // the atomic claim keeps two runs from sending the same item.
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun localDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    companion object {
        const val WORK_NAME = "outbox"

        /** Actions where the server needs the phone's own date (yyyy-MM-dd). */
        private val TAKES_CLIENT_DATE = setOf("addStrike", "reviewTaskSubmission", "addMaterial", "setAnnouncement")

        /** Login and routing fields are added at send time, never stored with the item. */
        private val NOT_STORED = setOf("username", "password", "rid", "action", "className")
    }
}
