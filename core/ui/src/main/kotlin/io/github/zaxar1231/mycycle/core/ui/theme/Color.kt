package io.github.zaxar1231.mycycle.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback "twilight" palette for devices without dynamic color (Android 11 and older)
// or when the user turns Material You off.
internal val TwilightLight = lightColorScheme(
    primary = Color(0xFF7A4E9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1DBFF),
    onPrimaryContainer = Color(0xFF2E0A4A),
    secondary = Color(0xFFB4436C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF3E0021),
    tertiary = Color(0xFF3F6A8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF001E30),
    background = Color(0xFFFFF7FC),
    onBackground = Color(0xFF1E1A20),
    surface = Color(0xFFFFF7FC),
    onSurface = Color(0xFF1E1A20),
    surfaceVariant = Color(0xFFEBDFEA),
    onSurfaceVariant = Color(0xFF4C444E),
)

internal val TwilightDark = darkColorScheme(
    primary = Color(0xFFDFB7FF),
    onPrimary = Color(0xFF45226A),
    primaryContainer = Color(0xFF5E3A82),
    onPrimaryContainer = Color(0xFFF1DBFF),
    secondary = Color(0xFFFFB1C8),
    onSecondary = Color(0xFF5E1136),
    secondaryContainer = Color(0xFF7C2A4D),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFF9BCBF5),
    onTertiary = Color(0xFF00344F),
    tertiaryContainer = Color(0xFF244D6B),
    onTertiaryContainer = Color(0xFFCBE6FF),
    background = Color(0xFF16121A),
    onBackground = Color(0xFFE9E0E8),
    surface = Color(0xFF16121A),
    onSurface = Color(0xFFE9E0E8),
    surfaceVariant = Color(0xFF4C444E),
    onSurfaceVariant = Color(0xFFCEC3CF),
)
