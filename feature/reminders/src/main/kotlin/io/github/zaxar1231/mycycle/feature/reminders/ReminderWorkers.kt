package io.github.zaxar1231.mycycle.feature.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.zaxar1231.mycycle.core.domain.reminders.CycleReminder
import io.github.zaxar1231.mycycle.core.domain.reminders.Reminders
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderDependencies {
    fun settings(): SettingsRepository
    fun prediction(): ObserveCyclePredictionUseCase
    fun dayLogs(): DayLogRepository
    fun medications(): MedicationRepository
    fun dates(): DateProvider
    fun scheduler(): ReminderScheduler
}

private fun Context.reminderDependencies() = EntryPointAccessors.fromApplication(applicationContext, ReminderDependencies::class.java)

/** Runs [block]; a database that cannot be read (for example after data loss) skips the reminder. */
private suspend fun quietly(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Nothing to remind about this time.
    }
}

/** Once a day in the morning: the period soon, the fertile window, ovulation. */
class CycleReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = applicationContext.reminderDependencies()
        val today = deps.dates().today()
        // A periodic run can land twice on one day; only the first one reminds.
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_RUN, null) == today.toString()) return Result.success()
        quietly {
            val settings = deps.settings().settings.first().reminders
            val prediction = deps.prediction()(today).first()
            Reminders.dueOn(prediction, today, settings).forEach { reminder ->
                val (title, text) = textOf(reminder)
                ReminderNotifications.post(applicationContext, ReminderNotifications.CHANNEL_CYCLE, idOf(reminder), title, text)
            }
        }
        prefs.edit().putString(LAST_RUN, today.toString()).apply()
        return Result.success()
    }

    private fun textOf(reminder: CycleReminder): Pair<String, String> {
        val c = applicationContext
        return when (reminder) {
            is CycleReminder.PeriodSoon -> if (reminder.days == 0) {
                c.getString(R.string.reminder_period_today_title) to c.getString(R.string.reminder_period_text)
            } else {
                c.resources.getQuantityString(R.plurals.reminder_period_soon_title, reminder.days, reminder.days) to
                    c.getString(R.string.reminder_period_text)
            }
            CycleReminder.FertileWindowStarts -> c.getString(R.string.reminder_fertile_title) to c.getString(R.string.reminder_forecast_text)
            CycleReminder.Ovulation -> c.getString(R.string.reminder_ovulation_title) to c.getString(R.string.reminder_forecast_text)
        }
    }

    private fun idOf(reminder: CycleReminder) = when (reminder) {
        is CycleReminder.PeriodSoon -> ID_PERIOD
        CycleReminder.FertileWindowStarts -> ID_FERTILE
        CycleReminder.Ovulation -> ID_OVULATION
    }

    private companion object {
        const val PREFS = "reminders"
        const val LAST_RUN = "cycle_last_run"
        const val ID_PERIOD = 101
        const val ID_FERTILE = 102
        const val ID_OVULATION = 103
    }
}

/** In the evening, if today has no log yet. Schedules the next evening itself. */
class DailyLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = applicationContext.reminderDependencies()
        quietly {
            val settings = deps.settings().settings.first().reminders
            if (settings.dailyLog && deps.dayLogs().get(deps.dates().today()) == null) {
                ReminderNotifications.post(
                    applicationContext,
                    ReminderNotifications.CHANNEL_LOG,
                    ID_LOG,
                    applicationContext.getString(R.string.reminder_log_title),
                    applicationContext.getString(R.string.reminder_log_text),
                )
            }
        }
        deps.scheduler().scheduleDailyLog()
        return Result.success()
    }

    private companion object {
        const val ID_LOG = 110
    }
}

/** At a medication's reminder time. Schedules the next day itself. */
class MedicationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = applicationContext.reminderDependencies()
        val id = inputData.getLong(KEY_ID, -1L)
        quietly {
            val on = deps.settings().settings.first().reminders.medications
            val medication = deps.medications().observeMedications().first().firstOrNull { it.id == id }
            if (on && medication != null && medication.isActive && medication.reminderTime != null) {
                val text = medication.dosage?.takeIf { it.isNotBlank() }
                    ?.let { applicationContext.getString(R.string.reminder_medication_dose, medication.name, it) }
                    ?: medication.name
                ReminderNotifications.post(
                    applicationContext,
                    ReminderNotifications.CHANNEL_MEDICATIONS,
                    ID_MEDICATION_BASE + (id % MAX_IDS).toInt(),
                    applicationContext.getString(R.string.reminder_medication_title),
                    text,
                )
                deps.scheduler().scheduleMedication(medication)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_ID = "medication_id"
        private const val ID_MEDICATION_BASE = 1_000
        private const val MAX_IDS = 10_000
    }
}
