package io.github.zaxar1231.mycycle.feature.security

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.repository.PinRepository
import io.github.zaxar1231.mycycle.core.domain.security.AutoLock
import io.github.zaxar1231.mycycle.core.domain.security.PinCheck
import io.github.zaxar1231.mycycle.core.domain.security.PinPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinEntryState(
    val entered: String = "",
    val checking: Boolean = false,
    /** Attempts left after the last wrong PIN, or null when there was none. */
    val attemptsLeft: Int? = null,
    /** Wall-clock time until which input is frozen after too many mistakes. */
    val frozenUntil: Long? = null,
)

/**
 * Locks the app on a cold start and after it spent [AutoLock] time in the background. Scoped to
 * the activity: a new process, or an activity that was really finished, starts locked again.
 * Uses only the PIN store, which does not need the database, so it is safe before the key check.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(private val pins: PinRepository) : ViewModel() {
    private val lockedFlag = MutableStateFlow(true)
    private val _entry = MutableStateFlow(PinEntryState())
    val entry: StateFlow<PinEntryState> = _entry.asStateFlow()

    /** Bumped on every lock, so the biometric prompt opens once per lock. */
    private val _lockCount = MutableStateFlow(0)
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    val locked: StateFlow<Boolean> = combine(pins.hasPin, lockedFlag) { hasPin, locked -> hasPin && locked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var backgroundAt: Long? = null

    /** The system biometric prompt may stop the activity on some devices; that is not leaving the app. */
    var biometricInProgress = false

    init {
        viewModelScope.launch {
            // Without a PIN the app is open, so a PIN set later does not lock the current session.
            pins.hasPin.collect { if (!it) lockedFlag.value = false }
        }
        refreshFreeze()
    }

    fun onBackground(changingConfigurations: Boolean) {
        if (changingConfigurations || biometricInProgress) return
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(lockAfterMinutes: Int) {
        val since = backgroundAt ?: return
        backgroundAt = null
        if (!lockedFlag.value && AutoLock.shouldLock(since, SystemClock.elapsedRealtime(), lockAfterMinutes)) {
            lockedFlag.value = true
            _lockCount.update { it + 1 }
            _entry.value = PinEntryState()
            refreshFreeze()
        }
    }

    fun digit(digit: Char) {
        val current = _entry.value
        if (current.checking || current.entered.length >= PinPolicy.LENGTH || current.isFrozen()) return
        val entered = current.entered + digit
        _entry.update { it.copy(entered = entered) }
        if (entered.length == PinPolicy.LENGTH) check(entered)
    }

    fun backspace() = _entry.update { if (it.checking) it else it.copy(entered = it.entered.dropLast(1)) }

    fun unlockedByBiometrics() = unlock()

    fun freezeEnded() = _entry.update { it.copy(frozenUntil = null) }

    private fun check(pin: String) {
        _entry.update { it.copy(checking = true) }
        viewModelScope.launch {
            when (val result = pins.check(pin)) {
                PinCheck.Correct -> unlock()
                is PinCheck.Wrong -> _entry.value = PinEntryState(attemptsLeft = result.attemptsLeft, frozenUntil = result.lockedUntilMillis)
                is PinCheck.LockedOut -> _entry.value = PinEntryState(frozenUntil = result.untilMillis)
            }
        }
    }

    private fun unlock() {
        lockedFlag.value = false
        _entry.value = PinEntryState()
    }

    private fun refreshFreeze() {
        viewModelScope.launch {
            val until = pins.lockedUntil()
            _entry.update { it.copy(frozenUntil = until) }
        }
    }
}

internal fun PinEntryState.isFrozen(now: Long = System.currentTimeMillis()) = frozenUntil != null && frozenUntil > now
