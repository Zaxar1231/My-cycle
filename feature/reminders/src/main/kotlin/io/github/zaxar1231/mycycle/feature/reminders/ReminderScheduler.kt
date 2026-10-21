package io.github.zaxar1231.mycycle.feature.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.reminders.ReminderSettings
import io.github.zaxar1231.mycycle.core.domain.reminders.Reminders
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the reminder jobs in line with the settings and the medication list. Each job is a
 * one-time job that schedules the next one itself; WorkManager runs it close to the chosen time
 * without exact alarms, so no extra permission is needed.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    // Lazy: the medication list lives in the database, which opens only after the key check.
    private val medicationRepository: dagger.Lazy<MedicationRepository>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private val started = AtomicBoolean(false)
    private val work get() = WorkManager.getInstance(context)

    /** What the jobs were last set up for in this process, to leave unchanged ones alone. */
    private var scheduled: Pair<ReminderSettings, Map<Long, LocalTime>>? = null

    /** Called once the database may be opened (after the key check). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        ReminderNotifications.createChannels(context)
        scope.launch {
            combine(settings.settings.map { it.reminders }, medicationRepository.get().observeMedications()) { reminders, meds ->
                reminders to meds.filter { it.isActive && it.reminderTime != null }.associate { it.id to it.reminderTime!! }
            }
                .distinctUntilChanged()
                .catch { /* An unreadable database: no reminders until the app opens it again. */ }
                .collect { (reminders, meds) -> sync(reminders, meds) }
        }
    }

    /**
     * Jobs already waiting keep their time (an app start must not push today's reminder to
     * tomorrow); a changed time replaces the job, and switched-off reminders are cancelled.
     */
    private fun sync(reminders: ReminderSettings, meds: Map<Long, LocalTime>) {
        val previous = scheduled
        val cycleOn = reminders.periodSoon || reminders.fertileWindow || reminders.ovulation
        if (cycleOn) enqueue<CycleReminderWorker>(CYCLE_WORK, ReminderSettings.CYCLE_REMINDER_TIME, ExistingWorkPolicy.KEEP) else work.cancelUniqueWork(CYCLE_WORK)

        if (reminders.dailyLog) {
            val changed = previous != null && previous.first.dailyLogTime != reminders.dailyLogTime
            enqueue<DailyLogWorker>(LOG_WORK, reminders.dailyLogTime, if (changed) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
        } else {
            work.cancelUniqueWork(LOG_WORK)
        }

        if (!reminders.medications) {
            work.cancelAllWorkByTag(MEDICATION_TAG)
        } else {
            val before = previous?.second.orEmpty()
            (before.keys - meds.keys).forEach { work.cancelUniqueWork(medicationWork(it)) }
            meds.forEach { (id, time) ->
                val policy = if (before[id] != null && before[id] != time) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
                enqueueMedication(id, time, policy)
            }
        }
        scheduled = reminders to meds
    }

    /** From a finished job: the next cycle reminder, tomorrow morning. */
    fun scheduleNextCycle() = enqueue<CycleReminderWorker>(CYCLE_WORK, ReminderSettings.CYCLE_REMINDER_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** From a finished job: the next evening's log reminder, from the current settings. */
    suspend fun scheduleNextDailyLog() {
        val reminders = settings.settings.first().reminders
        if (reminders.dailyLog) enqueue<DailyLogWorker>(LOG_WORK, reminders.dailyLogTime, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** From a finished job: the same medication tomorrow. */
    fun scheduleNextMedication(medication: Medication) {
        val time = medication.reminderTime ?: return
        enqueueMedication(medication.id, time, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueMedication(id: Long, time: LocalTime, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<MedicationWorker>()
            .setInitialDelay(delayUntil(time))
            .setInputData(workDataOf(MedicationWorker.KEY_ID to id))
            .addTag(MEDICATION_TAG)
            .build()
        work.enqueueUniqueWork(medicationWork(id), policy, request)
    }

    private inline fun <reified W : androidx.work.ListenableWorker> enqueue(name: String, time: LocalTime, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<W>().setInitialDelay(delayUntil(time)).build()
        work.enqueueUniqueWork(name, policy, request)
    }

    /** Time-zone aware, so a clock change keeps the reminder at the same wall-clock time. */
    private fun delayUntil(time: LocalTime): Duration {
        val now = ZonedDateTime.now()
        val next = Reminders.nextAt(now.toLocalDateTime(), time).atZone(now.zone)
        return Duration.between(now, next)
    }

    private fun medicationWork(id: Long) = "$MEDICATION_WORK$id"

    private companion object {
        const val CYCLE_WORK = "cycle-reminders-daily"
        const val LOG_WORK = "log-reminder"
        const val MEDICATION_WORK = "medication-reminder-"
        const val MEDICATION_TAG = "medication-reminder"
    }
}
