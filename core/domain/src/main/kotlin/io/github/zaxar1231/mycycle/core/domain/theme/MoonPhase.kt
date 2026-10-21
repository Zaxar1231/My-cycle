package io.github.zaxar1231.mycycle.core.domain.theme

import kotlin.math.PI
import kotlin.math.cos

/**
 * The cycle drawn as a moon: the new moon on day 1, the full moon on the ovulation day, waning
 * again until the next period. It is a picture of the cycle, not a link to the real moon.
 */
data class MoonPhase(
    /** 0 new moon, 0.5 full moon, towards 1 new again. */
    val phase: Float,
) {
    /** Lit share of the disc, 0–1. */
    val illumination: Float get() = ((1 - cos(2 * PI * phase)) / 2).toFloat()
    val waxing: Boolean get() = phase < HALF

    companion object {
        private const val HALF = 0.5f

        fun of(cycleDay: Int, cycleLength: Int, ovulationDay: Int): MoonPhase {
            val length = cycleLength.coerceAtLeast(2)
            val ovulation = ovulationDay.coerceIn(2, length)
            val day = cycleDay.coerceIn(1, length)
            val phase = if (day <= ovulation) {
                HALF * (day - 1) / (ovulation - 1)
            } else {
                HALF + HALF * (day - ovulation) / (length - ovulation + 1)
            }
            return MoonPhase(phase)
        }
    }
}
