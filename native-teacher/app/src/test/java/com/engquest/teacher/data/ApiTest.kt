package com.engquest.teacher.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiTest {
    private val server = MockWebServer()
    private lateinit var api: Api

    @Before
    fun setUp() {
        server.start()
        api = Api(server.url("/exec").toString(), OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun reply(body: String) = server.enqueue(MockResponse().setBody(body))

    private inline fun <reified E : ApiError> assertThrows(block: () -> Unit): E {
        try {
            block()
        } catch (e: ApiError) {
            if (e is E) return e
            fail("expected ${E::class.simpleName}, got $e")
        }
        fail("expected ${E::class.simpleName}, nothing thrown")
        throw AssertionError()
    }

    @Test
    fun `read sends GET without callback and parses JSON`() = runBlocking {
        reply("""{"students":[{"no":"1","name":"Ana"}]}""")

        val json = api.read("getStudents", mapOf("className" to "XE1"))

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        val url = req.requestUrl!!
        assertEquals("/exec", url.encodedPath)
        assertEquals("getStudents", url.queryParameter("action"))
        assertEquals("XE1", url.queryParameter("className"))
        assertNull(url.queryParameter("callback"))
        assertEquals("Ana", json["students"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `write posts JSON with all params`() = runBlocking {
        reply("""{"success":true}""")

        val params = mapOf("username" to "teacher", "password" to "pw", "rid" to "r-1", "className" to "XE2")
        val json = api.write("addStrike", params)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("addStrike", body["action"]!!.jsonPrimitive.content)
        for ((k, v) in params) assertEquals(v, body[k]!!.jsonPrimitive.content)
        assertEquals("true", json["success"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Unauthorized reply throws ApiError_Unauthorized`() {
        reply("""{"success":false,"error":"Unauthorized"}""")
        assertThrows<ApiError.Unauthorized> { runBlocking { api.write("addStrike", emptyMap()) } }
    }

    @Test
    fun `other error reply throws Server with the message`() {
        reply("""{"success":false,"error":"Student not found"}""")
        val e = assertThrows<ApiError.Server> { runBlocking { api.read("getStudents", emptyMap()) } }
        assertEquals("Student not found", e.message)
    }

    @Test
    fun `retry reply throws RetryLater`() {
        reply("""{"error":"Request in progress","retry":true}""")
        assertThrows<ApiError.RetryLater> { runBlocking { api.write("addStrike", emptyMap()) } }
    }

    @Test
    fun `ok false without an error is a normal reply`() = runBlocking {
        reply("""{"ok":false}""")
        val json = api.write("checkLogin", emptyMap())
        assertEquals("false", json["ok"]!!.jsonPrimitive.content)
    }

    @Test
    fun `connection refused throws Offline`() {
        val url = server.url("/exec").toString()
        server.shutdown()
        val offline = Api(url, OkHttpClient())
        assertThrows<ApiError.Offline> { runBlocking { offline.read("ping", emptyMap()) } }
    }

    @Test
    fun `503 reply throws RetryLater`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        assertThrows<ApiError.RetryLater> { runBlocking { api.write("addStrike", emptyMap()) } }
    }

    @Test
    fun `404 reply throws Server`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        assertThrows<ApiError.Server> { runBlocking { api.read("ping", emptyMap()) } }
    }

    @Test
    fun `a reply that is not JSON throws Server`() {
        reply("<html>Bad gateway</html>")
        assertThrows<ApiError.Server> { runBlocking { api.read("ping", emptyMap()) } }
    }

    @Test
    fun `a reply too big for the phone's memory ends the call with a plain message`() {
        // Stands in for body.string() running out of memory on a huge photo.
        val huge = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1
            override fun source(): BufferedSource = throw OutOfMemoryError("test")
        }
        val tooBig = Api(
            server.url("/exec").toString(),
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(huge).build()
            }.build(),
        )
        val e = assertThrows<ApiError.Server> {
            runBlocking { withTimeout(5_000) { tooBig.read("getSubmissionPhoto", emptyMap()) } }
        }
        assertEquals("This photo is too big to show on this phone.", e.userMessage)
    }
}
