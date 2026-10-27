package com.engquest.teacher.data

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * The app came to the front or the internet came back: a run that is only waiting (out a
     * retry pause, or for a network) is replaced by one that starts as soon as it can. A run
     * that is sending right now is left alone.
     */
    fun sendNow(): Job = scope.launch {
        if (dao.countUnsent() == 0) return@launch
        val runs = withContext(Dispatchers.IO) { work.getWorkInfosForUniqueWork(WORK_NAME).get() }
        if (runs.any { it.state == WorkInfo.State.RUNNING }) return@launch
        if (runs.none { it.state == WorkInfo.State.ENQUEUED }) return@launch
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, sendRequest())
    }

    /** Log out: stops sending and drops every item, failed ones too. */
    suspend fun clear() {
        work.cancelUniqueWork(WORK_NAME)
        dao.deleteAll()
    }

    private fun schedule() {
        // Append, not keep: a run that is just finishing may already have looked for items, so a
        // new item needs a run of its own after it. A run with nothing left ends at once, and
        // the atomic claim keeps two runs from sending the same item.
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, sendRequest())
    }

    /**
     * One sending run. Between tries the wait grows by 30 seconds (30 s, 60 s, 90 s…), never
     * doubling: school wifi can count as "connected" with no real internet, and a doubling wait
     * would soon leave changes unsent for hours after the internet is back ([sendNow] also
     * cuts a wait short).
     */
    private fun sendRequest() = OneTimeWorkRequestBuilder<OutboxWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        .build()

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
