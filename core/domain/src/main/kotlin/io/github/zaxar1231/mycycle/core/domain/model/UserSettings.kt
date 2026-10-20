package io.github.zaxar1231.mycycle.core.domain.model

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants

/** Local profile and preferences. Accounts arrive in stage 9, until then [accountId] is null. */
data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val role: UserRole = UserRole.TRACKER,
    val accountId: String? = null,
    val usualCycleLength: Int = CycleConstants.DEFAULT_CYCLE_LENGTH,
    val usualPeriodLength: Int = CycleConstants.DEFAULT_PERIOD_LENGTH,
    val theme: ThemeSettings = ThemeSettings(),
    val pinSuggestionDismissed: Boolean = false,
)

enum class UserRole { TRACKER, PARTNER }

data class ThemeSettings(
    val style: ThemeStyle = ThemeStyle.MATERIAL_YOU,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)

enum class ThemeStyle { MATERIAL_YOU, TWILIGHT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }
