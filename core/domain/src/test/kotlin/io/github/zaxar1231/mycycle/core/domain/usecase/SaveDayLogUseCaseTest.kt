package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Flow
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SaveDayLogUseCaseTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val cycles = FakeCycles()
    private val logs = FakeLogs()
    private val useCase = SaveDayLogUseCase(
        logs,
        RecordPeriodStartUseCase(cycles) { today },
        RecordPeriodEndUseCase(cycles),
    ) { today }

    @Test
    fun `saves a regular log`() = runTest {
        useCase(DayLog(date = today, flow = Flow.LIGHT, note = "  hello  "), usualPeriodLength = 5)
        assertEquals(DayLog(date = today, flow = Flow.LIGHT, note = "hello"), logs.items[today])
    }

    @Test
    fun `period start creates a cycle`() = runTest {
        useCase(DayLog(date = today, periodEvent = PeriodEvent.STARTED), usualPeriodLength = 5)
        assertEquals(today, cycles.items.value.single().startDate)
    }

    @Test
    fun `period end sets the period length of the current cycle`() = runTest {
        cycles.items.value = listOf(Cycle(id = 1, startDate = today.minusDays(3), periodLength = 5))
        useCase(DayLog(date = today, periodEvent = PeriodEvent.ENDED), usualPeriodLength = 5)
        assertEquals(4, cycles.items.value.single().periodLength)
    }

    @Test
    fun `period end without a cycle is rejected`() = runTest {
        val error = runCatching { useCase(DayLog(date = today, periodEvent = PeriodEvent.ENDED), 5) }.exceptionOrNull()
        assertEquals(InvalidDayLogException.Field.PERIOD_END, (error as InvalidDayLogException).field)
    }

    @Test
    fun `implausible values are rejected`() = runTest {
        val temp = runCatching { useCase(DayLog(date = today, temperatureCelsius = 3.66), 5) }.exceptionOrNull()
        assertEquals(InvalidDayLogException.Field.TEMPERATURE, (temp as InvalidDayLogException).field)
        val weight = runCatching { useCase(DayLog(date = today, weightKg = 5000.0), 5) }.exceptionOrNull()
        assertEquals(InvalidDayLogException.Field.WEIGHT, (weight as InvalidDayLogException).field)
        val future = runCatching { useCase(DayLog(date = today.plusDays(1), flow = Flow.LIGHT), 5) }.exceptionOrNull()
        assertEquals(InvalidDayLogException.Field.DATE, (future as InvalidDayLogException).field)
        assertTrue(logs.items.isEmpty())
    }

    @Test
    fun `an empty log deletes the entry`() = runTest {
        logs.items[today] = DayLog(date = today, flow = Flow.HEAVY)
        useCase(DayLog(date = today, note = "   "), 5)
        assertNull(logs.items[today])
    }

    private class FakeLogs : DayLogRepository {
        val items = mutableMapOf<LocalDate, DayLog>()
        private val flow = MutableStateFlow(0)
        override fun observe(date: LocalDate) = flow.map { items[date] }
        override fun observeRange(from: LocalDate, to: LocalDate) = flow.map { items.values.toList() }
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
        override fun observeCycles() = items
        override suspend fun upsert(cycle: Cycle): Long {
            val id = if (cycle.id != 0L) cycle.id else nextId++
            items.value = items.value.filterNot { it.id == id || it.startDate == cycle.startDate } + cycle.copy(id = id)
            return id
        }
        override suspend fun delete(cycle: Cycle) {
            items.value = items.value - cycle
        }
    }
}
