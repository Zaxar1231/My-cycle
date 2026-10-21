package io.github.zaxar1231.mycycle.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.zaxar1231.mycycle.core.domain.theme.Tones

/**
 * A full Material 3 colour scheme from one seed colour, like Material You does from the
 * wallpaper: primary keeps the seed's hue, secondary is a calmer version of it, tertiary is
 * turned 60° around the wheel, and backgrounds are almost grey with a hint of the hue.
 * Text sits on tones far enough apart for readable contrast.
 */
fun seedColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val (hue, seedChroma) = Tones.hueChroma(seed)
    // A grey or white seed stays almost monochrome instead of picking up an accidental hue.
    val chroma = if (seedChroma < NEUTRAL_SEED_CHROMA) seedChroma else seedChroma.coerceAtLeast(MIN_PRIMARY_CHROMA)
    val primary = Palette(hue, chroma)
    val secondary = Palette(hue, chroma / SECONDARY_DIVISOR)
    val tertiary = Palette((hue + TERTIARY_TURN) % FULL_TURN, chroma / TERTIARY_DIVISOR)
    val neutral = Palette(hue, NEUTRAL_CHROMA)
    val neutralVariant = Palette(hue, NEUTRAL_VARIANT_CHROMA)
    val error = Palette(ERROR_HUE, ERROR_CHROMA)
    return if (dark) {
        darkColorScheme(
            primary = primary(80), onPrimary = primary(20), primaryContainer = primary(30), onPrimaryContainer = primary(90),
            inversePrimary = primary(40),
            secondary = secondary(80), onSecondary = secondary(20), secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
            tertiary = tertiary(80), onTertiary = tertiary(20), tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
            background = neutral(6), onBackground = neutral(90),
            surface = neutral(6), onSurface = neutral(90),
            surfaceVariant = neutralVariant(30), onSurfaceVariant = neutralVariant(80),
            surfaceTint = primary(80),
            inverseSurface = neutral(90), inverseOnSurface = neutral(20),
            error = error(80), onError = error(20), errorContainer = error(30), onErrorContainer = error(90),
            outline = neutralVariant(60), outlineVariant = neutralVariant(30),
            scrim = Color.Black,
            surfaceBright = neutral(24), surfaceDim = neutral(6),
            surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10), surfaceContainer = neutral(12),
            surfaceContainerHigh = neutral(17), surfaceContainerHighest = neutral(22),
        )
    } else {
        lightColorScheme(
            primary = primary(40), onPrimary = primary(100), primaryContainer = primary(90), onPrimaryContainer = primary(10),
            inversePrimary = primary(80),
            secondary = secondary(40), onSecondary = secondary(100), secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
            tertiary = tertiary(40), onTertiary = tertiary(100), tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
            background = neutral(98), onBackground = neutral(10),
            surface = neutral(98), onSurface = neutral(10),
            surfaceVariant = neutralVariant(90), onSurfaceVariant = neutralVariant(30),
            surfaceTint = primary(40),
            inverseSurface = neutral(20), inverseOnSurface = neutral(95),
            error = error(40), onError = error(100), errorContainer = error(90), onErrorContainer = error(10),
            outline = neutralVariant(50), outlineVariant = neutralVariant(80),
            scrim = Color.Black,
            surfaceBright = neutral(98), surfaceDim = neutral(87),
            surfaceContainerLowest = neutral(100), surfaceContainerLow = neutral(96), surfaceContainer = neutral(94),
            surfaceContainerHigh = neutral(92), surfaceContainerHighest = neutral(90),
        )
    }
}

private class Palette(private val hue: Float, private val chroma: Float) {
    operator fun invoke(tone: Int) = Color(Tones.color(hue, chroma, tone.toFloat()))
}

private const val MIN_PRIMARY_CHROMA = 36f
private const val NEUTRAL_SEED_CHROMA = 6f
private const val SECONDARY_DIVISOR = 3f
private const val TERTIARY_DIVISOR = 2f
private const val TERTIARY_TURN = 60f
private const val FULL_TURN = 360f
private const val NEUTRAL_CHROMA = 4f
private const val NEUTRAL_VARIANT_CHROMA = 8f
private const val ERROR_HUE = 25f
private const val ERROR_CHROMA = 60f
