package io.github.zaxar1231.mycycle.core.domain.insights

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants.LUTEAL_PHASE_LENGTH

enum class Hormone { ESTRADIOL, PROGESTERONE, LH, FSH, TESTOSTERONE }

enum class HormoneLevel { LOW, MEDIUM, HIGH }

/**
 * Typical, normalized hormone curves (0..1, each relative to its own peak) for a 28-day cycle with
 * ovulation on day 14, stretched to the user's cycle. A model for illustration, not a blood test.
 *
 * The follicular part (up to ovulation) is stretched to the user's ovulation day and the luteal part
 * to the days after it, because cycles differ mostly in the length of the follicular phase.
 */
object HormoneModel {
    private const val TEMPLATE_OVULATION_DAY = 14.0
    private const val TEMPLATE_LUTEAL_DAYS = LUTEAL_PHASE_LENGTH.toDouble()
    private const val HIGH_LEVEL = 0.66
    private const val MEDIUM_LEVEL = 0.33
    private const val MIN_CYCLE_LENGTH = 3

    /** Key points (template day to level), interpolated linearly. Day 1 is the first day of the period. */
    private val templates: Map<Hormone, List<Pair<Double, Double>>> = mapOf(
        Hormone.ESTRADIOL to listOf(1.0 to 0.15, 5.0 to 0.2, 10.0 to 0.5, 13.0 to 1.0, 14.5 to 0.55, 16.0 to 0.35, 21.0 to 0.65, 25.0 to 0.45, 28.0 to 0.15),
        Hormone.PROGESTERONE to listOf(1.0 to 0.05, 13.0 to 0.05, 15.0 to 0.15, 18.0 to 0.6, 21.0 to 1.0, 24.0 to 0.8, 28.0 to 0.1),
        Hormone.LH to listOf(1.0 to 0.1, 12.0 to 0.15, 13.0 to 0.55, 14.0 to 1.0, 15.0 to 0.4, 17.0 to 0.12, 28.0 to 0.1),
        Hormone.FSH to listOf(1.0 to 0.45, 4.0 to 0.55, 8.0 to 0.35, 12.0 to 0.3, 14.0 to 0.7, 15.5 to 0.4, 18.0 to 0.2, 26.0 to 0.25, 28.0 to 0.45),
        Hormone.TESTOSTERONE to listOf(1.0 to 0.4, 10.0 to 0.55, 14.0 to 0.8, 16.0 to 0.6, 22.0 to 0.45, 28.0 to 0.4),
    )

    /** Level of [hormone] on [cycleDay] (1-based) of a cycle of [cycleLength] days with ovulation on [ovulationDay]. */
    fun level(hormone: Hormone, cycleDay: Int, cycleLength: Int, ovulationDay: Int): Double =
        interpolate(templates.getValue(hormone), templateDay(cycleDay, cycleLength, ovulationDay))

    /** Levels for every day of the cycle, day 1 first. */
    fun curve(hormone: Hormone, cycleLength: Int, ovulationDay: Int): List<Double> =
        (1..cycleLength).map { level(hormone, it, cycleLength, ovulationDay) }

    fun levelOf(value: Double): HormoneLevel = when {
        value >= HIGH_LEVEL -> HormoneLevel.HIGH
        value >= MEDIUM_LEVEL -> HormoneLevel.MEDIUM
        else -> HormoneLevel.LOW
    }

    private fun templateDay(cycleDay: Int, cycleLength: Int, ovulationDay: Int): Double {
        require(cycleLength >= MIN_CYCLE_LENGTH) { "cycle of $cycleLength days is too short for the hormone model" }
        val ovulation = ovulationDay.coerceIn(2, cycleLength - 1).toDouble()
        val day = cycleDay.coerceIn(1, cycleLength).toDouble()
        return if (day <= ovulation) {
            1 + (day - 1) * (TEMPLATE_OVULATION_DAY - 1) / (ovulation - 1)
        } else {
            TEMPLATE_OVULATION_DAY + (day - ovulation) * TEMPLATE_LUTEAL_DAYS / (cycleLength - ovulation)
        }
    }

    private fun interpolate(points: List<Pair<Double, Double>>, day: Double): Double {
        if (day <= points.first().first) return points.first().second
        val upper = points.indexOfFirst { it.first >= day }.takeIf { it > 0 } ?: return points.last().second
        val (x0, y0) = points[upper - 1]
        val (x1, y1) = points[upper]
        return y0 + (y1 - y0) * (day - x0) / (x1 - x0)
    }
}
