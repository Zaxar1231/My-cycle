package io.github.zaxar1231.mycycle.core.domain.insights

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleSegment
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InsightsTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val prediction = CyclePredictor().predict(
        CyclePredictor.Input(
            cycles = listOf(Cycle(id = 1, startDate = start, periodLength = 5)),
            usualCycleLength = 28,
            usualPeriodLength = 5,
            usualLengthsEstimated = false,
            today = start.plusDays(3),
        ),
    )
    private val ovulation = start.plusDays(13)

    @Test
    fun `chance is highest around ovulation and low elsewhere`() {
        val atOvulation = PregnancyChanceModel.on(ovulation, prediction)!!
        assertEquals(ChanceLevel.HIGH, atOvulation.level)
        assertEquals(33, atOvulation.percent)
        assertEquals(0, atOvulation.daysFromOvulation)

        assertEquals(ChanceLevel.MEDIUM, PregnancyChanceModel.on(ovulation.minusDays(4), prediction)!!.level)
        assertEquals(ChanceLevel.LOW, PregnancyChanceModel.on(ovulation.plusDays(2), prediction)!!.level)
        assertEquals(ChanceLevel.LOW, PregnancyChanceModel.on(start, prediction)!!.level)
        assertNull(PregnancyChanceModel.on(start.minusDays(1), prediction))
    }

    @Test
    fun `a short cycle can be fertile during the period`() {
        val short = CyclePredictor().predict(
            CyclePredictor.Input(
                cycles = listOf(Cycle(id = 1, startDate = start, periodLength = 7)),
                usualCycleLength = 21,
                usualPeriodLength = 7,
                usualLengthsEstimated = false,
                today = start.plusDays(1),
            ),
        )
        // Ovulation on day 7: day 5 of the period is two days before it.
        assertEquals(ChanceLevel.HIGH, PregnancyChanceModel.on(start.plusDays(4), short)!!.level)
    }

    @Test
    fun `hormone curves peak where expected and stretch with the cycle`() {
        for (length in listOf(24, 28, 35)) {
            val ovulationDay = length - 14
            val lh = HormoneModel.curve(Hormone.LH, length, ovulationDay)
            assertEquals(ovulationDay, lh.indexOf(lh.max()) + 1)
            val progesterone = HormoneModel.curve(Hormone.PROGESTERONE, length, ovulationDay)
            assertEquals(ovulationDay + 7, progesterone.indexOf(progesterone.max()) + 1)
            Hormone.entries.forEach { hormone ->
                assertTrue(HormoneModel.curve(hormone, length, ovulationDay).all { it in 0.0..1.0 })
            }
        }
        assertEquals(HormoneLevel.HIGH, HormoneModel.levelOf(HormoneModel.level(Hormone.LH, 14, 28, 14)))
        assertEquals(HormoneLevel.LOW, HormoneModel.levelOf(HormoneModel.level(Hormone.PROGESTERONE, 3, 28, 14)))
    }

    @Test
    fun `phase ranges cover the whole cycle in order`() {
        val segment = CycleSegment(start, length = 30, periodLength = 5, ovulation = start.plusDays(15), ovulationConfirmed = false, isPredicted = false)
        val ranges = PhaseAnalysis.ranges(segment)
        assertEquals(listOf(CyclePhase.MENSTRUAL, CyclePhase.FOLLICULAR, CyclePhase.OVULATORY, CyclePhase.LUTEAL), ranges.keys.toList())
        assertEquals(1..5, ranges[CyclePhase.MENSTRUAL])
        assertEquals(15..17, ranges[CyclePhase.OVULATORY])
        assertEquals(30, ranges.getValue(CyclePhase.LUTEAL).last)
    }
}
