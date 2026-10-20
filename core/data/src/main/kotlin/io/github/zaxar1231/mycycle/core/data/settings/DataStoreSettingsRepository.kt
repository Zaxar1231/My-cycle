package io.github.zaxar1231.mycycle.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.zaxar1231.mycycle.core.data.db.Converters.Companion.enumOrNull
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { prefs -> transform(prefs.toSettings()).writeTo(prefs) }
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            onboardingCompleted = this[ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            role = enumOrNull<UserRole>(this[ROLE]) ?: defaults.role,
            accountId = this[ACCOUNT_ID],
            usualCycleLength = this[CYCLE_LENGTH] ?: defaults.usualCycleLength,
            usualPeriodLength = this[PERIOD_LENGTH] ?: defaults.usualPeriodLength,
            theme = ThemeSettings(
                style = enumOrNull<ThemeStyle>(this[THEME_STYLE]) ?: defaults.theme.style,
                mode = enumOrNull<ThemeMode>(this[THEME_MODE]) ?: defaults.theme.mode,
            ),
            pinSuggestionDismissed = this[PIN_SUGGESTION_DISMISSED] ?: defaults.pinSuggestionDismissed,
        )
    }

    private fun UserSettings.writeTo(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs[ONBOARDING_COMPLETED] = onboardingCompleted
        prefs[ROLE] = role.name
        if (accountId != null) prefs[ACCOUNT_ID] = accountId else prefs.remove(ACCOUNT_ID)
        prefs[CYCLE_LENGTH] = usualCycleLength
        prefs[PERIOD_LENGTH] = usualPeriodLength
        prefs[THEME_STYLE] = theme.style.name
        prefs[THEME_MODE] = theme.mode.name
        prefs[PIN_SUGGESTION_DISMISSED] = pinSuggestionDismissed
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ROLE = stringPreferencesKey("role")
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val CYCLE_LENGTH = intPreferencesKey("usual_cycle_length")
        val PERIOD_LENGTH = intPreferencesKey("usual_period_length")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PIN_SUGGESTION_DISMISSED = booleanPreferencesKey("pin_suggestion_dismissed")
    }
}
