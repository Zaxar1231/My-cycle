package io.github.zaxar1231.mycycle.core.update

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Periodic background check that posts a notification when a new release appears. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = UpdatePrefs(applicationContext)
        if (!prefs.autoCheck) return Result.success()
        val result = try {
            UpdateChecker.check(applicationContext.appVersionName())
        } catch (e: Exception) {
            return Result.retry()
        }
        if (result is CheckResult.Available && prefs.notifiedVersion != result.release.version) {
            notify(applicationContext, result.release)
            prefs.notifiedVersion = result.release.version
        }
        return Result.success()
    }

    companion object {
        // Renamed when the worker class moved to :core:update: the old entry points to a class that no longer exists.
        private const val WORK_NAME = "update-check-v2"
        private const val LEGACY_WORK_NAME = "update-check"
        const val CHANNEL_ID = "updates"

        /** Set on the launch intent from the update notification: the app checks again on open. */
        const val EXTRA_CHECK_UPDATE = "check_update"
        private const val NOTIFICATION_ID = 1

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(LEGACY_WORK_NAME)
                enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(LEGACY_WORK_NAME)
                cancelUniqueWork(WORK_NAME)
            }
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notify(context: Context, release: ReleaseInfo) {
            // The runtime permission exists from Android 13; before that notifications are allowed by default.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CHECK_UPDATE, true)
            val pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.update_available_title, release.version))
                .setContentText(context.getString(R.string.notification_update_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
