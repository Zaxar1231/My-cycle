package io.github.zaxar1231.mycycle.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Local reminders. The lock screen shows only a neutral line; the details appear once the phone
 * is unlocked, because a period reminder is health data.
 */
object ReminderNotifications {
    const val CHANNEL_CYCLE = "cycle_reminders"
    const val CHANNEL_LOG = "log_reminders"
    const val CHANNEL_MEDICATIONS = "medication_reminders"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            CHANNEL_CYCLE to R.string.reminders_channel_cycle,
            CHANNEL_LOG to R.string.reminders_channel_log,
            CHANNEL_MEDICATIONS to R.string.reminders_channel_medications,
        ).forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        // The runtime permission exists from Android 13; before that notifications are allowed by default.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launch = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.reminders_public))
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
