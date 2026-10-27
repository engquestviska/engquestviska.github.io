package com.engquest.teacher.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Why a call to the server did not give a usable reply. [userMessage] is shown to the teacher. */
sealed class ApiError(message: String, val userMessage: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** No connection, or no reply within 20 seconds. Safe to try again later. */
    class Offline(cause: Throwable? = null) :
        ApiError("offline", "No internet connection.", cause)

    /** The saved username/password no longer works (e.g. changed on the website). */
    class Unauthorized : ApiError("Unauthorized", "Please log in again.")

    /** `retry: true` reply, or HTTP 5xx/408/429: the server is busy or briefly down. Treat like [Offline]. */
    class RetryLater(message: String) : ApiError(message, "The server is busy. Trying again soon.")

    /** The server refused the request or sent something unreadable. */
    class Server(message: String) : ApiError(message, message)
}

/**
 * Talks to the Worker's `/exec` bridge. Reads are GET with query params (no `callback`, so the
 * reply is plain JSON); writes are POST with every param in a JSON body.
 */
class Api(baseUrl: String, client: OkHttpClient) {
    private val base = baseUrl.toHttpUrl()
    private val client = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun read(action: String, params: Map<String, String>): JsonObject {
        val url = base.newBuilder().addQueryParameter("action", action).apply {
            for ((k, v) in params) addQueryParameter(k, v)
        }.build()
        return send(Request.Builder().url(url).get().build())
    }

    suspend fun write(action: String, params: Map<String, String>): JsonObject {
        val url = base.newBuilder().addQueryParameter("action", action).build()
        val body = JsonObject((params + ("action" to action)).mapValues { JsonPrimitive(it.value) })
        return send(Request.Builder().url(url).post(body.toString().toRequestBody(JSON_TYPE)).build())
    }

    private suspend fun send(request: Request): JsonObject {
        val text = suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(ApiError.Offline(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            // Busy or briefly down: resend later (the rid keeps a resend from doubling).
                            if (it.code >= 500 || it.code == 408 || it.code == 429) {
                                throw ApiError.RetryLater("Server error ${it.code}.")
                            }
                            if (!it.isSuccessful) throw ApiError.Server("Server error ${it.code}.")
                            it.body!!.string()
                        }
                        cont.resume(result)
                    } catch (e: Exception) {
                        cont.resumeWithException(
                            when (e) {
                                is ApiError -> e
                                is IOException -> ApiError.Offline(e)
                                else -> ApiError.Server("The server sent an unreadable reply.")
                            },
                        )
                    }
                }
            })
        }
        return check(parse(text))
    }

    private fun parse(text: String): JsonObject =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw ApiError.Server("The server sent an unreadable reply.")

    private fun check(json: JsonObject): JsonObject {
        val error = (json["error"] as? JsonPrimitive)?.contentOrNull
        if ((json["retry"] as? JsonPrimitive)?.booleanOrNull == true) {
            throw ApiError.RetryLater(error ?: "Request in progress")
        }
        if (error == "Unauthorized") throw ApiError.Unauthorized()
        if (error != null) throw ApiError.Server(error)
        if ((json["success"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw ApiError.Server("The server did not save this.")
        }
        return json
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
