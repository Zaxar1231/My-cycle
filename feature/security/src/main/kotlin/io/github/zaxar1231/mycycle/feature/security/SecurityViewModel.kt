package io.github.zaxar1231.mycycle.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.SecuritySettings
import io.github.zaxar1231.mycycle.core.domain.repository.PinRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.security.PinCheck
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** PIN and lock options for settings and onboarding. Only used once the database is open. */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val pins: PinRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val hasPin: StateFlow<Boolean?> = pins.hasPin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)
    val security: StateFlow<SecuritySettings?> = settingsRepository.settings.map { it.security }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)

    fun setPin(pin: String) {
        viewModelScope.launch { pins.setPin(pin) }
    }

    suspend fun check(pin: String): PinCheck = pins.check(pin)

    fun removePin() {
        viewModelScope.launch {
            update { it.copy(biometricUnlock = false) }
            pins.clear()
        }
    }

    fun setBiometricUnlock(enabled: Boolean) = launchUpdate { it.copy(biometricUnlock = enabled) }

    fun setLockAfter(minutes: Int) = launchUpdate { it.copy(lockAfterMinutes = minutes) }

    fun setHideInRecents(hide: Boolean) = launchUpdate { it.copy(hideInRecents = hide) }

    private fun launchUpdate(transform: (SecuritySettings) -> SecuritySettings) {
        viewModelScope.launch { update(transform) }
    }

    private suspend fun update(transform: (SecuritySettings) -> SecuritySettings) =
        settingsRepository.update { it.copy(security = transform(it.security)) }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
