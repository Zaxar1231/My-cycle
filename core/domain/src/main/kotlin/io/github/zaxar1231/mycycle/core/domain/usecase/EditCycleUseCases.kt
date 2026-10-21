package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import java.time.LocalDate
import javax.inject.Inject

class InvalidCycleEditException(val reason: Reason) : IllegalArgumentException("invalid cycle edit: $reason") {
    enum class Reason {
        /** The start is in the future. */
        DATE,
        PERIOD_LENGTH,

        /** The cycle would move past a neighbour or into its period. */
        OVERLAP,
    }
}

/**
 * Changes the start date and period length of a logged cycle. Neighbouring cycles are re-closed and
 * the start and end marks in the day logs follow the new dates.
 */
class EditCycleUseCase @Inject constructor(
    private val cycles: CycleRepository,
    private val dayLogs: DayLogRepository,
    private val transactions: TransactionRunner,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(cycle: Cycle, newStart: LocalDate, periodLength: Int) {
        val today = dateProvider.today()
        if (newStart.isAfter(today)) throw InvalidCycleEditException(InvalidCycleEditException.Reason.DATE)
        if (periodLength !in CycleConstants.MIN_PERIOD_LENGTH..CycleConstants.MAX_PERIOD_LENGTH) {
            throw InvalidCycleEditException(InvalidCycleEditException.Reason.PERIOD_LENGTH)
        }
        transactions.inTransaction {
            val all = cycles.cycles().filterNot { it.isPredicted }.sortedBy { it.startDate }
            val index = all.indexOfFirst { it.id == cycle.id }
            check(index >= 0) { "cycle ${cycle.id} not found" }
            val original = all[index]
            val previous = all.getOrNull(index - 1)
            val next = all.getOrNull(index + 1)
            val newPeriodEnd = newStart.plusDays(periodLength - 1L)
            val overlaps = (previous != null && !newStart.isAfter(previous.periodEnd())) ||
                (next != null && !newPeriodEnd.isBefore(next.startDate))
            if (overlaps) throw InvalidCycleEditException(InvalidCycleEditException.Reason.OVERLAP)

            val hadEndMark = dayLogs.clearPeriodMarks(original.startDate, next?.startDate)
            cycles.upsert(original.copy(startDate = newStart, periodLength = periodLength).closedBy(next?.startDate))
            previous?.let { cycles.upsert(it.closedBy(newStart)) }
            dayLogs.setPeriodEvent(newStart, PeriodEvent.STARTED)
            // An end mark only where the user set one or changed the length; a running period has none yet.
            val endKnown = hadEndMark || periodLength != original.periodLength
            if (endKnown && periodLength > 1 && !newPeriodEnd.isAfter(today)) {
                dayLogs.setPeriodEvent(newPeriodEnd, PeriodEvent.ENDED)
            }
        }
    }
}

/** Deletes a logged cycle by mistake: the previous cycle extends to the next one, and its marks go. */
class DeleteCycleUseCase @Inject constructor(
    private val cycles: CycleRepository,
    private val dayLogs: DayLogRepository,
    private val removePeriodStart: RemovePeriodStartUseCase,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(cycle: Cycle) = transactions.inTransaction {
        val next = cycles.cycles().filterNot { it.isPredicted }.sortedBy { it.startDate }.firstOrNull { it.startDate.isAfter(cycle.startDate) }
        dayLogs.clearPeriodMarks(cycle.startDate, next?.startDate)
        removePeriodStart(cycle.startDate)
    }
}

private fun Cycle.periodEnd(): LocalDate = startDate.plusDays(periodLength - 1L)

/**
 * Removes the start mark on [start] and end marks up to the next cycle (or a maximal period).
 * Returns whether an end mark was there.
 */
private suspend fun DayLogRepository.clearPeriodMarks(start: LocalDate, nextStart: LocalDate?): Boolean {
    val until = nextStart?.minusDays(1) ?: start.plusDays(CycleConstants.MAX_PERIOD_LENGTH - 1L)
    var hadEndMark = false
    range(start, until).forEach { log ->
        if (log.periodEvent == PeriodEvent.ENDED) hadEndMark = true
        val isOwnMark = (log.date == start && log.periodEvent == PeriodEvent.STARTED) || log.periodEvent == PeriodEvent.ENDED
        if (isOwnMark) setPeriodEvent(log.date, null)
    }
    return hadEndMark
}

/** Sets or clears the period mark of a day, creating or deleting its log as needed. */
internal suspend fun DayLogRepository.setPeriodEvent(date: LocalDate, event: PeriodEvent?) {
    val log = (get(date) ?: DayLog(date = date)).copy(periodEvent = event)
    if (log.isEmpty()) delete(date) else save(log)
}
