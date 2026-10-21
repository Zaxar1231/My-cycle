package io.github.zaxar1231.mycycle.core.domain.reminders

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Which reminders are on. Everything is local; nothing goes through a push service. */
data class ReminderSettings(
    val periodSoon: Boolean = true,
    val periodDaysBefore: Int = DEFAULT_DAYS_BEFORE,
    val fertileWindow: Boolean = false,
    val ovulation: Boolean = false,
    val dailyLog: Boolean = false,
    val dailyLogTime: LocalTime = DEFAULT_LOG_TIME,
    val medications: Boolean = true,
) {
    companion object {
        const val DEFAULT_DAYS_BEFORE = 2
        val DAYS_BEFORE_OPTIONS = listOf(1, 2, 3, 5)
        val DEFAULT_LOG_TIME: LocalTime = LocalTime.of(21, 0)
        val LOG_TIME_OPTIONS: List<LocalTime> = listOf(8, 12, 19, 21, 22).map { LocalTime.of(it, 0) }

        /** Cycle reminders come once a day at this time. */
        val CYCLE_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** A cycle reminder for today. */
sealed interface CycleReminder {
    /** [days] is 0 when the period is expected today. */
    data class PeriodSoon(val days: Int) : CycleReminder
    data object FertileWindowStarts : CycleReminder
    data object Ovulation : CycleReminder
}

object Reminders {
    /** The cycle reminders due on [today]; approximate, like every forecast. */
    fun dueOn(prediction: CyclePrediction, today: LocalDate, settings: ReminderSettings): List<CycleReminder> {
        val status = prediction.today ?: return emptyList()
        val due = mutableListOf<CycleReminder>()
        // Not while the period is late: it would repeat every day.
        if (settings.periodSoon && status.daysLate == 0) {
            val days = ChronoUnit.DAYS.between(today, status.nextPeriodStart).toInt()
            if (days == settings.periodDaysBefore || days == 0) due += CycleReminder.PeriodSoon(days)
        }
        if (settings.fertileWindow && status.fertileWindow.start == today) due += CycleReminder.FertileWindowStarts
        if (settings.ovulation && status.ovulation == today) due += CycleReminder.Ovulation
        return due
    }

    /** The next moment at [time] after [now]: later today, or tomorrow. */
    fun nextAt(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
