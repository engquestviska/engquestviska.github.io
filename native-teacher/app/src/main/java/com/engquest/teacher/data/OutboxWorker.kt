package com.engquest.teacher.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Sends the outbox, oldest first, one at a time, with the login as it is now and each item's
 * own rid.
 * - No connection: the item waits, and WorkManager runs this again later.
 * - Server busy or erroring: the same, but after [MAX_SERVER_TRIES] such replies in a row the
 *   item is marked FAILED so it stops holding up the rest.
 * - Password no longer works: the session is marked expired and everything waits; [Outbox]
 *   schedules again after the next login.
 * - Server refused the item: it is marked FAILED with the server's message; the rest go on.
 * When a class has nothing left to send, [Repo.notifyDrained] lets its screens re-read.
 */
class OutboxWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDb,
    private val session: Session,
    private val api: Api,
    private val repo: Repo,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = db.outboxDao()
        // An item left SENDING by a killed app goes back in line; its rid keeps a resend safe.
        dao.resetSending()
        while (true) {
            val item = dao.oldestPending() ?: return Result.success()
            val creds = session.creds() ?: return Result.success()
            if (dao.claim(item.id) == 0) continue // cancelled (XP Undo) just now
            val params = storedParams(item) + mapOf(
                "className" to item.className,
                "username" to creds.username,
                "password" to creds.password,
                "rid" to item.rid,
            )
            try {
                api.write(item.action, params)
                dao.delete(item.id)
            } catch (e: ApiError) {
                when (e) {
                    is ApiError.Offline -> {
                        dao.setState(item.id, OutboxState.PENDING)
                        return Result.retry()
                    }
                    is ApiError.RetryLater -> {
                        // The server answered but could not save it. Asking forever would hold up
                        // every later write, so after a few tries the teacher decides (Retry/Discard).
                        val tries = item.attempts + 1
                        if (tries < MAX_SERVER_TRIES) {
                            dao.waitAfterServerTry(item.id, tries)
                            return Result.retry()
                        }
                        dao.setState(item.id, OutboxState.FAILED, SERVER_GAVE_UP)
                    }
                    is ApiError.Unauthorized -> {
                        dao.setState(item.id, OutboxState.PENDING)
                        session.markExpired()
                        return Result.success()
                    }
                    is ApiError.Server -> dao.setState(item.id, OutboxState.FAILED, e.userMessage)
                }
            }
            if (dao.countUnsent(item.className) == 0) repo.notifyDrained(item.className)
        }
    }

    companion object {
        const val MAX_SERVER_TRIES = 5
        const val SERVER_GAVE_UP = "The server couldn't save this. Tap Retry to try again."
    }

    private fun storedParams(item: OutboxItem): Map<String, String> =
        (Json.parseToJsonElement(item.paramsJson) as JsonObject)
            .mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }
}

/**
 * Builds [OutboxWorker] with the app's own objects. The app's WorkManager configuration
 * (Task 8, `AppGraph`) registers this; tests pass it to `TestListenableWorkerBuilder`.
 */
class OutboxWorkerFactory(
    private val db: AppDb,
    private val session: Session,
    private val api: Api,
    private val repo: Repo,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == OutboxWorker::class.java.name) {
            OutboxWorker(appContext, workerParameters, db, session, api, repo)
        } else {
            null
        }
}
