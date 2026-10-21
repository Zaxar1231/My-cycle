package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import java.time.LocalDate
import javax.inject.Inject

/** What the user entered during onboarding. A null length means "I don't know". */
data class OnboardingInput(
    val role: UserRole,
    val lastPeriodStart: LocalDate? = null,
    val cycleLength: Int? = null,
    val periodLength: Int? = null,
    val pet: PetType? = null,
    val theme: ThemeSettings = ThemeSettings(),
)

class CompleteOnboardingUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val cycles: CycleRepository,
    private val pets: PetRepository,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(input: OnboardingInput) {
        val cycleLength = resolveCycleLength(input.cycleLength)
        val periodLength = resolvePeriodLength(input.periodLength)

        if (input.role == UserRole.TRACKER && input.lastPeriodStart != null) {
            require(!input.lastPeriodStart.isAfter(dateProvider.today())) { "last period start is in the future" }
            cycles.upsert(Cycle(startDate = input.lastPeriodStart, periodLength = periodLength))
        }
        input.pet?.let { pets.adopt(Pet(type = it)) }
        settings.update {
            it.copy(
                onboardingCompleted = true,
                role = input.role,
                usualCycleLength = cycleLength,
                usualPeriodLength = periodLength,
                usualLengthsEstimated = input.cycleLength == null,
                theme = input.theme,
            )
        }
    }

    companion object {
        fun resolveCycleLength(value: Int?): Int =
            value?.coerceIn(CycleConstants.MIN_REGULAR_CYCLE_LENGTH, CycleConstants.MAX_REGULAR_CYCLE_LENGTH)
                ?: CycleConstants.DEFAULT_CYCLE_LENGTH

        fun resolvePeriodLength(value: Int?): Int =
            value?.coerceIn(CycleConstants.MIN_PERIOD_LENGTH, CycleConstants.MAX_PERIOD_LENGTH)
                ?: CycleConstants.DEFAULT_PERIOD_LENGTH
    }
}
