package com.engquest.app.data

import androidx.room.Room
import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SendQueueTest {
    private val fake = FakeServer()
    private lateinit var db: AppDb
    private lateinit var queue: SendQueue
    private lateinit var dir: File
    private val me = Profile(Grade.G10, "XE2", 14, "Dimas")

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "send-test").apply { deleteRecursively(); mkdirs() }
        queue = SendQueue(db, Api(fake.start(), OkHttpClient()), dir, kick = {})
    }

    @After fun tearDown() {
        db.close()
        fake.stop()
    }

    private fun photo(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) = File(dir, name).apply { writeBytes(bytes) }

    @Test fun sendsPhotosWithRidAndClearsTheQueue() = runBlocking {
        queue.enqueue(me, "C1T2", listOf(photo("a.jpg"), photo("b.jpg")))
        assertEquals(1, db.photoSendDao().all().size)
        assertTrue(queue.drain())
        assertTrue(db.photoSendDao().all().isEmpty())
        val body = Json.parseToJsonElement(fake.calls("submitTaskPhoto").single()).jsonObject
        assertEquals("XE2", body["className"]!!.jsonPrimitive.content)
        assertEquals("14", body["studentNo"]!!.jsonPrimitive.content)
        assertEquals("C1T2", body["taskKey"]!!.jsonPrimitive.content)
        assertEquals(listOf("AQID", "AQID"), body["images"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(body["rid"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals(0, dir.listFiles()!!.count { it.name.startsWith("send_") })
    }

    @Test fun offlineKeepsTheSendWaiting() = runBlocking {
        queue.enqueue(me, "C1T1", listOf(photo("a.jpg")))
        fake.stop()
        assertFalse(queue.drain())
        val item = db.photoSendDao().all().single()
        assertEquals(SendState.PENDING, item.state)
    }

    @Test fun retryAfterBusyServerReusesTheSameRid() = runBlocking {
        queue.enqueue(me, "C2T1", listOf(photo("a.jpg")))
        fake.replies["submitTaskPhoto"] = """{"error":"Request in progress","retry":true}"""
        assertFalse(queue.drain())
        fake.replies.remove("submitTaskPhoto")
        assertTrue(queue.drain())
        val rids = fake.calls("submitTaskPhoto").map { Json.parseToJsonElement(it).jsonObject["rid"]!!.jsonPrimitive.content }
        assertEquals(2, rids.size)
        assertEquals(rids[0], rids[1])
    }

    @Test fun refusedSendIsMarkedFailedWithTheReason() = runBlocking {
        queue.enqueue(me, "C2T1", listOf(photo("a.jpg")))
        fake.replies["submitTaskPhoto"] = """{"success":false,"error":"Student not found"}"""
        assertTrue(queue.drain())
        val item = db.photoSendDao().all().single()
        assertEquals(SendState.FAILED, item.state)
        assertEquals("Student not found", item.error)
        queue.retry(item.id)
        assertEquals(SendState.PENDING, db.photoSendDao().all().single().state)
    }

    @Test fun killedMidSendGoesBackInLine() = runBlocking {
        queue.enqueue(me, "C3T1", listOf(photo("a.jpg")))
        val id = db.photoSendDao().all().single().id
        db.photoSendDao().claim(id)
        queue.recover()
        assertEquals(SendState.PENDING, db.photoSendDao().get(id)!!.state)
    }

    @Test fun pickedPhotosStopAtTen() {
        val picks = PhotoPicks()
        assertEquals(0, picks.add((1..8).map { "u$it" }))
        assertEquals(1, picks.add(listOf("u9", "u10", "u11")))
        assertEquals(10, picks.items.size)
        assertTrue(picks.full)
        picks.remove("u3")
        assertFalse(picks.full)
    }

    @Test fun shrinkKeepsShapeWithin1000() {
        assertEquals(1000 to 750, shrinkTo(4000, 3000, 1000))
        assertEquals(750 to 1000, shrinkTo(3000, 4000, 1000))
        assertEquals(800 to 600, shrinkTo(800, 600, 1000))
    }

    @Test fun aSendCutOffMidUploadGoesBackInLine() = runBlocking {
        queue.enqueue(me, "C2T2", listOf(photo("a.jpg")))
        fake.replies["submitTaskPhoto"] = """{"success":true}"""
        fake.server.dispatcher.let { real ->
            fake.server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                    real.dispatch(request).setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { queue.drain() }
        kotlinx.coroutines.delay(700)
        job.cancel()
        job.join()
        assertEquals(SendState.PENDING, db.photoSendDao().all().single().state)
    }
}
