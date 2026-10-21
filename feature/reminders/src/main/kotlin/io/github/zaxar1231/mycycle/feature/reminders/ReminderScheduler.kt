package io.github.zaxar1231.mycycle.feature.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.reminders.ReminderSettings
import io.github.zaxar1231.mycycle.core.domain.reminders.Reminders
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the reminder jobs in line with the settings and the medication list. WorkManager runs them
 * close to the chosen time without exact alarms, so no extra permission is needed.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val medications: MedicationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    private val work get() = WorkManager.getInstance(context)

    /** Called once the database may be opened (after the key check). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        ReminderNotifications.createChannels(context)
        scope.launch {
            combine(settings.settings.map { it.reminders }, medications.observeMedications()) { reminders, meds -> reminders to meds }
                .distinctUntilChanged()
                .collect { (reminders, meds) -> apply(reminders, meds) }
        }
    }

    private fun apply(reminders: ReminderSettings, meds: List<Medication>) {
        if (reminders.periodSoon || reminders.fertileWindow || reminders.ovulation) {
            val request = PeriodicWorkRequestBuilder<CycleReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(ReminderSettings.CYCLE_REMINDER_TIME))
                .build()
            work.enqueueUniquePeriodicWork(CYCLE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(CYCLE_WORK)
        }
        if (reminders.dailyLog) enqueueDailyLog(reminders.dailyLogTime) else work.cancelUniqueWork(LOG_WORK)
        work.cancelAllWorkByTag(MEDICATION_TAG)
        if (reminders.medications) meds.filter { it.isActive && it.reminderTime != null }.forEach(::scheduleMedication)
    }

    /** The next evening's log reminder, from the current settings. */
    suspend fun scheduleDailyLog() {
        val reminders = settings.settings.first().reminders
        if (reminders.dailyLog) enqueueDailyLog(reminders.dailyLogTime)
    }

    fun scheduleMedication(medication: Medication) {
        val time = medication.reminderTime ?: return
        val request = OneTimeWorkRequestBuilder<MedicationWorker>()
            .setInitialDelay(delayUntil(time))
            .setInputData(workDataOf(MedicationWorker.KEY_ID to medication.id))
            .addTag(MEDICATION_TAG)
            .build()
        work.enqueueUniqueWork("$MEDICATION_WORK${medication.id}", ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueDailyLog(time: LocalTime) {
        val request = OneTimeWorkRequestBuilder<DailyLogWorker>().setInitialDelay(delayUntil(time)).build()
        work.enqueueUniqueWork(LOG_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        return Duration.between(now, Reminders.nextAt(now, time))
    }

    private companion object {
        const val CYCLE_WORK = "cycle-reminders"
        const val LOG_WORK = "log-reminder"
        const val MEDICATION_WORK = "medication-reminder-"
        const val MEDICATION_TAG = "medication-reminder"
    }
}
