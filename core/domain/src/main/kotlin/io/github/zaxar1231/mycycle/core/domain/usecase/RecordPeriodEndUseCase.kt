package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Sets the period length of the cycle that [date] belongs to. Returns false if no cycle fits. */
class RecordPeriodEndUseCase @Inject constructor(private val cycles: CycleRepository) {
    suspend operator fun invoke(date: LocalDate): Boolean {
        val cycle = cycleOf(date) ?: return false
        val length = lengthUntil(cycle, date)
        if (length > CycleConstants.MAX_PERIOD_LENGTH) return false
        cycles.upsert(cycle.copy(periodLength = length))
        return true
    }

    /**
     * Undoes a period end marked on [date]. If that mark set the period length, it goes back to
     * [usualPeriodLength].
     */
    suspend fun clear(date: LocalDate, usualPeriodLength: Int) {
        val cycle = cycleOf(date) ?: return
        if (cycle.periodLength != lengthUntil(cycle, date)) return
        val length = usualPeriodLength.coerceIn(CycleConstants.MIN_PERIOD_LENGTH, CycleConstants.MAX_PERIOD_LENGTH)
        cycles.upsert(cycle.copy(periodLength = length))
    }

    private suspend fun cycleOf(date: LocalDate): Cycle? = cycles.cycles()
        .filter { !it.isPredicted && !it.startDate.isAfter(date) }
        .maxByOrNull { it.startDate }

    private fun lengthUntil(cycle: Cycle, date: LocalDate) = ChronoUnit.DAYS.between(cycle.startDate, date).toInt() + 1
}
