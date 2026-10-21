package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Undoes a period start marked on [date]: the cycle that began that day is deleted and the previous
 * cycle is extended to the next one, or reopened if there is none.
 */
class RemovePeriodStartUseCase @Inject constructor(private val cycles: CycleRepository) {
    suspend operator fun invoke(date: LocalDate) {
        val existing = cycles.cycles().filterNot { it.isPredicted }
        val removed = existing.firstOrNull { it.startDate == date } ?: return
        cycles.delete(removed)
        val next = existing.firstOrNull { it.startDate.isAfter(date) }
        existing.lastOrNull { it.startDate.isBefore(date) }?.let { previous ->
            cycles.upsert(previous.closedBy(next?.startDate))
        }
    }
}
