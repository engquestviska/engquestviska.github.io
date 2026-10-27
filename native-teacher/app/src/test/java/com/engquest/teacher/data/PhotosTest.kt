package com.engquest.teacher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** A real JPEG of [w]×[h] as base64, the way the server sends a photo. */
internal fun jpegBase64(w: Int, h: Int): String {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
    bmp.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotosTest {
    private val server = MockWebServer()
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var session: Session
    private lateinit var dir: File
    private lateinit var photos: Photos

    @Before
    fun setUp() {
        server.start()
        val prefs = context.getSharedPreferences("photos-session", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("username", "teacher").putString("password", "pw1").commit()
        val api = Api(server.url("/exec").toString(), OkHttpClient())
        session = Session(prefs, api)
        dir = File(context.cacheDir, "photos-test").apply { deleteRecursively() }
        photos = Photos(api, session, dir)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun reply(mime: String, data: String) =
        server.enqueue(MockResponse().setBody("""{"success":true,"mimeType":"$mime","dataBase64":"$data"}"""))

    @Test
    fun `a 4000x3000 photo is decoded at most 2048 px wide`() = runBlocking {
        reply("image/jpeg", jpegBase64(4000, 3000))
        val img = photos.load("12")
        assertNotNull(img)
        assertTrue("width ${img!!.width}", img.width in 1000..2048)
        assertTrue("height ${img.height}", img.height in 750..2048)
    }

    @Test
    fun `bad mime still shows the photo`() = runBlocking {
        reply("text/html<script>", jpegBase64(40, 30))
        val img = photos.load("12")
        assertEquals(40, img!!.width)
    }

    @Test
    fun `garbage base64 gives no photo`() = runBlocking {
        reply("image/jpeg", "%%%not*base64%%%")
        assertNull(photos.load("12"))
    }

    @Test
    fun `bytes that are not a picture give no photo`() = runBlocking {
        reply("image/jpeg", Base64.encodeToString("hello, not an image".toByteArray(), Base64.NO_WRAP))
        assertNull(photos.load("12"))
    }

    @Test
    fun `asks with the login and keeps a photo on disk so it opens offline`() = runBlocking {
        reply("image/png", jpegBase64(40, 30))
        assertNotNull(photos.load("12"))
        val req = server.takeRequest()
        assertEquals("getSubmissionPhoto", req.requestUrl!!.queryParameter("action"))
        assertEquals("12", req.requestUrl!!.queryParameter("fileId"))
        assertEquals("teacher", req.requestUrl!!.queryParameter("username"))
        assertTrue(File(dir, "12").exists())
        server.shutdown() // offline now
        val again = Photos(Api(server.url("/exec").toString(), OkHttpClient()), session, dir)
        assertEquals(40, again.load("12")!!.width)
    }

    @Test
    fun `a file id cannot write outside the photo folder`() = runBlocking {
        reply("image/jpeg", jpegBase64(10, 10))
        assertNotNull(photos.load("../../evil"))
        assertEquals(listOf(dir.canonicalPath), dir.listFiles()!!.map { it.parentFile!!.canonicalPath })
        assertTrue(!File(dir.parentFile!!.parentFile, "evil").exists())
    }

    @Test
    fun `a refused login asks to log in again`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":false,"error":"Unauthorized"}"""))
        val err = runCatching { photos.load("12") }.exceptionOrNull()
        assertTrue(err is ApiError.Unauthorized)
        assertTrue(session.expired.value)
    }
}
