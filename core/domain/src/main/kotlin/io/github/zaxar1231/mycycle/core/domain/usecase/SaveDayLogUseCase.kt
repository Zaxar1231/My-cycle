package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import javax.inject.Inject

class InvalidDayLogException(val field: Field) : IllegalArgumentException("invalid $field") {
    enum class Field { DATE, TEMPERATURE, WEIGHT, PERIOD_END }
}

/**
 * Validates and saves a day log. Marking the period start or end also updates the cycle, so the
 * calendar and predictions follow the log. An entry with nothing filled in is deleted.
 */
class SaveDayLogUseCase @Inject constructor(
    private val dayLogs: DayLogRepository,
    private val recordPeriodStart: RecordPeriodStartUseCase,
    private val recordPeriodEnd: RecordPeriodEndUseCase,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(log: DayLog, usualPeriodLength: Int) {
        val clean = log.copy(note = log.note?.trim()?.takeIf { it.isNotEmpty() })
        validate(clean)
        when (clean.periodEvent) {
            PeriodEvent.STARTED -> recordPeriodStart(clean.date, usualPeriodLength)
            PeriodEvent.ENDED -> if (!recordPeriodEnd(clean.date)) throw InvalidDayLogException(InvalidDayLogException.Field.PERIOD_END)
            null -> Unit
        }
        if (clean.isEmpty()) dayLogs.delete(clean.date) else dayLogs.save(clean)
    }

    private fun validate(log: DayLog) {
        if (log.date.isAfter(dateProvider.today())) throw InvalidDayLogException(InvalidDayLogException.Field.DATE)
        log.temperatureCelsius?.let {
            if (it !in CycleConstants.MIN_TEMPERATURE_CELSIUS..CycleConstants.MAX_TEMPERATURE_CELSIUS) {
                throw InvalidDayLogException(InvalidDayLogException.Field.TEMPERATURE)
            }
        }
        log.weightKg?.let {
            if (it !in CycleConstants.MIN_WEIGHT_KG..CycleConstants.MAX_WEIGHT_KG) {
                throw InvalidDayLogException(InvalidDayLogException.Field.WEIGHT)
            }
        }
    }
}

fun DayLog.isEmpty(): Boolean = this == DayLog(date = date)
