package io.github.zaxar1231.mycycle.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.usecase.CompleteOnboardingUseCase
import io.github.zaxar1231.mycycle.core.domain.usecase.OnboardingInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        // The theme is saved as soon as it is picked; start from it so finishing never overwrites it.
        viewModelScope.launch {
            val saved = settings.settings.first().theme
            _state.update { it.copy(theme = saved) }
        }
    }

    fun next() {
        val s = _state.value
        if (!s.canContinue || s.saving) return
        val steps = s.steps
        if (s.stepIndex == steps.lastIndex) finish() else _state.update { it.copy(step = steps[s.stepIndex + 1]) }
    }

    fun back() {
        val s = _state.value
        if (s.stepIndex > 0) _state.update { it.copy(step = s.steps[s.stepIndex - 1]) }
    }

    fun selectRole(role: UserRole) = _state.update { it.copy(role = role) }

    fun accountReady() = _state.update { it.copy(signedIn = true) }

    fun setLastPeriodStart(date: LocalDate?) = _state.update { it.copy(lastPeriodStart = date) }

    fun setCycleLength(days: Int) = _state.update { it.copy(cycleLength = days) }

    fun setCycleLengthUnknown(unknown: Boolean) = _state.update { it.copy(cycleLengthUnknown = unknown) }

    fun setPeriodLength(days: Int) = _state.update { it.copy(periodLength = days) }

    fun setPeriodLengthUnknown(unknown: Boolean) = _state.update { it.copy(periodLengthUnknown = unknown) }

    /** Picking a pet only asks for confirmation; [confirmPet] makes the choice. */
    fun requestPet(pet: PetType) = _state.update { it.copy(pendingPet = pet) }

    fun confirmPet() = _state.update { it.copy(pet = it.pendingPet ?: it.pet, pendingPet = null) }

    fun cancelPet() = _state.update { it.copy(pendingPet = null) }

    /** Saved right away so the whole app previews the theme while the user chooses. */
    fun setTheme(theme: ThemeSettings) {
        _state.update { it.copy(theme = theme) }
        viewModelScope.launch { settings.setTheme(theme) }
    }

    private fun finish() {
        val s = _state.value
        _state.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            val input = OnboardingInput(
                role = s.role,
                lastPeriodStart = s.lastPeriodStart.takeIf { s.role == UserRole.TRACKER },
                cycleLength = s.cycleLength.takeUnless { s.cycleLengthUnknown },
                periodLength = s.periodLength.takeUnless { s.periodLengthUnknown },
                pet = s.pet,
                theme = s.theme,
            )
            try {
                completeOnboarding(input)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = true) }
            }
        }
    }
}
