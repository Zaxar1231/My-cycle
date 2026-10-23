package io.github.zaxar1231.mycycle.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.zaxar1231.mycycle.core.domain.model.AppFont
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle

/**
 * App theme. "Twilight rose" is the default; Material You (colors from the wallpaper, Android 12+),
 * the violet "twilight" palette and a custom scheme built from a colour on the wheel are options.
 * Material You falls back to "twilight" on older devices.
 */
@Composable
fun TwilightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: ThemeStyle = ThemeStyle.ROSE,
    showFlowers: Boolean = true,
    seedColor: Int = ThemeSettings.DEFAULT_SEED_COLOR,
    font: AppFont = AppFont.SYSTEM,
    customFontFile: String? = null,
    wallpaperFile: String? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val typography = remember(font, customFontFile) { typographyWith(font.family(context, customFontFile)) }
    val dark = darkTheme || style.alwaysDark
    val colorScheme = when {
        style == ThemeStyle.ROSE -> if (dark) RoseDark else RoseLight
        style == ThemeStyle.CUSTOM -> remember(seedColor, dark) { seedColorScheme(seedColor, dark) }
        style == ThemeStyle.LIQUID_GLASS -> remember(dark) { glassScheme(dark) }
        style == ThemeStyle.AMOLED -> remember { amoledScheme() }
        style == ThemeStyle.NEON -> remember { neonScheme() }
        style == ThemeStyle.PASTEL -> remember(dark) { pastelScheme(dark) }
        style == ThemeStyle.PAPER -> remember(dark) { paperScheme(dark) }
        style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> TwilightDark
        else -> TwilightLight
    }
    val cycleColors = if (style == ThemeStyle.ROSE) roseCycleColors(colorScheme, dark) else colorScheme.toCycleColors()
    val glass = style == ThemeStyle.LIQUID_GLASS
    val solid = if (glass) glassBase(dark) else colorScheme.background
    CompositionLocalProvider(
        LocalCycleColors provides cycleColors,
        LocalShowFlowers provides showFlowers,
        LocalThemeStyle provides style,
        LocalSolidBackground provides solid,
        LocalDarkTheme provides dark,
    ) {
        // With a picture behind, the backgrounds turn transparent so it shows through the screens.
        val shown = if (wallpaperFile != null) colorScheme.copy(background = Color.Transparent, surface = Color.Transparent) else colorScheme
        MaterialTheme(colorScheme = shown, typography = typography) {
            // The same tree in every style: switching to glass must not recreate the app below.
            Box {
                if (wallpaperFile != null) {
                    WallpaperBackdrop(wallpaperFile, Modifier.matchParentSize())
                } else if (glass) {
                    GlassBackdrop(colorScheme, solid, Modifier.matchParentSize())
                }
                content()
            }
        }
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
    TwilightTheme(
        darkTheme = dark,
        style = settings.style,
        showFlowers = settings.flowers,
        seedColor = settings.seedColor,
        font = settings.font,
        customFontFile = settings.customFontFile,
        wallpaperFile = settings.wallpaperFile,
        content = content,
    )
}

/** Whether decorative flowers are drawn (the user can turn them off). */
val LocalShowFlowers = staticCompositionLocalOf { true }

/** The active style, for components that look different per style, such as the PIN pad. */
val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.ROSE }

/**
 * An opaque background colour. Under Liquid Glass the scheme's background is transparent, so
 * screens that must hide what is behind them, like the lock screen, paint this instead.
 */
val LocalSolidBackground = staticCompositionLocalOf { Color.Black }

/** Whether the effective theme is dark, including styles that are always dark. */
val LocalDarkTheme = staticCompositionLocalOf { false }

object TwilightThemeExtras {
    val cycleColors: CycleColors
        @Composable @ReadOnlyComposable get() = LocalCycleColors.current

    val solidBackground: Color
        @Composable @ReadOnlyComposable get() = LocalSolidBackground.current

    val style: ThemeStyle
        @Composable @ReadOnlyComposable get() = LocalThemeStyle.current
}
