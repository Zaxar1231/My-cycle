package io.github.zaxar1231.mycycle.core.sync.partner

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Shares the owner's day or checks for the partner's reminder while the app is closed. */
class PartnerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun partnerSync(): PartnerSync
    }

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).partnerSync()
        return if (sync.runOnce()) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "partner-sync"
        private const val INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PartnerWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
