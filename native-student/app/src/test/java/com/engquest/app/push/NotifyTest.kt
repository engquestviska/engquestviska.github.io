package com.engquest.app.push

import android.content.Intent
import com.engquest.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotifyTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun tappingANotificationOpensItsPage() {
        val intent = Notify.openIntent(context, "strikes")
        assertEquals("strikes", MainActivity.linkFrom(intent))
    }

    @Test fun serverNotificationTapCarriesTheLinkExtra() {
        // While the app is closed, Android puts the message's data keys on the launch intent.
        val intent = Intent(context, MainActivity::class.java).putExtra("link", "levelxp")
        assertEquals("levelxp", MainActivity.linkFrom(intent))
    }

    @Test fun unknownOrMissingLinksAreIgnored() {
        assertNull(MainActivity.linkFrom(Intent()))
        assertNull(MainActivity.linkFrom(Intent().putExtra("link", "https://evil.example")))
    }

    @Test fun onlyEventMessagesAreShown() {
        assertEquals(false, Notify.show(context, "t", "b", mapOf("type" to "submission")))
        assertEquals(true, Notify.show(context, "Task approved", "C1 Task 2 · Score 90", mapOf("type" to "event", "kind" to "approved", "link" to "send", "eventId" to "5")))
    }
}
