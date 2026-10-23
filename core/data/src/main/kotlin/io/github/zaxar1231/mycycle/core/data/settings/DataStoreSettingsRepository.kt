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
import io.github.zaxar1231.mycycle.core.domain.model.AppFont
import io.github.zaxar1231.mycycle.core.domain.model.HomeLayoutCodec
import io.github.zaxar1231.mycycle.core.domain.model.HomeStyle
import io.github.zaxar1231.mycycle.core.domain.model.SecuritySettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.reminders.ReminderSettings
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
import java.time.LocalTime
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
                font = enumOrNull<AppFont>(prefs[THEME_FONT]) ?: defaults.theme.font,
                customFontFile = prefs[THEME_CUSTOM_FONT],
                wallpaperFile = prefs[THEME_WALLPAPER],
            ),
            pinSuggestionDismissed = prefs[PIN_SUGGESTION_DISMISSED] ?: defaults.pinSuggestionDismissed,
            homeLayout = HomeLayoutCodec.decode(prefs[HOME_LAYOUT]),
            security = SecuritySettings(
                biometricUnlock = prefs[BIOMETRIC_UNLOCK] ?: defaults.security.biometricUnlock,
                lockAfterMinutes = prefs[LOCK_AFTER_MINUTES]?.takeIf { it in AutoLock.OPTIONS_MINUTES }
                    ?: defaults.security.lockAfterMinutes,
                hideInRecents = prefs[HIDE_IN_RECENTS] ?: defaults.security.hideInRecents,
            ),
            reminders = ReminderSettings(
                periodSoon = prefs[REMIND_PERIOD] ?: defaults.reminders.periodSoon,
                periodDaysBefore = prefs[REMIND_PERIOD_DAYS]?.takeIf { it in ReminderSettings.DAYS_BEFORE_OPTIONS }
                    ?: defaults.reminders.periodDaysBefore,
                fertileWindow = prefs[REMIND_FERTILE] ?: defaults.reminders.fertileWindow,
                ovulation = prefs[REMIND_OVULATION] ?: defaults.reminders.ovulation,
                dailyLog = prefs[REMIND_LOG] ?: defaults.reminders.dailyLog,
                dailyLogTime = prefs[REMIND_LOG_MINUTE]?.takeIf { it in 0 until MINUTES_PER_DAY }
                    ?.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) } ?: defaults.reminders.dailyLogTime,
                medications = prefs[REMIND_MEDICATIONS] ?: defaults.reminders.medications,
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
        prefs[THEME_FONT] = theme.font.name
        theme.customFontFile?.let { prefs[THEME_CUSTOM_FONT] = it } ?: prefs.remove(THEME_CUSTOM_FONT)
        theme.wallpaperFile?.let { prefs[THEME_WALLPAPER] = it } ?: prefs.remove(THEME_WALLPAPER)
        prefs[PIN_SUGGESTION_DISMISSED] = pinSuggestionDismissed
        prefs[USUAL_LENGTHS_ESTIMATED] = usualLengthsEstimated
        prefs[BIOMETRIC_UNLOCK] = security.biometricUnlock
        prefs[LOCK_AFTER_MINUTES] = security.lockAfterMinutes
        prefs[HIDE_IN_RECENTS] = security.hideInRecents
        prefs[HOME_LAYOUT] = HomeLayoutCodec.encode(homeLayout)
        prefs[REMIND_PERIOD] = reminders.periodSoon
        prefs[REMIND_PERIOD_DAYS] = reminders.periodDaysBefore
        prefs[REMIND_FERTILE] = reminders.fertileWindow
        prefs[REMIND_OVULATION] = reminders.ovulation
        prefs[REMIND_LOG] = reminders.dailyLog
        prefs[REMIND_LOG_MINUTE] = reminders.dailyLogTime.hour * MINUTES_PER_HOUR + reminders.dailyLogTime.minute
        prefs[REMIND_MEDICATIONS] = reminders.medications
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_FLOWERS = booleanPreferencesKey("theme_flowers")
        val THEME_HOME = stringPreferencesKey("theme_home")
        val THEME_SEED = intPreferencesKey("theme_seed")
        val THEME_FONT = stringPreferencesKey("theme_font")
        val THEME_CUSTOM_FONT = stringPreferencesKey("theme_custom_font")
        val THEME_WALLPAPER = stringPreferencesKey("theme_wallpaper")
        val PIN_SUGGESTION_DISMISSED = booleanPreferencesKey("pin_suggestion_dismissed")
        val USUAL_LENGTHS_ESTIMATED = booleanPreferencesKey("usual_lengths_estimated")
        val BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
        val LOCK_AFTER_MINUTES = intPreferencesKey("lock_after_minutes")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val REMIND_PERIOD = booleanPreferencesKey("remind_period")
        val REMIND_PERIOD_DAYS = intPreferencesKey("remind_period_days")
        val REMIND_FERTILE = booleanPreferencesKey("remind_fertile")
        val REMIND_OVULATION = booleanPreferencesKey("remind_ovulation")
        val REMIND_LOG = booleanPreferencesKey("remind_log")
        val REMIND_LOG_MINUTE = intPreferencesKey("remind_log_minute")
        val REMIND_MEDICATIONS = booleanPreferencesKey("remind_medications")
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
    }
}
