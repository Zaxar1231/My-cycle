package io.github.zaxar1231.mycycle.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors of cycle phases and calendar marks. Always derived from the active color scheme. */
@Immutable
data class CycleColors(
    val period: Color,
    val onPeriod: Color,
    val predictedPeriod: Color,
    val fertile: Color,
    val onFertile: Color,
    val ovulation: Color,
    val onOvulation: Color,
    val luteal: Color,
    val follicular: Color,
)

fun ColorScheme.toCycleColors() = CycleColors(
    period = secondary,
    onPeriod = onSecondary,
    predictedPeriod = secondary.copy(alpha = PREDICTED_ALPHA),
    fertile = tertiaryContainer,
    onFertile = onTertiaryContainer,
    ovulation = tertiary,
    onOvulation = onTertiary,
    luteal = primaryContainer,
    follicular = surfaceVariant,
)

/** Muted range bars like a paper planner: rose for the period, slate for fertile days, lavender ovulation. */
internal fun roseCycleColors(scheme: ColorScheme, dark: Boolean) = CycleColors(
    period = if (dark) Color(0xFF6E4C55) else Color(0xFFF2C9D6),
    onPeriod = scheme.onSurface,
    predictedPeriod = scheme.primary.copy(alpha = PREDICTED_ALPHA),
    fertile = if (dark) Color(0xFF3C3B4D) else Color(0xFFE1E0F2),
    onFertile = scheme.onSurface,
    ovulation = scheme.tertiary,
    onOvulation = scheme.onTertiary,
    luteal = scheme.surfaceVariant,
    follicular = scheme.secondaryContainer,
)

private const val PREDICTED_ALPHA = 0.35f

val LocalCycleColors = staticCompositionLocalOf { TwilightLight.toCycleColors() }
