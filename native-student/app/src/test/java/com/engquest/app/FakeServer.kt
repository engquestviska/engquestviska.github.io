package com.engquest.app

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for the Worker: answers each action with [replies] (set by the test) or, failing that,
 * the anonymised sample reply in test resources/fixtures. Unknown actions get {"success":true}.
 * [requests] keeps every call as action -> params (query for GET, JSON body text for POST).
 */
class FakeServer {
    val server = MockWebServer()
    val replies = mutableMapOf<String, String>()
    val requests = CopyOnWriteArrayList<Pair<String, String>>()
    /** When true every request fails like a dead connection. */
    @Volatile var down = false

    fun start(): String {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (down) return MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
                val url = request.requestUrl!!
                val action = url.queryParameter("action").orEmpty()
                val body = request.body.readUtf8()
                requests += action to (if (request.method == "POST") body else url.query.orEmpty())
                val text = replies[action]
                    ?: FakeServer::class.java.getResource("/fixtures/$action.json")?.readText()
                    ?: """{"success":true}"""
                return MockResponse().setBody(text)
            }
        }
        server.start()
        return server.url("/exec").toString()
    }

    fun calls(action: String) = requests.filter { it.first == action }.map { it.second }

    fun stop() = server.shutdown()
}
