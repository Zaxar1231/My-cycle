package io.github.zaxar1231.mycycle.core.domain.model

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.security.AutoLock

/** Local profile and preferences. Accounts arrive in stage 9, until then [accountId] is null. */
data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val role: UserRole = UserRole.TRACKER,
    val accountId: String? = null,
    val usualCycleLength: Int = CycleConstants.DEFAULT_CYCLE_LENGTH,
    val usualPeriodLength: Int = CycleConstants.DEFAULT_PERIOD_LENGTH,
    /** The user answered "I don't know" in onboarding, so the usual lengths are standard values. */
    val usualLengthsEstimated: Boolean = false,
    val theme: ThemeSettings = ThemeSettings(),
    val pinSuggestionDismissed: Boolean = false,
    val security: SecuritySettings = SecuritySettings(),
)

/** Whether a PIN is set is known only to the PIN store; these are the options around it. */
data class SecuritySettings(
    val biometricUnlock: Boolean = false,
    val lockAfterMinutes: Int = AutoLock.DEFAULT_MINUTES,
    /** FLAG_SECURE: a blank preview in recent apps; screenshots are blocked too. */
    val hideInRecents: Boolean = false,
)

enum class UserRole { TRACKER, PARTNER }

data class ThemeSettings(
    val style: ThemeStyle = ThemeStyle.ROSE,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Decorative flowers around the cycle ring on the home screen. */
    val flowers: Boolean = true,
)

/** [ROSE] is the default look: dark background, rose accents. */
enum class ThemeStyle { ROSE, MATERIAL_YOU, TWILIGHT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }
