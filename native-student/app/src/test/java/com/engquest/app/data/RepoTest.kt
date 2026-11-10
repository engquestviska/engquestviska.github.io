package com.engquest.app.data

import android.content.Context
import androidx.room.Room
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RepoTest {
    private val server = MockWebServer()
    private lateinit var db: AppDb
    private lateinit var repo: Repo
    private var now = 1000L

    @Before fun setUp() {
        server.start()
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
        repo = Repo(Api(server.url("/exec").toString(), OkHttpClient()), db, clock = { now })
    }

    @After fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test fun serverDownAfterOneSuccessGivesTheSavedReply() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"students":[{"no":1,"name":"Ani","nickname":""}]}"""))
        val first = repo.classmates("XE1")
        assertFalse(first.fromCache)
        assertEquals("Ani", first.value.single().name)

        server.shutdown()
        now = 9000L
        val second = repo.classmates("XE1")
        assertTrue(second.fromCache)
        assertEquals(1000L, second.savedAt)
        assertEquals("Ani", second.value.single().name)
    }

    @Test fun offlineWithNothingSavedThrowsOffline() = runBlocking {
        server.shutdown()
        try {
            repo.classmates("XE3")
            fail("expected Offline")
        } catch (e: ApiError.Offline) {
            // expected
        }
    }

    @Test fun readsSendTheActionAndParams() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"events":[]}"""))
        repo.myEvents(Profile(Grade.G10, "XE2", 14, "Dimas"), beforeId = 0)
        val url = server.takeRequest().requestUrl!!
        assertEquals("getMyEvents", url.queryParameter("action"))
        assertEquals("XE2", url.queryParameter("className"))
        assertEquals("14", url.queryParameter("studentNo"))
        assertNull(url.queryParameter("beforeId"))
    }

    @Test fun profileStoreRoundTrips() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("p-test", Context.MODE_PRIVATE)
        val store = ProfileStore(prefs)
        assertNull(store.get())
        val p = Profile(Grade.G11, "XIF8", 3, "Eka")
        store.save(p)
        assertEquals(p, store.get())
        store.clear()
        assertNull(store.get())
    }
}
