package com.engquest.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.engquest.app.MainActivity
import com.engquest.app.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.engquest.app.GraphOwner

/**
 * News notifications. While the app is closed the phone shows the server's notification itself
 * (channel from the manifest) and its tap carries the "link" data key into [MainActivity];
 * while the app is open, [StudentMessagingService] shows the same one through [show].
 */
object Notify {
    const val CHANNEL = "updates"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "EngQuest updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Approved tasks, XP, attendance and news from your teacher"
            },
        )
    }

    fun openIntent(context: Context, link: String): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(MainActivity.EXTRA_LINK, link)

    /** Shows one event message; returns false for anything that is not a student event. */
    fun show(context: Context, title: String, body: String, data: Map<String, String>): Boolean {
        if (data["type"] != "event") return false
        val link = data["link"].orEmpty()
        val id = data["eventId"]?.toIntOrNull() ?: (title + body).hashCode()
        val tap = PendingIntent.getActivity(context, id, openIntent(context, link), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // Without the notification permission the phone drops it; nothing else to do.
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
        return true
    }

    /** Joins and leaves Firebase topics (does nothing without Firebase). */
    fun topics(subscribe: Set<String>, unsubscribe: Set<String>) {
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            subscribe.forEach { fm.subscribeToTopic(it) }
            unsubscribe.forEach { fm.unsubscribeFromTopic(it) }
        }
    }

    /** Calls [onToken] with this phone's push token once Firebase has one (never, without Firebase). */
    fun token(onToken: (String) -> Unit) {
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { if (!it.isNullOrBlank()) onToken(it) } }
    }
}

/** Firebase's entry point: a new token is registered with the server; open-app messages are shown. */
class StudentMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val graph = (application as GraphOwner).graph
        graph.notify.saveToken(token)
        graph.profiles.get()?.let { if (graph.notify.on) graph.syncNotifyPrefs(it) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        Notify.show(this, n?.title.orEmpty(), n?.body.orEmpty(), message.data)
    }
}
