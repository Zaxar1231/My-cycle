package io.github.zaxar1231.mycycle.core.domain.cycle

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.BBT_BASELINE_DAYS
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.BBT_MAX_RISE_CELSIUS
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.BBT_MIN_RISE_CELSIUS
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.BBT_SHIFT_DAYS
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.FERTILE_DAYS_BEFORE_OVULATION
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.LUTEAL_PHASE_LENGTH
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.MAX_REGULAR_CYCLE_LENGTH
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.MIN_CYCLES_FOR_HISTORY
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.MIN_REGULAR_CYCLE_LENGTH
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.OVULATORY_PHASE_HALF_WIDTH
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.PREDICTED_FUTURE_CYCLES
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.PREDICTION_CYCLE_COUNT
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Cycle predictions. Pure function of its input, so it is fully unit tested.
 *
 * - Average lengths: weighted average of the last [PREDICTION_CYCLE_COUNT] regular cycles,
 *   newer cycles weigh more. Cycles outside [MIN_REGULAR_CYCLE_LENGTH]..[MAX_REGULAR_CYCLE_LENGTH]
 *   stay in history but do not affect the average.
 * - With fewer than [MIN_CYCLES_FOR_HISTORY] regular cycles the onboarding values are used.
 * - Ovulation ≈ next period − [LUTEAL_PHASE_LENGTH]; fertile window = [FERTILE_DAYS_BEFORE_OVULATION]
 *   days before ovulation plus the ovulation day.
 * - A basal temperature shift in the current cycle confirms ovulation and moves the next period.
 */
class CyclePredictor @Inject constructor() {

    data class Input(
        val cycles: List<Cycle>,
        val usualCycleLength: Int,
        val usualPeriodLength: Int,
        /** True when the user did not know their lengths during onboarding. */
        val usualLengthsEstimated: Boolean,
        /** Basal body temperature by date, °C. */
        val temperatures: Map<LocalDate, Double> = emptyMap(),
        val today: LocalDate,
    )

    fun predict(input: Input): CyclePrediction {
        val starts = input.cycles.filterNot { it.isPredicted }.sortedBy { it.startDate }
        val lengths = starts.zipWithNext { a, b -> days(a.startDate, b.startDate) }
        val regular = lengths.filter { it in MIN_REGULAR_CYCLE_LENGTH..MAX_REGULAR_CYCLE_LENGTH }.takeLast(PREDICTION_CYCLE_COUNT)

        val source = when {
            regular.size >= MIN_CYCLES_FOR_HISTORY -> PredictionSource.HISTORY
            input.usualLengthsEstimated -> PredictionSource.DEFAULTS
            else -> PredictionSource.ONBOARDING
        }
        val cycleLength = if (source == PredictionSource.HISTORY) weightedAverage(regular) else input.usualCycleLength
        val periodLengths = starts.takeLast(PREDICTION_CYCLE_COUNT).map { it.periodLength }
        val periodLength = if (source == PredictionSource.HISTORY) weightedAverage(periodLengths) else input.usualPeriodLength

        val timeline = buildTimeline(starts, lengths, cycleLength, periodLength, input.temperatures, input.today)
        return CyclePrediction(
            source = source,
            confidence = confidence(source, regular),
            averageCycleLength = cycleLength,
            averagePeriodLength = periodLength,
            today = todayStatus(timeline, input.today),
            timeline = timeline,
        )
    }

    private fun buildTimeline(
        starts: List<Cycle>,
        lengths: List<Int>,
        cycleLength: Int,
        periodLength: Int,
        temperatures: Map<LocalDate, Double>,
        today: LocalDate,
    ): List<CycleSegment> {
        if (starts.isEmpty()) return emptyList()
        val logged = starts.mapIndexed { index, cycle ->
            val isCurrent = index == starts.lastIndex
            val shift = if (isCurrent) detectTemperatureShift(cycle.startDate, temperatures) else null
            val expectedLength = when {
                !isCurrent -> lengths[index]
                shift != null -> days(cycle.startDate, shift) + 1 + LUTEAL_PHASE_LENGTH
                else -> cycleLength
            }
            val ovulation = shift ?: cycle.startDate.plusDays(expectedLength - LUTEAL_PHASE_LENGTH - 1L)
            // A late period stretches the current cycle up to today, so predictions never lie in the past.
            val length = if (isCurrent) maxOf(expectedLength, days(cycle.startDate, today)) else expectedLength
            CycleSegment(cycle.startDate, length, cycle.periodLength, ovulation, shift != null, isPredicted = false)
        }
        val future = generateSequence(logged.last().let { it.start.plusDays(it.length.toLong()) }) {
            it.plusDays(cycleLength.toLong())
        }.take(PREDICTED_FUTURE_CYCLES).map { start ->
            CycleSegment(
                start = start,
                length = cycleLength,
                periodLength = periodLength,
                ovulation = start.plusDays(cycleLength - LUTEAL_PHASE_LENGTH - 1L),
                ovulationConfirmed = false,
                isPredicted = true,
            )
        }
        return logged + future
    }

    private fun todayStatus(timeline: List<CycleSegment>, today: LocalDate): TodayStatus? {
        val current = timeline.lastOrNull { !it.isPredicted } ?: return null
        if (today.isBefore(current.start)) return null
        val cycleDay = days(current.start, today) + 1
        val nextPeriod = current.start.plusDays(current.length.toLong())
        val expectedPeriod = current.ovulation.plusDays(LUTEAL_PHASE_LENGTH + 1L)
        // Predicted cycles never start before today, so a later ovulation always exists.
        val upcomingOvulation = timeline.first { !it.ovulation.isBefore(today) }.ovulation
        return TodayStatus(
            cycleDay = cycleDay,
            phase = phaseOf(cycleDay, current),
            nextPeriodStart = nextPeriod,
            daysUntilNextPeriod = days(today, nextPeriod),
            daysLate = days(expectedPeriod, today).coerceAtLeast(0),
            ovulation = upcomingOvulation,
            daysUntilOvulation = days(today, upcomingOvulation),
            fertileWindow = upcomingOvulation.minusDays(FERTILE_DAYS_BEFORE_OVULATION.toLong())..upcomingOvulation,
            currentOvulationConfirmed = current.ovulationConfirmed,
        )
    }

    companion object {
        fun phaseOf(cycleDay: Int, segment: CycleSegment): CyclePhase {
            val ovulationDay = days(segment.start, segment.ovulation) + 1
            return when {
                cycleDay <= segment.periodLength -> CyclePhase.MENSTRUAL
                cycleDay < ovulationDay - OVULATORY_PHASE_HALF_WIDTH -> CyclePhase.FOLLICULAR
                cycleDay <= ovulationDay + OVULATORY_PHASE_HALF_WIDTH -> CyclePhase.OVULATORY
                else -> CyclePhase.LUTEAL
            }
        }

        /** Newest values weigh most: weights 1, 2, …, n from oldest to newest. */
        fun weightedAverage(values: List<Int>): Int {
            require(values.isNotEmpty())
            val weights = values.indices.map { it + 1 }
            return (values.zip(weights).sumOf { (v, w) -> v * w }.toDouble() / weights.sum()).roundToInt()
        }

        fun confidence(source: PredictionSource, regularLengths: List<Int>): PredictionConfidence {
            if (source != PredictionSource.HISTORY) return PredictionConfidence.LOW
            val mean = regularLengths.average()
            val deviation = sqrt(regularLengths.sumOf { (it - mean) * (it - mean) } / regularLengths.size)
            return when {
                regularLengths.size >= CycleConstants.HIGH_CONFIDENCE_MIN_CYCLES &&
                    deviation <= CycleConstants.HIGH_CONFIDENCE_MAX_DEVIATION_DAYS -> PredictionConfidence.HIGH
                deviation <= CycleConstants.MEDIUM_CONFIDENCE_MAX_DEVIATION_DAYS -> PredictionConfidence.MEDIUM
                else -> PredictionConfidence.LOW
            }
        }

        /**
         * Ovulation day from a basal temperature shift: the first day after which [BBT_SHIFT_DAYS]
         * consecutive readings are [BBT_MIN_RISE_CELSIUS]..[BBT_MAX_RISE_CELSIUS] above the highest of the
         * previous [BBT_BASELINE_DAYS] readings. Bigger jumps look like fever and are ignored.
         */
        fun detectTemperatureShift(cycleStart: LocalDate, temperatures: Map<LocalDate, Double>): LocalDate? {
            val readings = temperatures.filterKeys { !it.isBefore(cycleStart) }.toSortedMap().toList()
            for (i in BBT_BASELINE_DAYS..readings.size - BBT_SHIFT_DAYS) {
                val baseline = readings.subList(i - BBT_BASELINE_DAYS, i).maxOf { it.second }
                val shift = readings.subList(i, i + BBT_SHIFT_DAYS)
                val consecutive = shift.zipWithNext().all { (a, b) -> days(a.first, b.first) == 1 } &&
                    days(readings[i - 1].first, shift.first().first) == 1
                // Compared in hundredths of a degree: 36.4 − 36.2 is 0.19999… in floating point.
                val risen = shift.all { hundredths(it.second - baseline) in hundredths(BBT_MIN_RISE_CELSIUS)..hundredths(BBT_MAX_RISE_CELSIUS) }
                if (consecutive && risen) return readings[i - 1].first
            }
            return null
        }

        private fun days(from: LocalDate, to: LocalDate) = ChronoUnit.DAYS.between(from, to).toInt()

        private fun hundredths(celsius: Double) = (celsius * HUNDREDTHS).roundToInt()

        private const val HUNDREDTHS = 100
    }
}
