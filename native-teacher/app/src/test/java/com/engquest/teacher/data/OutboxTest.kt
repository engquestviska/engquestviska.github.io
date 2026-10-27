package com.engquest.teacher.data

import android.content.Context
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import androidx.room.Room
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.impl.WorkManagerImpl
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class OutboxTest {
    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var db: AppDb
    private lateinit var api: Api
    private lateinit var session: Session
    private lateinit var repo: Repo
    private lateinit var work: WorkManager
    private lateinit var outbox: Outbox

    /** 2026-10-27 09:30 on the phone's clock. */
    private val now: Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 27, 9, 30)
    }.timeInMillis

    private val ok = """{"success":true}"""

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
        // Short read timeout so a reply that never comes ("lost reply") is seen as Offline quickly.
        api = Api(server.url("/exec").toString(), OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build())
        val prefs = context.getSharedPreferences("outbox-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("username", "teacher").putString("password", "pw1").commit()
        session = Session(prefs, api)
        repo = Repo(api, session, db)
        // The scheduled job itself does nothing here; each test runs the real worker by hand.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(c: Context, name: String, p: WorkerParameters): ListenableWorker =
                        object : Worker(c, p) {
                            override fun doWork() = Result.success()
                        }
                })
                .build(),
        )
        work = WorkManager.getInstance(context)
        outbox = Outbox(db, session, work, scope, clock = { now })
    }

    @After
    fun tearDown() {
        scope.cancel()
        WorkManagerTestInitHelper.closeWorkDatabase()
        db.close()
        server.shutdown()
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<OutboxWorker>(context)
            .setWorkerFactory(OutboxWorkerFactory(db, session, api, repo))
            .build()
            .doWork()

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body.readUtf8()).jsonObject
    private fun JsonObject.s(key: String) = get(key)?.jsonPrimitive?.content
    private fun sent(n: Int) = List(n) { server.takeRequest(5, TimeUnit.SECONDS)!!.json() }

    /** Lets WorkManager's main-thread callbacks run (Robolectric pauses the main looper) until [done]. */
    private fun waitFor(done: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return true
            Thread.sleep(20)
        }
        return false
    }

    private suspend fun state(id: Long) = db.outboxDao().get(id)?.state

    /**
     * Ids of the outbox runs not yet finished, after a short pause: the Outbox's start-up check
     * may append one more run just after the test's first item lands.
     */
    private fun waitingRuns(work: WorkManager): Set<java.util.UUID> {
        Thread.sleep(200)
        return work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().filter { !it.state.isFinished }.map { it.id }.toSet()
    }

    @Test
    fun `items are sent oldest first, each with its own rid`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody(ok)) }
        val drained = mutableListOf<String>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { repo.outboxDrained.collect { drained += it } }

        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1", "reason" to "Answering"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2", "reason" to "Quiz"))
        val c = outbox.enqueue("saveAttendance", "XE1", mapOf("studentId" to "3"))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val requests = sent(3)
        assertEquals(listOf("1", "2", "3"), requests.map { it.s("studentId") })
        val rids = requests.map { it.s("rid")!! }
        assertEquals(listOf(a, b, c).map { db.outboxDao().get(it) }, listOf(null, null, null))
        assertEquals(3, rids.toSet().size)
        for (r in requests) {
            assertEquals("teacher", r.s("username"))
            assertEquals("pw1", r.s("password"))
            assertEquals("XE1", r.s("className"))
        }
        // clientDate only for the actions that take it.
        assertEquals("2026-10-27", requests[0].s("clientDate"))
        assertNull(requests[2].s("clientDate"))
        assertEquals(0, outbox.pending.first())
        assertEquals(listOf("XE1"), drained)
        watcher.cancel()
    }

    @Test
    fun `credentials are never stored in the item`() = runBlocking {
        val id = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1", "password" to "x", "username" to "y"))
        val item = db.outboxDao().get(id)!!
        assertFalse(item.paramsJson.contains("password"))
        assertFalse(item.paramsJson.contains("username"))
        assertEquals(OutboxState.PENDING, item.state)
        assertEquals(now, item.createdAt)
        assertTrue(item.rid.isNotBlank())
        assertTrue(work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().isNotEmpty())
    }

    @Test
    fun `offline keeps every item and asks to retry`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))
        server.shutdown()

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertEquals(OutboxState.PENDING, state(a))
        assertEquals(OutboxState.PENDING, state(b))
        assertEquals(2, outbox.pending.first())
    }

    @Test
    fun `a busy server keeps the item and asks to retry`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ListenableWorker.Result.retry(), runWorker())
        assertEquals(OutboxState.PENDING, state(a))
    }

    @Test
    fun `a server that keeps failing marks the item failed after 5 tries and the rest go on`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(500))
            assertEquals(ListenableWorker.Result.retry(), runWorker())
            assertEquals(OutboxState.PENDING, state(a))
        }
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(ok))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val failed = db.outboxDao().get(a)!!
        assertEquals(OutboxState.FAILED, failed.state)
        assertEquals("The server couldn't save this. Tap Retry to try again.", failed.error)
        // The failed item no longer holds up the next one.
        assertNull(db.outboxDao().get(b))
        val requests = sent(6)
        assertEquals(listOf("1", "1", "1", "1", "1", "2"), requests.map { it.s("studentId") })
        assertEquals(1, requests.take(5).map { it.s("rid") }.toSet().size)
    }

    @Test
    fun `retry replies count too and Retry starts the count again`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        repeat(5) {
            server.enqueue(MockResponse().setBody("""{"retry":true,"error":"Request in progress"}"""))
            runWorker()
        }
        assertEquals(OutboxState.FAILED, state(a))

        outbox.retry(a).join()
        assertEquals(0, db.outboxDao().get(a)!!.attempts)
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(ListenableWorker.Result.retry(), runWorker())
            assertEquals(OutboxState.PENDING, state(a))
        }
    }

    @Test
    fun `no connection never marks an item failed`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        server.shutdown()

        repeat(6) { assertEquals(ListenableWorker.Result.retry(), runWorker()) }

        assertEquals(OutboxState.PENDING, state(a))
        assertEquals(0, db.outboxDao().get(a)!!.attempts)
    }

    @Test
    fun `sending waits 30 seconds between tries, without growing`() = runBlocking {
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))

        val runs = waitingRuns(work)
        assertTrue(runs.isNotEmpty())
        for (id in runs) {
            val spec = (work as WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(id.toString())!!
            assertEquals(BackoffPolicy.LINEAR, spec.backoffPolicy)
            assertEquals(30_000L, spec.backoffDelayDuration)
        }
    }

    @Test
    fun `sendNow replaces a run that is waiting`() = runBlocking {
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val waiting = waitingRuns(work)
        assertTrue(waiting.isNotEmpty())

        outbox.sendNow().join()

        val fresh = waitingRuns(work)
        assertTrue(fresh.none { it in waiting })
        assertEquals(1, fresh.size)
    }

    @Test
    fun `sendNow leaves a run that is sending alone`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        WorkManagerTestInitHelper.closeWorkDatabase()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(pool)
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(c: Context, name: String, p: WorkerParameters): ListenableWorker =
                        object : Worker(c, p) {
                            override fun doWork(): Result {
                                started.countDown()
                                release.await(5, TimeUnit.SECONDS)
                                return Result.success()
                            }
                        }
                })
                .build(),
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        val work = WorkManager.getInstance(context)
        val outbox = Outbox(db, session, work, scope, clock = { now })
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().forEach { driver.setAllConstraintsMet(it.id) }
        assertTrue(waitFor { started.count == 0L })
        assertTrue(waitFor { work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().any { it.state == WorkInfo.State.RUNNING } })
        val before = work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get()
        val running = before.first { it.state == WorkInfo.State.RUNNING }

        outbox.sendNow().join()

        val after = work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get()
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
        assertEquals(WorkInfo.State.RUNNING, after.first { it.id == running.id }.state)
        release.countDown()
        assertTrue(waitFor { work.getWorkInfoById(running.id).get()!!.state == WorkInfo.State.SUCCEEDED })
        pool.shutdown()
    }

    @Test
    fun `a resend after a lost reply uses the same rid`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setBody(ok))

        assertEquals(ListenableWorker.Result.retry(), runWorker())
        assertEquals(OutboxState.PENDING, state(a))
        assertEquals(ListenableWorker.Result.success(), runWorker())

        val (first, second) = sent(2)
        assertEquals(first.s("rid"), second.s("rid"))
        assertNull(db.outboxDao().get(a))
    }

    @Test
    fun `unauthorized keeps items and marks the session expired`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))
        server.enqueue(MockResponse().setBody("""{"error":"Unauthorized"}"""))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(1, server.requestCount)
        assertEquals(OutboxState.PENDING, state(a))
        assertEquals(OutboxState.PENDING, state(b))
        assertTrue(session.expired.value)
        assertNull(session.creds())

        // Logged out: the worker sends nothing and keeps everything.
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(1, server.requestCount)

        // After a new login the same items go out, with the new password and their old rids.
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        repeat(2) { server.enqueue(MockResponse().setBody(ok)) }
        val oldRid = db.outboxDao().get(a)!!.rid
        assertTrue(session.login("teacher", "pw2"))
        server.takeRequest() // the refused first attempt
        server.takeRequest() // checkLogin
        assertEquals(ListenableWorker.Result.success(), runWorker())
        val requests = sent(2)
        assertEquals(listOf("1", "2"), requests.map { it.s("studentId") })
        assertEquals(listOf("pw2", "pw2"), requests.map { it.s("password") })
        assertEquals(oldRid, requests[0].s("rid"))
        assertEquals(0, outbox.pending.first())
    }

    @Test
    fun `a server error fails only that item`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))
        val c = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "3"))
        server.enqueue(MockResponse().setBody(ok))
        server.enqueue(MockResponse().setBody("""{"error":"Student not found"}"""))
        server.enqueue(MockResponse().setBody(ok))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertNull(db.outboxDao().get(a))
        assertNull(db.outboxDao().get(c))
        val failed = outbox.failed.first()
        assertEquals(listOf(b), failed.map { it.id })
        assertEquals("Student not found", failed[0].error)
        assertEquals(0, outbox.pending.first())
    }

    @Test
    fun `retry sends a failed item again with the same rid and discard removes it`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))
        server.enqueue(MockResponse().setBody("""{"error":"Try later"}"""))
        server.enqueue(MockResponse().setBody("""{"error":"No"}"""))
        server.enqueue(MockResponse().setBody(ok))
        runWorker()
        val firstRid = sent(1)[0].s("rid")
        assertEquals(2, outbox.failed.first().size)

        outbox.retry(a).join()
        outbox.discard(b).join()
        assertEquals(1, outbox.pending.first())
        assertTrue(outbox.failed.first().isEmpty())
        assertNull(db.outboxDao().get(b))

        assertEquals(ListenableWorker.Result.success(), runWorker())
        sent(1) // b's refused attempt
        assertEquals(firstRid, sent(1)[0].s("rid"))
        assertNull(db.outboxDao().get(a))
    }

    @Test
    fun `items keep the class they were made for`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody(ok)) }
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        outbox.enqueue("addStrike", "XE2", mapOf("studentId" to "1"))

        runWorker()

        assertEquals(listOf("XE1", "XE2"), sent(2).map { it.s("className") })
    }

    @Test
    fun `pending items survive a new Outbox instance on the same database`() = runBlocking {
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        // The app was killed while this item was on its way.
        db.outboxDao().setState(a, OutboxState.SENDING)
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))

        val restarted = Outbox(db, session, work, scope, clock = { now })
        assertEquals(2, restarted.pending.first())
        repeat(2) { server.enqueue(MockResponse().setBody(ok)) }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf("1", "2"), sent(2).map { it.s("studentId") })
        assertEquals(0, restarted.pending.first())
    }

    @Test
    fun `an item enqueued while the worker is finishing still gets sent`() = runBlocking {
        // A real WorkManager run: the first run stands for a worker that already found nothing to
        // send and is just finishing; later runs are the real OutboxWorker.
        val finishing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val created = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(2)
        val real = OutboxWorkerFactory(db, session, api, repo)
        WorkManagerTestInitHelper.closeWorkDatabase()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(pool)
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(c: Context, name: String, p: WorkerParameters): ListenableWorker? =
                        if (created.getAndIncrement() == 0) {
                            object : Worker(c, p) {
                                override fun doWork(): Result {
                                    finishing.countDown()
                                    release.await(5, TimeUnit.SECONDS)
                                    return Result.success()
                                }
                            }
                        } else {
                            real.createWorker(c, name, p)
                        }
                })
                .build(),
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        val work = WorkManager.getInstance(context)
        val outbox = Outbox(db, session, work, scope, clock = { now })
        repeat(2) { server.enqueue(MockResponse().setBody(ok)) }

        work.enqueueUniqueWork(Outbox.WORK_NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<OutboxWorker>().build())
        assertTrue(waitFor { finishing.count == 0L })
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().forEach { driver.setAllConstraintsMet(it.id) }
        release.countDown()

        // Both appended runs finish; the second finds nothing left and sends nothing twice.
        waitFor { work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get().all { it.state.isFinished } }
        val runs = work.getWorkInfosForUniqueWork(Outbox.WORK_NAME).get()
        assertEquals(3, runs.size)
        assertTrue(runs.all { it.state == WorkInfo.State.SUCCEEDED })
        assertEquals(listOf("1", "2"), sent(2).map { it.s("studentId") })
        assertEquals(2, server.requestCount)
        assertEquals(0, outbox.pending.first())
        pool.shutdown()
    }

    @Test
    fun `cancelIfPending refuses an item that is being sent`() = runBlocking {
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown()
                release.await(5, TimeUnit.SECONDS)
                return MockResponse().setBody(ok)
            }
        }
        val a = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "1"))
        val b = outbox.enqueue("addStrike", "XE1", mapOf("studentId" to "2"))

        val run = async(Dispatchers.IO) { runWorker() }
        assertTrue(received.await(5, TimeUnit.SECONDS))

        assertEquals(OutboxState.SENDING, state(a))
        assertFalse(outbox.cancelIfPending(a))
        assertTrue(outbox.cancelIfPending(b))
        release.countDown()

        assertEquals(ListenableWorker.Result.success(), run.await())
        assertEquals(1, server.requestCount)
        assertNull(db.outboxDao().get(a))
        assertNotEquals(OutboxState.PENDING, state(b))
    }
}
