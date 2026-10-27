package com.engquest.teacher.push

import com.engquest.teacher.GraphOwner
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/** Hands out this phone's notification token. Firebase in the app; a fake in tests. */
fun interface PushTokens {
    /** Calls [onToken] with the current token once Firebase has one (never, if it fails). */
    fun current(onToken: (String) -> Unit)
}

/** The real [PushTokens]: asks Firebase. */
object FirebasePushTokens : PushTokens {
    override fun current(onToken: (String) -> Unit) {
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { onToken(it) } }
    }
}

/**
 * Firebase's entry point. A new token is registered with the server (through the outbox).
 * A message that arrives while the app is open is shown here; while the app is closed the
 * phone shows the server's notification itself, and its tap carries the same
 * className / studentNo extras into [com.engquest.teacher.MainActivity].
 */
class EqMessagingService : FirebaseMessagingService() {
    private val graph get() = (application as GraphOwner).graph

    // Firebase calls this on its own background thread; waiting here is fine.
    public override fun onNewToken(token: String) {
        runBlocking { graph.registerDevice(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        SubmissionNotifications.show(this, n?.title.orEmpty(), n?.body.orEmpty(), message.data)
    }
}
