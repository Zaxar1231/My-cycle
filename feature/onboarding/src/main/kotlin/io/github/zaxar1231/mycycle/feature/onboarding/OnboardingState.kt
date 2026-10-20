package io.github.zaxar1231.mycycle.feature.onboarding

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import java.time.LocalDate

enum class OnboardingStep { WELCOME, ACCOUNT, ROLE, CYCLE, PET, THEME, PIN }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val role: UserRole = UserRole.TRACKER,
    val lastPeriodStart: LocalDate? = null,
    val cycleLength: Int = CycleConstants.DEFAULT_CYCLE_LENGTH,
    val cycleLengthUnknown: Boolean = false,
    val periodLength: Int = CycleConstants.DEFAULT_PERIOD_LENGTH,
    val periodLengthUnknown: Boolean = false,
    val pet: PetType? = null,
    /** The pet waiting for confirmation in the "this cannot be changed" dialog. */
    val pendingPet: PetType? = null,
    val theme: ThemeSettings = ThemeSettings(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: Boolean = false,
) {
    /** Partners do not track a cycle, so they skip the cycle step. */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { it != OnboardingStep.CYCLE || role == UserRole.TRACKER }

    val stepIndex: Int get() = steps.indexOf(step)

    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.PET -> pet != null
            else -> true
        }
}
