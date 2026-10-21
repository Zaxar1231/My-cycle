package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Starts a new cycle on [date] (today by default) and closes the previous one: its end date and
 * length are filled in. Marking the same day twice changes nothing.
 */
class RecordPeriodStartUseCase @Inject constructor(
    private val cycles: CycleRepository,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(date: LocalDate = dateProvider.today(), periodLength: Int) {
        require(!date.isAfter(dateProvider.today())) { "period start is in the future" }
        val existing = cycles.observeCycles().first().filterNot { it.isPredicted }
        if (existing.any { it.startDate == date }) return

        existing.lastOrNull { it.startDate.isBefore(date) }?.let { previous ->
            cycles.upsert(
                previous.copy(
                    endDate = date.minusDays(1),
                    cycleLength = ChronoUnit.DAYS.between(previous.startDate, date).toInt(),
                ),
            )
        }
        val length = periodLength.coerceIn(CycleConstants.MIN_PERIOD_LENGTH, CycleConstants.MAX_PERIOD_LENGTH)
        // A back-dated start that lands before a later cycle ends where that cycle begins.
        val next = existing.firstOrNull { it.startDate.isAfter(date) }
        cycles.upsert(
            Cycle(
                startDate = date,
                periodLength = length,
                endDate = next?.startDate?.minusDays(1),
                cycleLength = next?.let { ChronoUnit.DAYS.between(date, it.startDate).toInt() },
            ),
        )
    }
}
