package io.github.zaxar1231.mycycle.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.zaxar1231.mycycle.R
import io.github.zaxar1231.mycycle.feature.calendar.CalendarRoute
import io.github.zaxar1231.mycycle.feature.pet.PetRoute
import io.github.zaxar1231.mycycle.feature.settings.SettingsRoute
import io.github.zaxar1231.mycycle.feature.stats.StatsRoute
import kotlin.reflect.KClass
import io.github.zaxar1231.mycycle.core.ui.R as UiR

/** Tabs of the bottom bar. The "+" button in the middle is not a tab: it opens the day log. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    CALENDAR(CalendarRoute, CalendarRoute::class, R.string.nav_calendar, UiR.drawable.ic_nav_calendar),
    STATS(StatsRoute, StatsRoute::class, R.string.nav_stats, UiR.drawable.ic_nav_stats),
    PET(PetRoute, PetRoute::class, R.string.nav_pet, UiR.drawable.ic_nav_pet),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.nav_settings, UiR.drawable.ic_nav_settings),
    ;

    companion object {
        /** Tabs left of the "+" button; the rest go to its right. */
        val leftOfFab = listOf(CALENDAR, STATS)
        val rightOfFab = listOf(PET, SETTINGS)
    }
}
