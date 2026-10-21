package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Flow
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EditCycleUseCasesTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val cycles = FakeCycles()
    private val logs = FakeLogs()
    private val transactions = object : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }
    private val edit = EditCycleUseCase(cycles, logs, transactions) { today }
    private val delete = DeleteCycleUseCase(cycles, logs, RemovePeriodStartUseCase(cycles), transactions)

    private val a = Cycle(id = 1, startDate = today.minusDays(60), endDate = today.minusDays(31), periodLength = 5, cycleLength = 30)
    private val b = Cycle(id = 2, startDate = today.minusDays(30), endDate = today.minusDays(3), periodLength = 5, cycleLength = 28)
    private val c = Cycle(id = 3, startDate = today.minusDays(2), periodLength = 5)

    private fun mark(date: LocalDate, event: PeriodEvent) {
        logs.items[date] = DayLog(date = date, periodEvent = event)
    }

    @Test
    fun `editing moves the cycle, re-closes neighbours and moves the marks`() = runTest {
        cycles.items.value = listOf(a, b, c)
        mark(b.startDate, PeriodEvent.STARTED)
        mark(b.startDate.plusDays(4), PeriodEvent.ENDED)
        logs.items[b.startDate.plusDays(1)] = DayLog(date = b.startDate.plusDays(1), flow = Flow.HEAVY, periodEvent = null)

        edit(b, b.startDate.minusDays(2), periodLength = 4)

        val newStart = b.startDate.minusDays(2)
        val edited = cycles.items.value.single { it.id == 2L }
        assertEquals(newStart, edited.startDate)
        assertEquals(4, edited.periodLength)
        assertEquals(30, edited.cycleLength)
        assertEquals(28, cycles.items.value.single { it.id == 1L }.cycleLength)
        assertNull(logs.items[b.startDate])
        assertNull(logs.items[b.startDate.plusDays(4)])
        assertEquals(PeriodEvent.STARTED, logs.items[newStart]?.periodEvent)
        assertEquals(PeriodEvent.ENDED, logs.items[newStart.plusDays(3)]?.periodEvent)
        assertEquals(Flow.HEAVY, logs.items[b.startDate.plusDays(1)]?.flow)
    }

    @Test
    fun `moving only the start adds no end mark the user never set`() = runTest {
        cycles.items.value = listOf(a, b, c)
        edit(b, b.startDate.plusDays(1), periodLength = 5)
        assertEquals(PeriodEvent.STARTED, logs.items[b.startDate.plusDays(1)]?.periodEvent)
        assertEquals(1, logs.items.size)
    }

    @Test
    fun `a running period gets no end mark`() = runTest {
        cycles.items.value = listOf(b, c)
        edit(c, c.startDate, periodLength = 6)
        assertEquals(6, cycles.items.value.single { it.id == 3L }.periodLength)
        assertEquals(PeriodEvent.STARTED, logs.items[c.startDate]?.periodEvent)
        assertEquals(1, logs.items.size)
    }

    @Test
    fun `edits that overlap a neighbour or leave the valid range are rejected`() = runTest {
        cycles.items.value = listOf(a, b, c)
        suspend fun reason(start: LocalDate, length: Int) =
            (runCatching { edit(b, start, length) }.exceptionOrNull() as InvalidCycleEditException).reason

        assertEquals(InvalidCycleEditException.Reason.OVERLAP, reason(a.startDate.plusDays(2), 5))
        assertEquals(InvalidCycleEditException.Reason.OVERLAP, reason(c.startDate.minusDays(3), 5))
        assertEquals(InvalidCycleEditException.Reason.PERIOD_LENGTH, reason(b.startDate, 20))
        assertEquals(InvalidCycleEditException.Reason.DATE, reason(today.plusDays(1), 5))
        assertEquals(listOf(a, b, c), cycles.items.value.sortedBy { it.startDate })
    }

    @Test
    fun `deleting a cycle extends the previous one and clears its marks`() = runTest {
        cycles.items.value = listOf(a, b, c)
        mark(b.startDate, PeriodEvent.STARTED)
        mark(b.startDate.plusDays(4), PeriodEvent.ENDED)

        delete(b)

        assertEquals(listOf(1L, 3L), cycles.items.value.map { it.id }.sorted())
        assertEquals(58, cycles.items.value.single { it.id == 1L }.cycleLength)
        assertEquals(emptyMap<LocalDate, DayLog>(), logs.items.toMap())
    }

    private class FakeLogs : DayLogRepository {
        val items = mutableMapOf<LocalDate, DayLog>()
        private val flow = MutableStateFlow(0)
        override fun observe(date: LocalDate) = flow.map { items[date] }
        override fun observeRange(from: LocalDate, to: LocalDate) =
            flow.map { items.values.filter { it.date in from..to }.sortedBy { it.date } }
        override fun observeAll() = flow.map { items.values.sortedBy { it.date } }
        override suspend fun save(log: DayLog) {
            items[log.date] = log
        }
        override suspend fun delete(date: LocalDate) {
            items.remove(date)
        }
    }

    private class FakeCycles : CycleRepository {
        val items = MutableStateFlow<List<Cycle>>(emptyList())
        private var nextId = 100L
        override fun observeCycles() = items.map { list -> list.sortedBy { it.startDate } }
        override suspend fun upsert(cycle: Cycle): Long {
            val id = if (cycle.id != 0L) cycle.id else nextId++
            items.value = items.value.filterNot { it.id == id || it.startDate == cycle.startDate } + cycle.copy(id = id)
            return id
        }
        override suspend fun delete(cycle: Cycle) {
            items.value = items.value.filterNot { it.id == cycle.id }
        }
    }
}
