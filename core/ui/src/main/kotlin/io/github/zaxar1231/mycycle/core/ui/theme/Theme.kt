package io.github.zaxar1231.mycycle.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle

/**
 * App theme. "Twilight rose" is the default; Material You (colors from the wallpaper, Android 12+)
 * and the violet "twilight" palette are options. Material You falls back to "twilight" on older devices.
 */
@Composable
fun TwilightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: ThemeStyle = ThemeStyle.ROSE,
    showFlowers: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        style == ThemeStyle.ROSE -> if (darkTheme) RoseDark else RoseLight
        style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TwilightDark
        else -> TwilightLight
    }
    val cycleColors = if (style == ThemeStyle.ROSE) roseCycleColors(colorScheme, darkTheme) else colorScheme.toCycleColors()
    CompositionLocalProvider(
        LocalCycleColors provides cycleColors,
        LocalShowFlowers provides showFlowers,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** Applies the user's saved theme choice. */
@Composable
fun TwilightTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    TwilightTheme(darkTheme = dark, style = settings.style, showFlowers = settings.flowers, content = content)
}

/** Whether decorative flowers are drawn (the user can turn them off). */
val LocalShowFlowers = staticCompositionLocalOf { true }

object TwilightThemeExtras {
    val cycleColors: CycleColors
        @Composable @ReadOnlyComposable get() = LocalCycleColors.current
}
