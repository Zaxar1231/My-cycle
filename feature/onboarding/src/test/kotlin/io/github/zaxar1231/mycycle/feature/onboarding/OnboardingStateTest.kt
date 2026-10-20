package io.github.zaxar1231.mycycle.feature.onboarding

import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingStateTest {
    @Test
    fun `tracker goes through every step`() {
        assertEquals(OnboardingStep.entries, OnboardingState(role = UserRole.TRACKER).steps)
    }

    @Test
    fun `partner skips the cycle step`() {
        val steps = OnboardingState(role = UserRole.PARTNER).steps
        assertFalse(OnboardingStep.CYCLE in steps)
        assertEquals(OnboardingStep.entries.size - 1, steps.size)
    }

    @Test
    fun `pet step needs a confirmed pet`() {
        assertFalse(OnboardingState(step = OnboardingStep.PET).canContinue)
        assertFalse(OnboardingState(step = OnboardingStep.PET, pendingPet = PetType.CAT).canContinue)
        assertTrue(OnboardingState(step = OnboardingStep.PET, pet = PetType.CAT).canContinue)
    }
}
