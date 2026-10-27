package com.engquest.teacher.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.engquest.teacher.MainActivity
import com.engquest.teacher.R

/**
 * "A student sent a submission" notifications. While the app is closed the phone shows the
 * server's own notification (in the same channel, see the manifest); while it is open,
 * [EqMessagingService] shows the same one through [show].
 */
object SubmissionNotifications {
    const val CHANNEL = "submissions"
    /** 0, as the phone uses for the server's own notification, so the same tag replaces it. */
    const val ID = 0

    /** Makes the "Submissions" channel (safe to call again). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Submissions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a student sends a submission"
            },
        )
    }

    /**
     * Shows a submission message ([data] as the server sends it). One notification per student
     * (tag `sub-{className}-{studentNo}`, as the server's), so a burst of uploads replaces it.
     * Anything else is ignored. Tapping it opens that student's submissions.
     */
    fun show(context: Context, title: String, body: String, data: Map<String, String>) {
        if (data["type"] != "submission") return
        val className = data["className"]?.trim().orEmpty()
        val studentNo = data["studentNo"]?.trim().orEmpty()
        if (className.isEmpty() || studentNo.isEmpty()) return
        val tag = "sub-$className-$studentNo"

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_CLASS, className)
            .putExtra(MainActivity.EXTRA_STUDENT, studentNo)
        val tap = PendingIntent.getActivity(
            context,
            // One request code per student, so each notification keeps its own student.
            tag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        // Without the notification permission the phone drops it; nothing else to do.
        runCatching { NotificationManagerCompat.from(context).notify(tag, ID, notification) }
    }
}
