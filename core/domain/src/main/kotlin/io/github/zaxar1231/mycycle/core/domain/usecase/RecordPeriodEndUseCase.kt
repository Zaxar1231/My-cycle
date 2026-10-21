package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Sets the period length of the cycle that [date] belongs to. Returns false if no cycle fits. */
class RecordPeriodEndUseCase @Inject constructor(private val cycles: CycleRepository) {
    suspend operator fun invoke(date: LocalDate): Boolean {
        val cycle = cycles.observeCycles().first()
            .filter { !it.isPredicted && !it.startDate.isAfter(date) }
            .maxByOrNull { it.startDate } ?: return false
        val length = ChronoUnit.DAYS.between(cycle.startDate, date).toInt() + 1
        if (length > CycleConstants.MAX_PERIOD_LENGTH) return false
        cycles.upsert(cycle.copy(periodLength = length))
        return true
    }
}
