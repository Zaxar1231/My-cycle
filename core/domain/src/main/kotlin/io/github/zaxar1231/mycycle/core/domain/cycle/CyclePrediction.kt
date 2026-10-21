package io.github.zaxar1231.mycycle.core.domain.cycle

import java.time.LocalDate

/** Where the average lengths come from. */
enum class PredictionSource {
    /** At least [CycleConstants.MIN_CYCLES_FOR_HISTORY] regular cycles were logged. */
    HISTORY,

    /** Values the user entered during onboarding. */
    ONBOARDING,

    /** The user answered "I don't know": standard values, low accuracy. */
    DEFAULTS,
}

enum class PredictionConfidence { LOW, MEDIUM, HIGH }

/** One cycle on the timeline, logged or predicted. Dates are inclusive. */
data class CycleSegment(
    val start: LocalDate,
    val length: Int,
    val periodLength: Int,
    val ovulation: LocalDate,
    val ovulationConfirmed: Boolean,
    val isPredicted: Boolean,
) {
    val end: LocalDate get() = start.plusDays(length - 1L)
    val periodEnd: LocalDate get() = start.plusDays(periodLength - 1L)
    val fertileStart: LocalDate get() = ovulation.minusDays(CycleConstants.FERTILE_DAYS_BEFORE_OVULATION.toLong())

    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/** How a calendar day should look. */
enum class DayKind { PERIOD, PREDICTED_PERIOD, OVULATION, FERTILE, LUTEAL, OTHER }

data class CyclePrediction(
    val source: PredictionSource,
    val confidence: PredictionConfidence,
    val averageCycleLength: Int,
    val averagePeriodLength: Int,
    /** Null when no period has been logged yet. */
    val today: TodayStatus?,
    /** Logged cycles followed by [CycleConstants.PREDICTED_FUTURE_CYCLES] predicted ones, oldest first. */
    val timeline: List<CycleSegment>,
) {
    fun segmentFor(date: LocalDate): CycleSegment? = timeline.firstOrNull { it.contains(date) }

    fun dayKind(date: LocalDate): DayKind {
        val segment = segmentFor(date) ?: return DayKind.OTHER
        return when {
            !date.isAfter(segment.periodEnd) -> if (segment.isPredicted) DayKind.PREDICTED_PERIOD else DayKind.PERIOD
            date == segment.ovulation -> DayKind.OVULATION
            !date.isBefore(segment.fertileStart) && date.isBefore(segment.ovulation) -> DayKind.FERTILE
            date.isAfter(segment.ovulation) -> DayKind.LUTEAL
            else -> DayKind.OTHER
        }
    }
}

data class TodayStatus(
    val cycleDay: Int,
    val phase: CyclePhase,
    val nextPeriodStart: LocalDate,
    /** Negative when the period is late. */
    val daysUntilNextPeriod: Int,
    val ovulation: LocalDate,
    val daysUntilOvulation: Int,
    val ovulationConfirmed: Boolean,
    val fertileWindow: ClosedRange<LocalDate>,
) {
    val daysLate: Int get() = (-daysUntilNextPeriod).coerceAtLeast(0)
}
