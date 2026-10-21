package io.github.zaxar1231.mycycle.core.ui.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/*
 * Extra styles. Each starts from a scheme built from one seed colour (so every Material role is
 * filled and readable) and then overrides what makes the style:
 * - AMOLED: pure black, bright accent; always dark.
 * - Neon: deep night, cyan, magenta and lime accents; always dark.
 * - Pastel: soft pink, lavender and mint.
 * - Paper: cream paper with sepia ink, like a planner.
 * - Liquid Glass: translucent containers over soft colour blobs, blurred on Android 12+.
 */

internal fun amoledScheme(): ColorScheme = seedColorScheme(AMOLED_SEED, dark = true).copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1C1C1C),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    // Not equal to the card colour, so cards keep onSurface text.
    surfaceVariant = Color(0xFF202020),
)

internal fun neonScheme(): ColorScheme = seedColorScheme(NEON_CYAN.toArgb(), dark = true).copy(
    primary = NEON_CYAN,
    onPrimary = Color(0xFF00201F),
    secondary = NEON_MAGENTA,
    onSecondary = Color(0xFF2E0029),
    tertiary = NEON_LIME,
    onTertiary = Color(0xFF142000),
    background = NEON_NIGHT,
    surface = NEON_NIGHT,
    surfaceDim = NEON_NIGHT,
    surfaceContainerLowest = Color(0xFF05040C),
    surfaceContainerLow = Color(0xFF0E0C1C),
    surfaceContainer = Color(0xFF131126),
    surfaceContainerHigh = Color(0xFF1A1731),
    surfaceContainerHighest = Color(0xFF221E3D),
    outline = Color(0xFF5C57A0),
    outlineVariant = Color(0xFF332F5E),
)

internal fun pastelScheme(dark: Boolean): ColorScheme = if (dark) {
    seedColorScheme(PASTEL_SEED, dark = true).copy(
        tertiary = Color(0xFFA8E6CF),
        onTertiary = Color(0xFF00382A),
        tertiaryContainer = Color(0xFF2C5145),
        onTertiaryContainer = Color(0xFFC9F5E4),
    )
} else {
    seedColorScheme(PASTEL_SEED, dark = false).copy(
        background = Color(0xFFFFF8FB),
        surface = Color(0xFFFFF8FB),
        primaryContainer = Color(0xFFFCDDE6),
        secondaryContainer = Color(0xFFE9E1F7),
        tertiary = Color(0xFF3F7A66),
        tertiaryContainer = Color(0xFFD3F3E6),
        onTertiaryContainer = Color(0xFF0B3528),
        surfaceContainerLow = Color(0xFFFFF1F6),
        surfaceContainer = Color(0xFFFBECF2),
        surfaceContainerHigh = Color(0xFFF7E7EE),
        surfaceContainerHighest = Color(0xFFF2E2EA),
    )
}

internal fun paperScheme(dark: Boolean): ColorScheme = if (dark) {
    seedColorScheme(PAPER_SEED, dark = true).copy(
        background = Color(0xFF1E1B16),
        surface = Color(0xFF1E1B16),
        onBackground = Color(0xFFE8DFCF),
        onSurface = Color(0xFFE8DFCF),
        surfaceContainerLow = Color(0xFF231F19),
        surfaceContainer = Color(0xFF28241D),
        surfaceContainerHigh = Color(0xFF2E2921),
        surfaceContainerHighest = Color(0xFF342E25),
        outlineVariant = Color(0xFF4A4236),
    )
} else {
    seedColorScheme(PAPER_SEED, dark = false).copy(
        background = Color(0xFFF5EFE3),
        surface = Color(0xFFF5EFE3),
        onBackground = Color(0xFF2B2620),
        onSurface = Color(0xFF2B2620),
        surfaceContainerLowest = Color(0xFFFAF6EE),
        surfaceContainerLow = Color(0xFFF1EADC),
        surfaceContainer = Color(0xFFEDE5D5),
        surfaceContainerHigh = Color(0xFFE9E0CE),
        surfaceContainerHighest = Color(0xFFE4DAC6),
        outlineVariant = Color(0xFFCFC3AE),
    )
}

/**
 * Glass: the screen background is transparent so [GlassBackdrop] shows through; cards and bars
 * are translucent, while sheets and dialogs stay almost opaque to keep text readable.
 */
internal fun glassScheme(dark: Boolean): ColorScheme {
    val base = seedColorScheme(GLASS_SEED, dark)
    val tint = if (dark) Color(0xFF15182A) else Color(0xFFF4F6FF)
    return base.copy(
        background = Color.Transparent,
        surface = Color.Transparent,
        surfaceContainerLowest = tint.copy(alpha = SHEET_ALPHA),
        surfaceContainerLow = tint.copy(alpha = SHEET_ALPHA),
        surfaceContainer = tint.copy(alpha = BAR_ALPHA),
        surfaceContainerHigh = tint.copy(alpha = SHEET_ALPHA),
        // Tinted panes dense enough for body text over the blobs.
        surfaceContainerHighest = tint.copy(alpha = if (dark) CARD_ALPHA_DARK else CARD_ALPHA_LIGHT),
        // Kept different from the card colour, so cards keep onSurface text (contentColorFor
        // checks surfaceVariant first), and opaque for tracks and phase bars.
        surfaceVariant = base.surfaceVariant,
    )
}

/** The solid colour under a glass scheme, for screens that must hide what is behind them. */
internal fun glassBase(dark: Boolean) = if (dark) Color(0xFF0D1020) else Color(0xFFEEF1FB)

/**
 * Soft colour blobs behind the glass. On Android 12+ they are blurred with RenderEffect; older
 * versions draw the same blobs as radial gradients, which look soft enough without the blur.
 */
@Composable
fun GlassBackdrop(scheme: ColorScheme, base: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(base)) {
        val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(BLUR) else Modifier
        Canvas(Modifier.fillMaxSize().then(blurred)) {
            val blobs = listOf(
                Triple(Offset(0.15f, 0.12f), scheme.primaryContainer, 0.55f),
                Triple(Offset(0.9f, 0.35f), scheme.tertiaryContainer, 0.5f),
                Triple(Offset(0.3f, 0.8f), scheme.secondaryContainer, 0.6f),
            )
            blobs.forEach { (at, color, share) ->
                val c = Offset(at.x * size.width, at.y * size.height)
                val r = size.minDimension * share
                // Container tones (dark in the dark theme, pale in the light one) keep text contrast.
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = BLOB_ALPHA), color.copy(alpha = 0f)), c, r), r, c)
            }
        }
    }
}

private const val AMOLED_SEED = 0xFFB388FF.toInt()
private const val PASTEL_SEED = 0xFFF4A7B9.toInt()
private const val PAPER_SEED = 0xFF8B5E3C.toInt()
private const val GLASS_SEED = 0xFF7FA7FF.toInt()
private val NEON_CYAN = Color(0xFF00E5FF)
private val NEON_MAGENTA = Color(0xFFFF4FD8)
private val NEON_LIME = Color(0xFFC6FF3D)
private val NEON_NIGHT = Color(0xFF07060F)
private const val SHEET_ALPHA = 0.94f
private const val BAR_ALPHA = 0.72f
private const val CARD_ALPHA_DARK = 0.6f
private const val CARD_ALPHA_LIGHT = 0.65f
private const val BLOB_ALPHA = 0.7f
private val BLUR = 60.dp
