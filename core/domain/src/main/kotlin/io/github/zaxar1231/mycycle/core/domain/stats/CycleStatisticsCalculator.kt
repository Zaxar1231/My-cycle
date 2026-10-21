package io.github.zaxar1231.mycycle.core.domain.stats

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.MIN_CYCLES_FOR_REGULARITY
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.MIN_PATTERN_OCCURRENCES
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.PATTERN_MIN_SHARE
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.PATTERN_WINDOW_DAYS
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.PREDICTION_CYCLE_COUNT
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.REGULARITY_MAX_SPREAD_DAYS
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Statistics for the stats screen. Pure functions over the prediction timeline and the day logs. */
class CycleStatisticsCalculator @Inject constructor() {

    /** Logged cycles, newest first, each matched with its timeline segment. */
    fun history(prediction: CyclePrediction, cycles: List<Cycle>, today: LocalDate): List<CycleHistoryItem> {
        val logged = cycles.filterNot { it.isPredicted }.associateBy { it.startDate }
        val segments = prediction.timeline.filterNot { it.isPredicted }
        return segments.mapIndexedNotNull { index, segment ->
            val cycle = logged[segment.start] ?: return@mapIndexedNotNull null
            val isCurrent = index == segments.lastIndex
            CycleHistoryItem(
                cycle = cycle,
                segment = segment,
                isCurrent = isCurrent,
                elapsedDays = if (isCurrent) (days(segment.start, today) + 1).coerceIn(1, segment.length) else segment.length,
                days = List(segment.length) { prediction.dayKind(segment.start.plusDays(it.toLong())) },
            )
        }.reversed()
    }

    fun summary(history: List<CycleHistoryItem>): CycleSummary {
        val completed = history.filterNot { it.isCurrent }
        val lengths = completed.map { it.segment.length }
        val recent = lengths.take(PREDICTION_CYCLE_COUNT)
        val regularity = when {
            recent.size < MIN_CYCLES_FOR_REGULARITY -> Regularity.NOT_ENOUGH_DATA
            recent.max() - recent.min() <= REGULARITY_MAX_SPREAD_DAYS -> Regularity.REGULAR
            else -> Regularity.IRREGULAR
        }
        // A running period is not over yet, so its length is not counted.
        val periods = history.filter { !it.isCurrent || it.elapsedDays > it.periodLength }.map { it.periodLength }
        return CycleSummary(
            current = history.firstOrNull { it.isCurrent },
            lastCompleted = completed.firstOrNull(),
            completedCount = completed.size,
            averageCycleLength = lengths.takeIf { it.isNotEmpty() }?.average(),
            averagePeriodLength = periods.takeIf { it.isNotEmpty() }?.average(),
            shortestCycle = lengths.minOrNull(),
            longestCycle = lengths.maxOrNull(),
            regularity = regularity,
        )
    }

    fun logs(prediction: CyclePrediction, history: List<CycleHistoryItem>, logs: List<DayLog>): LogStatistics {
        val placed = logs.map { it to place(prediction, it.date) }
        val symptomDays = placed.flatMap { (log, place) -> log.symptoms.map { it to place?.first } }
        val moodsByPhase = placed
            .flatMap { (log, place) -> place?.second?.let { phase -> log.moods.map { phase to it } }.orEmpty() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, moods) -> moods.groupingBy { it }.eachCount().toList().sortedByDescending { it.second } }
        return LogStatistics(
            loggedDays = logs.size,
            symptomFrequency = symptomDays.groupingBy { it.first }.eachCount().toList().sortedByDescending { it.second },
            moodsByPhase = moodsByPhase,
            patterns = symptomDays.filter { it.second != null }
                .groupBy({ it.first }, { it.second!! })
                .mapNotNull { (symptom, cycleDays) -> pattern(cycleDays)?.let { (from, count) -> SymptomPattern(symptom, from, from + PATTERN_WINDOW_DAYS - 1, count, cycleDays.size) } }
                .sortedByDescending { it.occurrences },
            temperatures = logs.mapNotNull { log -> log.temperatureCelsius?.let { log.date to it } }.sortedBy { it.first },
            weights = logs.mapNotNull { log -> log.weightKg?.let { log.date to it } }.sortedBy { it.first },
            cycleLengths = history.filterNot { it.isCurrent }.map { it.start to it.segment.length }.reversed(),
        )
    }

    fun chronology(prediction: CyclePrediction, logs: List<DayLog>): List<ChronologyEntry> =
        logs.sortedByDescending { it.date }.map { log ->
            val place = place(prediction, log.date)
            ChronologyEntry(log, place?.first, place?.second)
        }

    /** Cycle day and phase of [date], or null when it falls outside the logged cycles. */
    private fun place(prediction: CyclePrediction, date: LocalDate): Pair<Int, CyclePhase>? {
        val segment = prediction.segmentFor(date)?.takeUnless { it.isPredicted } ?: return null
        val cycleDay = days(segment.start, date) + 1
        return cycleDay to CyclePredictor.phaseOf(cycleDay, segment)
    }

    /** The first day of the best [PATTERN_WINDOW_DAYS]-day window and how many days fall into it. */
    private fun pattern(cycleDays: List<Int>): Pair<Int, Int>? {
        if (cycleDays.size < MIN_PATTERN_OCCURRENCES) return null
        val best = (1..cycleDays.max())
            .map { from -> from to cycleDays.count { it in from until from + PATTERN_WINDOW_DAYS } }
            .maxBy { it.second }
        return best.takeIf { it.second >= MIN_PATTERN_OCCURRENCES && it.second >= cycleDays.size * PATTERN_MIN_SHARE }
    }

    private fun days(from: LocalDate, to: LocalDate) = ChronoUnit.DAYS.between(from, to).toInt()
}
