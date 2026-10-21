package io.github.zaxar1231.mycycle.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.model.BlockLook
import io.github.zaxar1231.mycycle.core.domain.model.BlockSize
import io.github.zaxar1231.mycycle.core.domain.model.BlockStyle
import io.github.zaxar1231.mycycle.core.ui.theme.LocalDarkTheme

/** The style of the home block being drawn; cards from any feature read it through [BlockCard]. */
val LocalBlockStyle = compositionLocalOf { BlockStyle() }

/**
 * A home screen card drawn in the user's chosen look, size, corners and colour. With
 * [BlockLook.NONE] the content sits on the background without padding.
 */
@Composable
fun BlockCard(modifier: Modifier = Modifier, style: BlockStyle = LocalBlockStyle.current, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val custom = style.color?.let { Color(it) }
    val shape = RoundedCornerShape(style.cornerDp.dp)
    val glassPane = if (LocalDarkTheme.current) Color.White.copy(alpha = GLASS_DARK) else Color.White.copy(alpha = GLASS_LIGHT)
    val container = when (style.look) {
        BlockLook.NONE, BlockLook.OUTLINED -> Color.Transparent
        BlockLook.FILLED -> custom ?: scheme.surfaceContainerHigh
        BlockLook.TONAL -> custom?.copy(alpha = TONAL_ALPHA) ?: scheme.primaryContainer
        BlockLook.GLASS -> custom?.copy(alpha = GLASS_TINT) ?: glassPane
    }
    // Text picks black or white against a custom fill, so any colour from the wheel stays readable.
    val textColor = when (style.look) {
        BlockLook.FILLED -> custom?.let { if (it.luminance() > EQUAL_CONTRAST) Color(0xFF1C1B1F) else Color.White } ?: scheme.onSurface
        BlockLook.TONAL -> if (custom != null) scheme.onSurface else scheme.onPrimaryContainer
        else -> LocalContentColor.current
    }
    val border = when (style.look) {
        BlockLook.OUTLINED -> BorderStroke(1.dp, custom ?: scheme.outlineVariant)
        BlockLook.GLASS -> BorderStroke(1.dp, Color.White.copy(alpha = GLASS_EDGE))
        else -> null
    }
    val padding = when {
        style.look == BlockLook.NONE -> 0.dp
        style.size == BlockSize.COMPACT -> 14.dp
        style.size == BlockSize.NORMAL -> 24.dp
        else -> 32.dp
    }
    val density = LocalDensity.current
    val scale = when (style.size) {
        BlockSize.COMPACT -> COMPACT_TEXT
        BlockSize.NORMAL -> 1f
        BlockSize.LARGE -> LARGE_TEXT
    }
    // On a custom fill every role children read from the theme follows the text colour, so
    // calendar numbers, chips and the disclaimer stay readable, not just plain Text.
    val innerScheme = if (custom != null && style.look == BlockLook.FILLED) {
        scheme.copy(
            onSurface = textColor,
            onSurfaceVariant = textColor.copy(alpha = VARIANT_ALPHA),
            outline = textColor.copy(alpha = OUTLINE_ALPHA),
            outlineVariant = textColor.copy(alpha = OUTLINE_VARIANT_ALPHA),
            primary = textColor,
            onPrimary = custom,
            onPrimaryContainer = textColor,
        )
    } else {
        scheme
    }
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) {
        MaterialTheme(colorScheme = innerScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            if (style.look == BlockLook.NONE) {
                // No container: nothing to clip, so day circles and ripples near the corners stay whole.
                CompositionLocalProvider(LocalContentColor provides textColor) {
                    Box(modifier.fillMaxWidth()) { content() }
                }
            } else {
                Surface(modifier.fillMaxWidth(), shape = shape, color = container, contentColor = textColor, border = border) {
                    Box(Modifier.padding(padding)) { content() }
                }
            }
        }
    }
}

/** The luminance at which black and white text have the same contrast. */
private const val EQUAL_CONTRAST = 0.179f
private const val VARIANT_ALPHA = 0.75f
private const val OUTLINE_ALPHA = 0.6f
private const val OUTLINE_VARIANT_ALPHA = 0.3f
private const val TONAL_ALPHA = 0.35f
private const val GLASS_TINT = 0.3f
private const val GLASS_DARK = 0.1f
private const val GLASS_LIGHT = 0.5f
private const val GLASS_EDGE = 0.35f
private const val COMPACT_TEXT = 0.92f
private const val LARGE_TEXT = 1.12f
