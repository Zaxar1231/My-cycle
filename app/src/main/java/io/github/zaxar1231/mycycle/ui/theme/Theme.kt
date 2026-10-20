package io.github.zaxar1231.mycycle.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFFC2416B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF9E1E8),
    onPrimaryContainer = Color(0xFF3E0A1D),
    background = Color(0xFFFBF6F7),
    surface = Color(0xFFFBF6F7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF08BAA),
    onPrimary = Color(0xFF4A0D24),
    primaryContainer = Color(0xFF6B2340),
    onPrimaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF1C1418),
    surface = Color(0xFF1C1418),
)

@Composable
fun MyCycleTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
