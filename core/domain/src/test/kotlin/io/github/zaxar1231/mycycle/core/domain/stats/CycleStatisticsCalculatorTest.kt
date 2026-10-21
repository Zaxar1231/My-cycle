package io.github.zaxar1231.mycycle.core.domain.stats

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.cycle.DayKind
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CycleStatisticsCalculatorTest {
    private val calculator = CycleStatisticsCalculator()
    private val first = LocalDate.of(2026, 6, 1)

    /** Cycles of the given lengths back to back, plus a current cycle started after them. */
    private fun cycles(vararg lengths: Int): List<Cycle> {
        var start = first
        return (lengths.toList() + 0).mapIndexed { i, length ->
            Cycle(id = i + 1L, startDate = start, periodLength = 5).also { start = start.plusDays(length.toLong()) }
        }
    }

    private fun prediction(cycles: List<Cycle>, today: LocalDate) = CyclePredictor().predict(
        CyclePredictor.Input(cycles, usualCycleLength = 28, usualPeriodLength = 5, usualLengthsEstimated = false, today = today),
    )

    @Test
    fun `history is newest first with the current cycle marked`() {
        val list = cycles(28, 30)
        val today = list.last().startDate.plusDays(3)
        val history = calculator.history(prediction(list, today), list, today)

        assertEquals(3, history.size)
        assertTrue(history.first().isCurrent)
        assertEquals(4, history.first().elapsedDays)
        assertEquals(first, history.last().start)
        assertEquals(28, history.last().segment.length)
        assertEquals(DayKind.PERIOD, history.last().days.first())
        assertTrue(DayKind.OVULATION in history.last().days)
    }

    @Test
    fun `summary averages completed cycles and judges regularity`() {
        val list = cycles(27, 29, 28)
        val today = list.last().startDate.plusDays(2)
        val summary = calculator.summary(calculator.history(prediction(list, today), list, today))

        assertEquals(3, summary.completedCount)
        assertEquals(28.0, summary.averageCycleLength)
        assertEquals(27, summary.shortestCycle)
        assertEquals(29, summary.longestCycle)
        assertEquals(Regularity.REGULAR, summary.regularity)
        // The current period is still running, so only completed periods count.
        assertEquals(5.0, summary.averagePeriodLength)
        assertEquals(list[2].startDate, summary.lastCompleted?.start)
    }

    @Test
    fun `a wide spread is irregular and two cycles are not enough`() {
        val wide = cycles(24, 38, 28)
        val today = wide.last().startDate
        assertEquals(Regularity.IRREGULAR, calculator.summary(calculator.history(prediction(wide, today), wide, today)).regularity)

        val few = cycles(28, 28)
        val day = few.last().startDate
        assertEquals(Regularity.NOT_ENOUGH_DATA, calculator.summary(calculator.history(prediction(few, day), few, day)).regularity)
    }

    @Test
    fun `symptoms on the same cycle days form a pattern`() {
        val list = cycles(28, 28, 28)
        val today = list.last().startDate.plusDays(5)
        val p = prediction(list, today)
        val logs = list.take(3).map { DayLog(date = it.startDate, symptoms = setOf(Symptom.HEADACHE)) } +
            DayLog(date = list[0].startDate.plusDays(15), symptoms = setOf(Symptom.HEADACHE, Symptom.ACNE))
        val stats = calculator.logs(p, calculator.history(p, list, today), logs)

        assertEquals(Symptom.HEADACHE to 4, stats.symptomFrequency.first())
        val pattern = stats.patterns.single()
        assertEquals(Symptom.HEADACHE, pattern.symptom)
        assertEquals(1, pattern.fromCycleDay)
        assertEquals(2, pattern.toCycleDay)
        assertEquals(3, pattern.occurrences)
        assertEquals(4, pattern.total)
    }

    @Test
    fun `scattered symptoms form no pattern`() {
        val list = cycles(28, 28)
        val today = list.last().startDate.plusDays(20)
        val p = prediction(list, today)
        val logs = listOf(0L, 10L, 20L).map { DayLog(date = list[0].startDate.plusDays(it), symptoms = setOf(Symptom.ACNE)) }
        assertTrue(calculator.logs(p, calculator.history(p, list, today), logs).patterns.isEmpty())
    }

    @Test
    fun `moods are grouped by phase and series are sorted`() {
        val list = cycles(28)
        val today = list.last().startDate.plusDays(1)
        val p = prediction(list, today)
        val logs = listOf(
            DayLog(date = first.plusDays(1), moods = setOf(Mood.TIRED), temperatureCelsius = 36.5),
            DayLog(date = first, moods = setOf(Mood.TIRED, Mood.CALM), weightKg = 60.0),
        )
        val stats = calculator.logs(p, calculator.history(p, list, today), logs)

        assertEquals(Mood.TIRED to 2, stats.moodsByPhase.getValue(CyclePhase.MENSTRUAL).first())
        assertEquals(listOf(first.plusDays(1) to 36.5), stats.temperatures)
        assertEquals(listOf(first to 60.0), stats.weights)
        assertEquals(listOf(first to 28), stats.cycleLengths)
    }

    @Test
    fun `chronology is newest first and places logs in the cycle`() {
        val list = cycles(28)
        val today = list.last().startDate.plusDays(1)
        val p = prediction(list, today)
        val before = DayLog(date = first.minusDays(3), note = "x")
        val entries = calculator.chronology(p, listOf(before, DayLog(date = first.plusDays(2), note = "y")))

        assertEquals(first.plusDays(2), entries.first().log.date)
        assertEquals(3, entries.first().cycleDay)
        assertEquals(CyclePhase.MENSTRUAL, entries.first().phase)
        assertNull(entries.last().cycleDay)
        assertNull(entries.last().phase)
    }
}
