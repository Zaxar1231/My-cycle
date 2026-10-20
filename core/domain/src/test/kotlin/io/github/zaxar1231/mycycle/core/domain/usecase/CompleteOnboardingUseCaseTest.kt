package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CompleteOnboardingUseCaseTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val settings = FakeSettings()
    private val cycles = FakeCycles()
    private val pets = FakePets()
    private val useCase = CompleteOnboardingUseCase(settings, cycles, pets) { today }

    @Test
    fun `tracker with known values saves cycle, pet and settings`() = runTest {
        useCase(
            OnboardingInput(
                role = UserRole.TRACKER,
                lastPeriodStart = today.minusDays(3),
                cycleLength = 30,
                periodLength = 6,
                pet = PetType.FOX,
                theme = ThemeSettings(mode = ThemeMode.DARK),
            ),
        )

        assertEquals(listOf(Cycle(startDate = today.minusDays(3), periodLength = 6)), cycles.saved)
        assertEquals(Pet(type = PetType.FOX), pets.pet)
        val s = settings.current.value
        assertTrue(s.onboardingCompleted)
        assertEquals(30, s.usualCycleLength)
        assertEquals(6, s.usualPeriodLength)
        assertEquals(ThemeMode.DARK, s.theme.mode)
    }

    @Test
    fun `unknown lengths fall back to defaults`() = runTest {
        useCase(OnboardingInput(role = UserRole.TRACKER, lastPeriodStart = today))

        assertEquals(CycleConstants.DEFAULT_CYCLE_LENGTH, settings.current.value.usualCycleLength)
        assertEquals(CycleConstants.DEFAULT_PERIOD_LENGTH, cycles.saved.single().periodLength)
    }

    @Test
    fun `out of range lengths are clamped`() {
        assertEquals(CycleConstants.MAX_REGULAR_CYCLE_LENGTH, CompleteOnboardingUseCase.resolveCycleLength(90))
        assertEquals(CycleConstants.MIN_REGULAR_CYCLE_LENGTH, CompleteOnboardingUseCase.resolveCycleLength(10))
        assertEquals(CycleConstants.MAX_PERIOD_LENGTH, CompleteOnboardingUseCase.resolvePeriodLength(30))
    }

    @Test
    fun `partner does not create a cycle`() = runTest {
        useCase(OnboardingInput(role = UserRole.PARTNER, lastPeriodStart = today))

        assertTrue(cycles.saved.isEmpty())
        assertEquals(UserRole.PARTNER, settings.current.value.role)
    }

    @Test
    fun `future period start is rejected and nothing is saved`() = runTest {
        val result = runCatching {
            useCase(OnboardingInput(role = UserRole.TRACKER, lastPeriodStart = today.plusDays(1)))
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(cycles.saved.isEmpty())
        assertTrue(!settings.current.value.onboardingCompleted)
    }

    @Test
    fun `retry after a failed save completes and keeps the first pet`() = runTest {
        val input = OnboardingInput(role = UserRole.TRACKER, lastPeriodStart = today, pet = PetType.CAT)
        settings.failNextUpdate = true
        assertTrue(runCatching { useCase(input) }.isFailure)

        useCase(input.copy(pet = PetType.PENGUIN))

        assertTrue(settings.current.value.onboardingCompleted)
        assertEquals(PetType.CAT, pets.pet?.type)
    }

    private class FakeSettings : SettingsRepository {
        val current = MutableStateFlow(UserSettings())
        var failNextUpdate = false
        override val settings: Flow<UserSettings> = current
        override suspend fun update(transform: (UserSettings) -> UserSettings) {
            if (failNextUpdate) {
                failNextUpdate = false
                throw java.io.IOException("disk full")
            }
            current.value = transform(current.value)
        }
    }

    private class FakeCycles : CycleRepository {
        val saved = mutableListOf<Cycle>()
        override fun observeCycles(): Flow<List<Cycle>> = MutableStateFlow(saved.toList())
        override suspend fun upsert(cycle: Cycle): Long {
            saved += cycle
            return saved.size.toLong()
        }
        override suspend fun delete(cycle: Cycle) {
            saved.remove(cycle)
        }
    }

    private class FakePets : PetRepository {
        var pet: Pet? = null
        override fun observePet(): Flow<Pet?> = MutableStateFlow(pet)
        override suspend fun adopt(pet: Pet) {
            if (this.pet == null) this.pet = pet
        }
        override suspend fun update(pet: Pet) {
            this.pet = pet
        }
    }
}
