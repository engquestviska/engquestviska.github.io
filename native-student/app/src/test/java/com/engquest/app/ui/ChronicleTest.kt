package com.engquest.app.ui

import android.net.Uri
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.ui.chronicle.ChronicleBridge
import com.engquest.app.ui.chronicle.instagramStoryIntent
import com.engquest.app.ui.chronicle.profileJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChronicleTest {
    @Test fun profileJsonHasAllFields() {
        val o = Json.parseToJsonElement(profileJson(Profile(Grade.G11, "XIF8", 5, "Nadia Putri"))).jsonObject
        assertEquals("11", o["grade"]!!.jsonPrimitive.content)
        assertEquals("XIF8", o["classId"]!!.jsonPrimitive.content)
        assertEquals("5", o["studentNo"]!!.jsonPrimitive.content)
        assertEquals("Nadia Putri", o["name"]!!.jsonPrimitive.content)
    }

    @Test fun onlyTheChroniclePageStaysInsideTheApp() {
        assertEquals(true, com.engquest.app.ui.chronicle.staysInChronicle("https://engquestviska.github.io/chronicle.html"))
        assertEquals(true, com.engquest.app.ui.chronicle.staysInChronicle("https://engquestviska.github.io/chronicle.html?story#x"))
        assertEquals(false, com.engquest.app.ui.chronicle.staysInChronicle("https://engquestviska.github.io/grade10/index.html"))
        assertEquals(false, com.engquest.app.ui.chronicle.staysInChronicle("https://engquestviska.github.io/chronicle.html.evil.com/"))
        assertEquals(false, com.engquest.app.ui.chronicle.staysInChronicle("https://drive.google.com/file/d/x"))
    }

    @Test fun instagramStoryIntentHasActionTypeAndExtras() {
        val uri = Uri.parse("content://com.engquest.app.fileprovider/share/chronicle-sticker.png")
        val i = instagramStoryIntent(uri, "123456")
        assertEquals("com.instagram.share.ADD_TO_STORY", i.action)
        assertEquals("image/png", i.type)
        assertEquals("123456", i.getStringExtra("source_application"))
        assertEquals(uri, i.getParcelableExtra<Uri>("interactive_asset_uri"))
        assertEquals("#F4F2ED", i.getStringExtra("top_background_color"))
        assertEquals("#141414", i.getStringExtra("bottom_background_color"))
        assertEquals("com.instagram.android", i.`package`)
    }

    @Test fun notYouOnThePageLogsOut() {
        var loggedOut = false
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        ChronicleBridge(ctx, Profile(Grade.G10, "XE1", 1, "Adel"), onLogOut = { loggedOut = true }).logOut()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        org.junit.Assert.assertTrue(loggedOut)
    }
}
