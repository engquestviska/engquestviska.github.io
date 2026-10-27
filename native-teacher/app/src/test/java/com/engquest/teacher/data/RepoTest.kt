package com.engquest.teacher.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RepoTest {
    private val server = MockWebServer()
    private lateinit var db: AppDb
    private lateinit var session: Session
    private lateinit var repo: Repo

    private fun obj(v: String) = JsonObject(mapOf("v" to JsonPrimitive(v)))
    private fun JsonObject?.v() = this?.get("v")?.jsonPrimitive?.content

    @Before
    fun setUp() {
        server.start()
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
        val api = Api(server.url("/exec").toString(), OkHttpClient())
        val prefs = context.getSharedPreferences("repo-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        session = Session(prefs, api)
        repo = Repo(api, session, db, clock = { 5000L })
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private suspend fun pendingWrite(className: String) = db.outboxDao().insert(
        OutboxItem(rid = "r", action = "addStrike", className = className, paramsJson = "{}", createdAt = 1L),
    )

    @Test
    fun `cached emits stored value before network value`() = runBlocking {
        val key = Repo.cacheKey("getStudents", "XE1")
        db.cacheDao().put(CacheEntry(key, obj("old").toString(), 1000L))

        val seen = repo.cached(key) { obj("new") }.take(2).toList()

        assertEquals(Cached(obj("old"), 1000L, refreshing = true, error = null), seen[0])
        assertEquals(Cached(obj("new"), 5000L, refreshing = false, error = null), seen[1])
        assertEquals(obj("new").toString(), db.cacheDao().get(key)!!.json)
    }

    @Test
    fun `cached with nothing stored emits empty then the network value`() = runBlocking {
        val key = Repo.cacheKey("getStudents", "XE3")
        val seen = repo.cached(key) { obj("new") }.take(2).toList()
        assertNull(seen[0].json)
        assertTrue(seen[0].refreshing)
        assertEquals("new", seen[1].json.v())
    }

    @Test
    fun `cached keeps the stored value and reports the problem when offline`() = runBlocking {
        val key = Repo.cacheKey("getStudents", "XE1")
        db.cacheDao().put(CacheEntry(key, obj("old").toString(), 1000L))

        val seen = repo.cached(key) { throw ApiError.Offline() }.take(2).toList()

        assertEquals("old", seen[1].json.v())
        assertFalse(seen[1].refreshing)
        assertEquals(ApiError.Offline().userMessage, seen[1].error)
    }

    @Test
    fun `cached does not overwrite while the class has unsent writes`() = runBlocking {
        val key = Repo.cacheKey("getClassData", "XE1", "4")
        db.cacheDao().put(CacheEntry(key, obj("old").toString(), 1000L))
        val itemId = pendingWrite("XE1")
        pendingWrite("XE2") // another class's write must not matter once XE1 drains

        var reads = 0
        val out = Channel<Cached>(Channel.UNLIMITED)
        val job = launch {
            repo.cached(key) { reads++; obj("server-$reads") }.collect { out.send(it) }
        }
        suspend fun next() = withTimeout(5000) { out.receive() }

        assertEquals("old", next().json.v())
        val afterRead = next()
        assertEquals(1, reads)
        assertEquals("old", afterRead.json.v()) // fresh reply held back
        assertFalse(afterRead.refreshing)
        assertEquals(obj("old").toString(), db.cacheDao().get(key)!!.json)

        // Another class draining does not trigger a re-read.
        repo.notifyDrained("XE2")
        db.outboxDao().delete(itemId)
        repo.notifyDrained("XE1")

        assertTrue(next().refreshing)
        val fresh = next()
        assertEquals(2, reads)
        assertEquals("server-2", fresh.json.v())
        assertEquals(obj("server-2").toString(), db.cacheDao().get(key)!!.json)
        job.cancel()
    }

    @Test
    fun `login-only reads carry the username and password`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        session.login("teacher", "170403")
        server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"submissions":[]}"""))
        server.enqueue(MockResponse().setBody("""{"students":[]}"""))

        repo.load("getTaskSubmissions", "XE1").take(2).toList()
        repo.load("getStudents", "XE1").take(2).toList()

        val subs = server.takeRequest().requestUrl!!
        assertEquals("getTaskSubmissions", subs.queryParameter("action"))
        assertEquals("XE1", subs.queryParameter("className"))
        assertEquals("teacher", subs.queryParameter("username"))
        assertEquals("170403", subs.queryParameter("password"))
        val students = server.takeRequest().requestUrl!!
        assertEquals("getStudents", students.queryParameter("action"))
        assertNull(students.queryParameter("password"))
    }

    @Test
    fun `photos are not stored in Room`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"v":"photo-12"}"""))
        server.enqueue(MockResponse().setBody("""{"v":"photo-13"}"""))

        val first = repo.load("getSubmissionPhoto", "XE1", mapOf("fileId" to "12")).take(2).toList()
        val second = repo.load("getSubmissionPhoto", "XE1", mapOf("fileId" to "13")).take(2).toList()

        assertNull(second[0].json) // never another student's photo first
        assertEquals("photo-12", first[1].json.v())
        assertEquals("photo-13", second[1].json.v())
        assertEquals("12", server.takeRequest().requestUrl!!.queryParameter("fileId"))
        assertEquals("13", server.takeRequest().requestUrl!!.queryParameter("fileId"))
        assertNull(db.cacheDao().get(Repo.cacheKey("getSubmissionPhoto", "XE1", params = mapOf("fileId" to "12"))))
        assertNull(db.cacheDao().get(Repo.cacheKey("getSubmissionPhoto", "XE1", params = mapOf("fileId" to "13"))))
    }

    @Test
    fun `reads with different params never share a cache entry`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"v":"a"}"""))
        server.enqueue(MockResponse().setBody("""{"v":"b"}"""))

        repo.load("getQuizAttempt", "XE1", mapOf("studentNo" to "1")).take(2).toList()
        val other = repo.load("getQuizAttempt", "XE1", mapOf("studentNo" to "2")).take(2).toList()

        assertNull(other[0].json)
        assertEquals("b", other[1].json.v())
    }

    @Test
    fun `cache key ignores credentials`() {
        val key = Repo.cacheKey(
            "getTaskSubmissions", "XE1", "4",
            mapOf("username" to "teacher", "password" to "170403", "className" to "XE1", "b" to "2", "a" to "1"),
        )
        assertEquals("getTaskSubmissions|XE1|4|a=1&b=2", key)
        assertEquals("getSubmissionPhoto|XE1||fileId=12", Repo.cacheKey("getSubmissionPhoto", "XE1", params = mapOf("fileId" to "12")))
    }

    @Test
    fun `unauthorized read marks the session expired`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        session.login("teacher", "old-pass")
        server.enqueue(MockResponse().setBody("""{"success":false,"error":"Unauthorized"}"""))

        val seen = repo.load("getTaskSubmissions", "XE1").take(2).toList()

        assertEquals(ApiError.Unauthorized().userMessage, seen[1].error)
        assertTrue(session.expired.value)
        assertNull(session.creds())
        assertEquals("teacher", session.savedUsername())
    }

    @Test
    fun `cache keys follow action, class, chapter`() {
        assertEquals("getClassData|XE1|4", Repo.cacheKey("getClassData", "XE1", "4"))
        assertEquals("getStudents|XE1", Repo.cacheKey("getStudents", "XE1"))
    }
}
