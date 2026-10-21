package io.github.zaxar1231.mycycle.core.domain.stats

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleSegment
import io.github.zaxar1231.mycycle.core.domain.cycle.DayKind
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import java.time.LocalDate

/** One logged cycle in the history list, newest first. */
data class CycleHistoryItem(
    val cycle: Cycle,
    val segment: CycleSegment,
    val isCurrent: Boolean,
    /** Length so far for the current cycle, the full length for a completed one. */
    val elapsedDays: Int,
    /** How each day of the segment looks, from its first to its last day. */
    val days: List<DayKind>,
) {
    val start: LocalDate get() = segment.start
    val end: LocalDate get() = segment.end
    val periodLength: Int get() = segment.periodLength
}

enum class Regularity { NOT_ENOUGH_DATA, REGULAR, IRREGULAR }

data class CycleSummary(
    val current: CycleHistoryItem?,
    val lastCompleted: CycleHistoryItem?,
    val completedCount: Int,
    val averageCycleLength: Double?,
    val averagePeriodLength: Double?,
    val shortestCycle: Int?,
    val longestCycle: Int?,
    val regularity: Regularity,
)

/** "Headache is most often logged on cycle days 1–2": [occurrences] of [total] logged days. */
data class SymptomPattern(
    val symptom: Symptom,
    val fromCycleDay: Int,
    val toCycleDay: Int,
    val occurrences: Int,
    val total: Int,
)

data class LogStatistics(
    val loggedDays: Int,
    /** Most frequent first. */
    val symptomFrequency: List<Pair<Symptom, Int>>,
    /** Most frequent first within each phase. */
    val moodsByPhase: Map<CyclePhase, List<Pair<Mood, Int>>>,
    val patterns: List<SymptomPattern>,
    /** Oldest first. */
    val temperatures: List<Pair<LocalDate, Double>>,
    val weights: List<Pair<LocalDate, Double>>,
    /** Completed cycles, oldest first: start date to length in days. */
    val cycleLengths: List<Pair<LocalDate, Int>>,
)

/** A log in the chronology feed, newest first. Cycle day and phase are null before the first cycle. */
data class ChronologyEntry(
    val log: DayLog,
    val cycleDay: Int?,
    val phase: CyclePhase?,
)
