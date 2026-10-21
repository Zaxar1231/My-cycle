package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecordPeriodStartUseCaseTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val repo = FakeCycles()
    private val useCase = RecordPeriodStartUseCase(repo) { today }

    @Test
    fun `closes the previous cycle and starts a new one`() = runTest {
        repo.items.value = listOf(Cycle(id = 1, startDate = today.minusDays(29), periodLength = 5))

        useCase(periodLength = 6)

        val (previous, current) = repo.items.value.sortedBy { it.startDate }
        assertEquals(today.minusDays(1), previous.endDate)
        assertEquals(29, previous.cycleLength)
        assertEquals(today, current.startDate)
        assertEquals(6, current.periodLength)
    }

    @Test
    fun `marking the same day twice is a no-op`() = runTest {
        useCase(periodLength = 5)
        useCase(periodLength = 5)
        assertEquals(1, repo.items.value.size)
    }

    @Test
    fun `a back-dated start between cycles is closed by the next one`() = runTest {
        repo.items.value = listOf(
            Cycle(id = 1, startDate = today.minusDays(60), periodLength = 5),
            Cycle(id = 2, startDate = today.minusDays(5), periodLength = 5),
        )

        useCase(today.minusDays(30), periodLength = 4)

        val inserted = repo.items.value.single { it.startDate == today.minusDays(30) }
        assertEquals(today.minusDays(6), inserted.endDate)
        assertEquals(25, inserted.cycleLength)
        val first = repo.items.value.single { it.id == 1L }
        assertEquals(30, first.cycleLength)
    }

    @Test
    fun `future dates are rejected`() = runTest {
        assertTrue(runCatching { useCase(today.plusDays(1), 5) }.isFailure)
        assertTrue(repo.items.value.isEmpty())
    }

    private class FakeCycles : CycleRepository {
        val items = MutableStateFlow<List<Cycle>>(emptyList())
        private var nextId = 100L
        override fun observeCycles(): Flow<List<Cycle>> = items
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
