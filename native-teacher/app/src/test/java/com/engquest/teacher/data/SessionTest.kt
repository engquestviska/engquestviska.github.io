package com.engquest.teacher.data

import android.content.Context
import android.content.SharedPreferences
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

@RunWith(RobolectricTestRunner::class)
class SessionTest {
    private val server = MockWebServer()
    private lateinit var prefs: SharedPreferences
    private lateinit var api: Api

    @Before
    fun setUp() {
        server.start()
        api = Api(server.url("/exec").toString(), OkHttpClient())
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("session-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login stores creds only when checkLogin ok`() = runBlocking {
        val session = Session(prefs, api)

        server.enqueue(MockResponse().setBody("""{"ok":false}"""))
        assertFalse(session.login("teacher", "wrong"))
        assertNull(session.creds())
        assertNull(session.user.value)

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        assertTrue(session.login("teacher", "170403"))
        assertEquals(Creds("teacher", "170403"), session.creds())
        assertEquals(Creds("teacher", "170403"), session.user.value)

        // checkLogin is called with the typed username and password, both times.
        repeat(2) {
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("checkLogin", body["action"]!!.jsonPrimitive.content)
            assertEquals("teacher", body["username"]!!.jsonPrimitive.content)
        }

        // A new Session on the same prefs (app restart) is still logged in.
        assertEquals(Creds("teacher", "170403"), Session(prefs, api).creds())
    }

    @Test
    fun `markExpired keeps username, drops password`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val session = Session(prefs, api)
        session.login("teacher", "170403")

        session.markExpired()

        assertNull(session.creds())
        assertNull(session.user.value)
        assertTrue(session.expired.value)
        assertEquals("teacher", session.savedUsername())
        val restarted = Session(prefs, api)
        assertTrue(restarted.expired.value)
        assertEquals("teacher", restarted.savedUsername())
        assertNull(restarted.creds())

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        session.login("teacher", "new-pass")
        assertFalse(session.expired.value)
        assertEquals(Creds("teacher", "new-pass"), session.creds())
    }

    @Test
    fun `logout forgets everything`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val session = Session(prefs, api)
        session.login("teacher", "170403")

        session.logout()

        assertNull(session.creds())
        assertNull(session.user.value)
        assertFalse(session.expired.value)
        assertNull(session.savedUsername())
    }
}
