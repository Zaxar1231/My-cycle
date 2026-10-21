package io.github.zaxar1231.mycycle.core.sync.partner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.zaxar1231.mycycle.core.sync.R

/**
 * Local notifications for the partner. Made on the phone; nothing goes through a push service.
 * The lock screen shows only a neutral line: the period belongs to someone else's health data.
 */
object PartnerNotifications {
    private const val CHANNEL_ID = "partner"
    private const val NOTIFICATION_ID = 20

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.partner_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    internal fun periodSoon(context: Context, daysLeft: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (daysLeft <= 0) {
            context.getString(R.string.partner_notice_today)
        } else {
            context.resources.getQuantityString(R.plurals.partner_notice_days, daysLeft, daysLeft)
        }
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_partner_notification)
            .setContentTitle(context.getString(R.string.partner_notice_public))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_partner_notification)
            .setContentTitle(context.getString(R.string.partner_notice_title))
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
