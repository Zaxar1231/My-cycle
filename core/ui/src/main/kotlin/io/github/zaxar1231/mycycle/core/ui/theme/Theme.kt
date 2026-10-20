package io.github.zaxar1231.mycycle.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme. Material You (colors from the wallpaper) is the default on Android 12+,
 * the twilight palette is used elsewhere.
 */
@Composable
fun TwilightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TwilightDark
        else -> TwilightLight
    }
    CompositionLocalProvider(LocalCycleColors provides colorScheme.toCycleColors()) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object TwilightThemeExtras {
    val cycleColors: CycleColors
        @Composable @ReadOnlyComposable get() = LocalCycleColors.current
}
