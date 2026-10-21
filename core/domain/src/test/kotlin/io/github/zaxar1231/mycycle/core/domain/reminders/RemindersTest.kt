package io.github.zaxar1231.mycycle.core.domain.reminders

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RemindersTest {
    private val start = LocalDate.of(2026, 10, 1)

    private fun predictionOn(today: LocalDate) = CyclePredictor().predict(
        CyclePredictor.Input(
            cycles = listOf(Cycle(id = 1, startDate = start, periodLength = 5)),
            usualCycleLength = 28,
            usualPeriodLength = 5,
            usualLengthsEstimated = false,
            today = today,
        ),
    )

    @Test
    fun `the period reminder comes the chosen days before and on the day`() {
        val settings = ReminderSettings(periodDaysBefore = 2)
        val next = start.plusDays(28)
        assertEquals(listOf(CycleReminder.PeriodSoon(2)), Reminders.dueOn(predictionOn(next.minusDays(2)), next.minusDays(2), settings))
        assertEquals(listOf(CycleReminder.PeriodSoon(0)), Reminders.dueOn(predictionOn(next), next, settings))
        assertTrue(Reminders.dueOn(predictionOn(next.minusDays(1)), next.minusDays(1), settings).isEmpty())
    }

    @Test
    fun `no period reminder while it is late`() {
        val late = start.plusDays(31)
        assertTrue(Reminders.dueOn(predictionOn(late), late, ReminderSettings()).isEmpty())
    }

    @Test
    fun `fertile window and ovulation only when switched on`() {
        val fertile = start.plusDays(8)
        val ovulation = start.plusDays(13)
        assertTrue(Reminders.dueOn(predictionOn(fertile), fertile, ReminderSettings()).isEmpty())
        val all = ReminderSettings(fertileWindow = true, ovulation = true)
        assertEquals(listOf(CycleReminder.FertileWindowStarts), Reminders.dueOn(predictionOn(fertile), fertile, all))
        assertEquals(listOf(CycleReminder.Ovulation), Reminders.dueOn(predictionOn(ovulation), ovulation, all))
    }

    @Test
    fun `the next time is later today or tomorrow`() {
        val now = LocalDateTime.of(2026, 10, 21, 20, 30)
        assertEquals(LocalDateTime.of(2026, 10, 21, 21, 0), Reminders.nextAt(now, LocalTime.of(21, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 0), Reminders.nextAt(now, LocalTime.of(9, 0)))
    }
}
