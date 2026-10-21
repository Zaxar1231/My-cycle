package io.github.zaxar1231.mycycle.core.domain.cycle

import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CyclePredictorTest {
    private val predictor = CyclePredictor()
    private val day0 = LocalDate.of(2026, 1, 1)

    /** Cycles starting at day0 with the given lengths; the last start is the current cycle. */
    private fun cycles(vararg lengths: Int, periodLength: Int = 5): List<Cycle> {
        var start = day0
        val result = mutableListOf(Cycle(startDate = start, periodLength = periodLength))
        lengths.forEach {
            start = start.plusDays(it.toLong())
            result += Cycle(startDate = start, periodLength = periodLength)
        }
        return result
    }

    private fun input(
        cycles: List<Cycle>,
        today: LocalDate,
        usualCycle: Int = 30,
        usualPeriod: Int = 4,
        estimated: Boolean = false,
        temps: Map<LocalDate, Double> = emptyMap(),
    ) = CyclePredictor.Input(cycles, usualCycle, usualPeriod, estimated, temps, today)

    @Nested
    inner class Averages {
        @Test
        fun `weighted average favours recent cycles`() {
            assertEquals(30, CyclePredictor.weightedAverage(listOf(26, 30, 32)))
            assertEquals(28, CyclePredictor.weightedAverage(listOf(28)))
        }

        @Test
        fun `uses only the last six regular cycles`() {
            val p = predictor.predict(input(cycles(40, 40, 28, 28, 28, 28, 28, 28), today = day0))
            assertEquals(28, p.averageCycleLength)
        }

        @Test
        fun `outliers stay in history but not in the average`() {
            val c = cycles(28, 60, 28, 15, 28)
            val p = predictor.predict(input(c, today = c.last().startDate))
            assertEquals(28, p.averageCycleLength)
            assertEquals(PredictionSource.HISTORY, p.source)
            assertEquals(60, p.timeline[1].length)
            assertEquals(15, p.timeline[3].length)
        }

        @Test
        fun `period length comes from history`() {
            val c = cycles(28, 28, periodLength = 6)
            assertEquals(6, predictor.predict(input(c, today = c.last().startDate)).averagePeriodLength)
        }
    }

    @Nested
    inner class Fallbacks {
        @Test
        fun `fewer than two cycles use onboarding values`() {
            val c = cycles(28)
            val p = predictor.predict(input(c, today = c.last().startDate, usualCycle = 32, usualPeriod = 4))
            assertEquals(PredictionSource.ONBOARDING, p.source)
            assertEquals(32, p.averageCycleLength)
            assertEquals(4, p.averagePeriodLength)
            assertEquals(PredictionConfidence.LOW, p.confidence)
        }

        @Test
        fun `unknown onboarding values are marked as defaults`() {
            val p = predictor.predict(input(cycles(), today = day0, usualCycle = 28, usualPeriod = 5, estimated = true))
            assertEquals(PredictionSource.DEFAULTS, p.source)
            assertEquals(PredictionConfidence.LOW, p.confidence)
        }

        @Test
        fun `no cycles means no today status and an empty timeline`() {
            val p = predictor.predict(input(emptyList(), today = day0))
            assertNull(p.today)
            assertTrue(p.timeline.isEmpty())
        }

        @Test
        fun `predicted cycles are ignored as input`() {
            val c = cycles(28) + Cycle(startDate = day0.plusDays(56), periodLength = 5, isPredicted = true)
            assertEquals(PredictionSource.ONBOARDING, predictor.predict(input(c, today = day0.plusDays(30))).source)
        }
    }

    @Nested
    inner class Today {
        @Test
        fun `ovulation is cycle length minus fourteen and the fertile window is six days`() {
            val today = day0.plusDays(9)
            val status = predictor.predict(input(cycles(), today = today, usualCycle = 28)).today!!
            assertEquals(10, status.cycleDay)
            assertEquals(day0.plusDays(13), status.ovulation) // cycle day 14
            assertEquals(day0.plusDays(8)..day0.plusDays(13), status.fertileWindow)
            assertEquals(day0.plusDays(28), status.nextPeriodStart)
            assertEquals(19, status.daysUntilNextPeriod)
            assertEquals(4, status.daysUntilOvulation)
        }

        @Test
        fun `phases follow the cycle`() {
            fun phase(day: Long) = predictor.predict(input(cycles(), today = day0.plusDays(day), usualCycle = 28, usualPeriod = 5)).today!!.phase
            assertEquals(CyclePhase.MENSTRUAL, phase(0))
            assertEquals(CyclePhase.MENSTRUAL, phase(4))
            assertEquals(CyclePhase.FOLLICULAR, phase(5))
            assertEquals(CyclePhase.OVULATORY, phase(12))
            assertEquals(CyclePhase.OVULATORY, phase(14))
            assertEquals(CyclePhase.LUTEAL, phase(15))
        }

        @Test
        fun `late period is reported and points to the next ovulation`() {
            val today = day0.plusDays(30)
            val status = predictor.predict(input(cycles(), today = today, usualCycle = 28)).today!!
            assertEquals(2, status.daysLate)
            assertEquals(31, status.cycleDay)
            assertTrue(status.daysUntilOvulation > 0)
        }

        @Test
        fun `today before the first logged period has no status`() {
            assertNull(predictor.predict(input(cycles(), today = day0.minusDays(1))).today)
        }
    }

    @Nested
    inner class Confidence {
        @Test
        fun `stable history gives high confidence`() {
            val c = cycles(28, 29, 28, 27, 28)
            assertEquals(PredictionConfidence.HIGH, predictor.predict(input(c, today = c.last().startDate)).confidence)
        }

        @Test
        fun `few cycles give medium confidence`() {
            val c = cycles(28, 29)
            assertEquals(PredictionConfidence.MEDIUM, predictor.predict(input(c, today = c.last().startDate)).confidence)
        }

        @Test
        fun `irregular history gives low confidence`() {
            val c = cycles(22, 40, 25, 38, 23)
            assertEquals(PredictionConfidence.LOW, predictor.predict(input(c, today = c.last().startDate)).confidence)
        }
    }

    @Nested
    inner class Temperature {
        private fun temps(baseline: Double, shiftFromDay: Int, rise: Double, days: Int = 20) =
            (0 until days).associate { d -> day0.plusDays(d.toLong()) to if (d >= shiftFromDay) baseline + rise else baseline }

        @Test
        fun `three high readings confirm ovulation the day before the shift`() {
            val shift = CyclePredictor.detectTemperatureShift(day0, temps(36.4, shiftFromDay = 16, rise = 0.3))
            assertEquals(day0.plusDays(15), shift)
        }

        @Test
        fun `confirmed ovulation moves the next period`() {
            val today = day0.plusDays(19)
            val p = predictor.predict(input(cycles(), today = today, usualCycle = 28, temps = temps(36.4, 16, 0.3)))
            val current = p.timeline.first()
            assertTrue(current.ovulationConfirmed)
            assertEquals(day0.plusDays(15), current.ovulation)
            assertEquals(30, current.length) // ovulation on cycle day 16, plus the luteal phase
            assertEquals(day0.plusDays(30), p.today!!.nextPeriodStart)
        }

        @Test
        fun `small rises and fever spikes are ignored`() {
            assertNull(CyclePredictor.detectTemperatureShift(day0, temps(36.4, 16, 0.1)))
            assertNull(CyclePredictor.detectTemperatureShift(day0, temps(36.4, 16, 1.0)))
        }

        @Test
        fun `a gap in readings breaks the shift`() {
            val t = temps(36.4, 16, 0.3).toMutableMap().apply { remove(day0.plusDays(17)) }
            assertNull(CyclePredictor.detectTemperatureShift(day0, t))
        }

        @Test
        fun `too few baseline readings detect nothing`() {
            assertNull(CyclePredictor.detectTemperatureShift(day0, temps(36.4, 3, 0.3, days = 6)))
        }
    }

    @Nested
    inner class Calendar {
        @Test
        fun `day kinds follow the timeline`() {
            val p = predictor.predict(input(cycles(), today = day0, usualCycle = 28, usualPeriod = 5))
            assertEquals(DayKind.PERIOD, p.dayKind(day0))
            assertEquals(DayKind.PERIOD, p.dayKind(day0.plusDays(4)))
            assertEquals(DayKind.OTHER, p.dayKind(day0.plusDays(5)))
            assertEquals(DayKind.FERTILE, p.dayKind(day0.plusDays(8)))
            assertEquals(DayKind.OVULATION, p.dayKind(day0.plusDays(13)))
            assertEquals(DayKind.LUTEAL, p.dayKind(day0.plusDays(14)))
            assertEquals(DayKind.PREDICTED_PERIOD, p.dayKind(day0.plusDays(28)))
            assertEquals(DayKind.OTHER, p.dayKind(day0.minusDays(1)))
        }

        @Test
        fun `three future cycles are predicted`() {
            val p = predictor.predict(input(cycles(), today = day0, usualCycle = 28))
            assertEquals(1 + CycleConstants.PREDICTED_FUTURE_CYCLES, p.timeline.size)
            assertFalse(p.timeline.first().isPredicted)
            assertTrue(p.timeline.drop(1).all { it.isPredicted })
            assertEquals(day0.plusDays(84), p.timeline.last().start)
        }
    }
}
