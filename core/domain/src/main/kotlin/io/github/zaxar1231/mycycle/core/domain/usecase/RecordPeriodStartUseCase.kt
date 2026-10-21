package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

/**
 * Starts a new cycle on [date] (today by default) and closes the previous one: its end date and
 * length are filled in. Marking the same day twice changes nothing.
 *
 * A start within the period of the latest cycle, a few days before or after its start, corrects that
 * cycle's start instead of creating a one- or two-day cycle (for example, the onboarding guess was
 * off). The old start date is returned in that case, so the caller can update the day logs.
 */
class RecordPeriodStartUseCase @Inject constructor(
    private val cycles: CycleRepository,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(date: LocalDate = dateProvider.today(), periodLength: Int): LocalDate? {
        require(!date.isAfter(dateProvider.today())) { "period start is in the future" }
        var existing = cycles.cycles().filterNot { it.isPredicted }
        if (existing.any { it.startDate == date }) return null

        val replaced = existing.lastOrNull()?.takeIf { latest ->
            val earlier = existing.getOrNull(existing.size - 2)
            abs(ChronoUnit.DAYS.between(latest.startDate, date)) < latest.periodLength &&
                (earlier == null || earlier.startDate.isBefore(date))
        }
        if (replaced != null) {
            cycles.delete(replaced)
            existing = existing - replaced
        }

        existing.lastOrNull { it.startDate.isBefore(date) }?.let { previous ->
            cycles.upsert(previous.closedBy(date))
        }
        val length = periodLength.coerceIn(CycleConstants.MIN_PERIOD_LENGTH, CycleConstants.MAX_PERIOD_LENGTH)
        // A back-dated start that lands before a later cycle ends where that cycle begins.
        val next = existing.firstOrNull { it.startDate.isAfter(date) }
        cycles.upsert(Cycle(startDate = date, periodLength = length).closedBy(next?.startDate))
        return replaced?.startDate
    }
}

/** Ends the cycle the day before [nextStart]; a null [nextStart] reopens it. */
internal fun Cycle.closedBy(nextStart: LocalDate?): Cycle = copy(
    endDate = nextStart?.minusDays(1),
    cycleLength = nextStart?.let { ChronoUnit.DAYS.between(startDate, it).toInt() },
)
