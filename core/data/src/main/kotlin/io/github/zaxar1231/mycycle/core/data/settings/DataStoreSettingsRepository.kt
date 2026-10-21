package io.github.zaxar1231.mycycle.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Lazy
import io.github.zaxar1231.mycycle.core.data.db.Converters.Companion.enumOrNull
import io.github.zaxar1231.mycycle.core.data.db.ProfileDao
import io.github.zaxar1231.mycycle.core.data.db.ProfileEntity
import io.github.zaxar1231.mycycle.core.domain.model.HomeStyle
import io.github.zaxar1231.mycycle.core.domain.model.SecuritySettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.security.AutoLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App preferences (onboarding flag, theme) live in plain DataStore because they are needed before
 * the database opens and say nothing about the user. Role, usual lengths and the account id are
 * personal and live in the encrypted [ProfileDao]. The DAO is lazy: the database must not open
 * before the key check in the app has passed.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val profileDao: Lazy<ProfileDao>,
) : SettingsRepository {
    private val writeLock = Mutex()

    override val settings: Flow<UserSettings> = flow {
        val prefs = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        emitAll(combine(prefs, profileDao.get().observe()) { p, profile -> merge(p, profile) })
    }

    override suspend fun update(transform: (UserSettings) -> UserSettings) = writeLock.withLock {
        val updated = transform(settings.first())
        store.edit { prefs -> updated.writeTo(prefs) }
        profileDao.get().upsert(
            ProfileEntity(
                role = updated.role.name,
                usualCycleLength = updated.usualCycleLength,
                usualPeriodLength = updated.usualPeriodLength,
                accountId = updated.accountId,
            ),
        )
    }

    /** Clears everything stored next to the database, keeping only the theme. */
    suspend fun resetAfterDataLoss() {
        store.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = false
            prefs.remove(PIN_SUGGESTION_DISMISSED)
            prefs.remove(BIOMETRIC_UNLOCK)
        }
    }

    private fun merge(prefs: Preferences, profile: ProfileEntity?): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            role = enumOrNull<UserRole>(profile?.role) ?: defaults.role,
            accountId = profile?.accountId,
            usualCycleLength = profile?.usualCycleLength ?: defaults.usualCycleLength,
            usualPeriodLength = profile?.usualPeriodLength ?: defaults.usualPeriodLength,
            usualLengthsEstimated = prefs[USUAL_LENGTHS_ESTIMATED] ?: defaults.usualLengthsEstimated,
            theme = ThemeSettings(
                style = enumOrNull<ThemeStyle>(prefs[THEME_STYLE]) ?: defaults.theme.style,
                mode = enumOrNull<ThemeMode>(prefs[THEME_MODE]) ?: defaults.theme.mode,
                flowers = prefs[THEME_FLOWERS] ?: defaults.theme.flowers,
                home = enumOrNull<HomeStyle>(prefs[THEME_HOME]) ?: defaults.theme.home,
                seedColor = prefs[THEME_SEED] ?: defaults.theme.seedColor,
            ),
            pinSuggestionDismissed = prefs[PIN_SUGGESTION_DISMISSED] ?: defaults.pinSuggestionDismissed,
            security = SecuritySettings(
                biometricUnlock = prefs[BIOMETRIC_UNLOCK] ?: defaults.security.biometricUnlock,
                lockAfterMinutes = prefs[LOCK_AFTER_MINUTES]?.takeIf { it in AutoLock.OPTIONS_MINUTES }
                    ?: defaults.security.lockAfterMinutes,
                hideInRecents = prefs[HIDE_IN_RECENTS] ?: defaults.security.hideInRecents,
            ),
        )
    }

    private fun UserSettings.writeTo(prefs: MutablePreferences) {
        prefs[ONBOARDING_COMPLETED] = onboardingCompleted
        prefs[THEME_STYLE] = theme.style.name
        prefs[THEME_MODE] = theme.mode.name
        prefs[THEME_FLOWERS] = theme.flowers
        prefs[THEME_HOME] = theme.home.name
        prefs[THEME_SEED] = theme.seedColor
        prefs[PIN_SUGGESTION_DISMISSED] = pinSuggestionDismissed
        prefs[USUAL_LENGTHS_ESTIMATED] = usualLengthsEstimated
        prefs[BIOMETRIC_UNLOCK] = security.biometricUnlock
        prefs[LOCK_AFTER_MINUTES] = security.lockAfterMinutes
        prefs[HIDE_IN_RECENTS] = security.hideInRecents
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_FLOWERS = booleanPreferencesKey("theme_flowers")
        val THEME_HOME = stringPreferencesKey("theme_home")
        val THEME_SEED = intPreferencesKey("theme_seed")
        val PIN_SUGGESTION_DISMISSED = booleanPreferencesKey("pin_suggestion_dismissed")
        val USUAL_LENGTHS_ESTIMATED = booleanPreferencesKey("usual_lengths_estimated")
        val BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
        val LOCK_AFTER_MINUTES = intPreferencesKey("lock_after_minutes")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
    }
}
