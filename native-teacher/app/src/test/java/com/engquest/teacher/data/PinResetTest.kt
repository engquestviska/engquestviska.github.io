package com.engquest.teacher.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PinResetTest {
    private val server = MockWebServer()
    private lateinit var api: Api

    @Before fun setUp() { server.start(); api = Api(server.url("/exec").toString(), OkHttpClient()) }
    @After fun tearDown() = server.shutdown()

    @Test fun `reset posts class, number and the teacher login`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        val result = PinReset(api) { Creds("teacher", "pw") }.reset("XE2", "7")
        assertNull(result)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("resetChroniclePin", body["action"]!!.jsonPrimitive.content)
        assertEquals("XE2", body["className"]!!.jsonPrimitive.content)
        assertEquals("7", body["studentNo"]!!.jsonPrimitive.content)
        assertEquals("teacher", body["username"]!!.jsonPrimitive.content)
        assertEquals("pw", body["password"]!!.jsonPrimitive.content)
    }

    @Test fun `an unknown student comes back as a plain message`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"Student not found"}"""))
        assertEquals("No student with that number in this class.", PinReset(api) { Creds("teacher", "pw") }.reset("XE2", "99"))
    }

    @Test fun `no login means no request`() = runBlocking {
        assertEquals("Please log in again.", PinReset(api) { null }.reset("XE2", "7"))
        assertEquals(0, server.requestCount)
    }

    @Test fun `a number that is not a number is refused before sending`() = runBlocking {
        assertEquals("Type the student's number.", PinReset(api) { Creds("teacher", "pw") }.reset("XE2", "abc"))
        assertEquals(0, server.requestCount)
    }
}
