package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import java.time.LocalDate
import javax.inject.Inject

class InvalidDayLogException(val field: Field) : IllegalArgumentException("invalid $field") {
    enum class Field { DATE, TEMPERATURE, WEIGHT, PERIOD_END }
}

/**
 * Validates and saves a day log. Marking the period start or end also updates the cycle, so the
 * calendar and predictions follow the log; removing the mark undoes that change. An entry with
 * nothing filled in is deleted. Everything runs in one transaction: a failure leaves no half-saved
 * cycle behind.
 */
class SaveDayLogUseCase @Inject constructor(
    private val dayLogs: DayLogRepository,
    private val recordPeriodStart: RecordPeriodStartUseCase,
    private val removePeriodStart: RemovePeriodStartUseCase,
    private val recordPeriodEnd: RecordPeriodEndUseCase,
    private val transactions: TransactionRunner,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(log: DayLog, usualPeriodLength: Int) {
        val clean = log.copy(note = log.note?.trim()?.takeIf { it.isNotEmpty() })
        validate(clean)
        transactions.inTransaction {
            val previousEvent = dayLogs.get(clean.date)?.periodEvent
            if (previousEvent != clean.periodEvent) {
                when (previousEvent) {
                    PeriodEvent.STARTED -> removePeriodStart(clean.date)
                    PeriodEvent.ENDED -> recordPeriodEnd.clear(clean.date, usualPeriodLength)
                    null -> Unit
                }
            }
            when (clean.periodEvent) {
                PeriodEvent.STARTED -> recordPeriodStart(clean.date, usualPeriodLength)?.let { realignMarks(it, clean.date) }
                PeriodEvent.ENDED -> if (!recordPeriodEnd(clean.date)) {
                    throw InvalidDayLogException(InvalidDayLogException.Field.PERIOD_END)
                }
                null -> Unit
            }
            put(clean)
        }
    }

    /** Marks the period start on [date], keeping whatever else that day's log holds. */
    suspend fun markPeriodStart(date: LocalDate, usualPeriodLength: Int) = transactions.inTransaction {
        val log = dayLogs.get(date) ?: DayLog(date = date)
        invoke(log.copy(periodEvent = PeriodEvent.STARTED), usualPeriodLength)
    }

    /**
     * The cycle start moved from [oldStart] to [newStart]: the old start mark goes, an end mark before
     * the new start goes, and an end mark after it sets the period length again.
     */
    private suspend fun realignMarks(oldStart: LocalDate, newStart: LocalDate) {
        val from = minOf(oldStart, newStart)
        val to = maxOf(oldStart, newStart).plusDays(CycleConstants.MAX_PERIOD_LENGTH.toLong())
        // The log of newStart itself is about to be replaced by the one being saved.
        dayLogs.range(from, to).filter { it.date != newStart }.forEach { log ->
            when {
                log.periodEvent == PeriodEvent.STARTED && log.date == oldStart -> put(log.copy(periodEvent = null))
                log.periodEvent != PeriodEvent.ENDED -> Unit
                log.date.isBefore(newStart) || !recordPeriodEnd(log.date) -> put(log.copy(periodEvent = null))
            }
        }
    }

    private suspend fun put(log: DayLog) {
        if (log.isEmpty()) dayLogs.delete(log.date) else dayLogs.save(log)
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
