package io.github.zaxar1231.mycycle.core.domain.insights

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ChanceLevel { LOW, MEDIUM, HIGH }

/**
 * A rough model estimate of the chance to conceive on a day. Never a contraception method: the
 * ovulation date itself is a prediction.
 */
data class PregnancyChance(
    val level: ChanceLevel,
    val percent: Int,
    /** Days from the predicted (or confirmed) ovulation: negative before it. */
    val daysFromOvulation: Int,
)

object PregnancyChanceModel {
    /** Null when [date] is outside the timeline, for example before the first logged period. */
    fun on(date: LocalDate, prediction: CyclePrediction): PregnancyChance? {
        val segment = prediction.segmentFor(date) ?: return null
        val offset = ChronoUnit.DAYS.between(segment.ovulation, date).toInt()
        val inPeriod = !segment.isPredicted && !date.isAfter(segment.periodEnd)
        val percent = if (inPeriod) {
            CycleConstants.CONCEPTION_CHANCE_OUTSIDE_WINDOW
        } else {
            CycleConstants.CONCEPTION_CHANCE_BY_DAY[offset] ?: CycleConstants.CONCEPTION_CHANCE_OUTSIDE_WINDOW
        }
        val level = when {
            percent >= CycleConstants.HIGH_CHANCE_MIN_PERCENT -> ChanceLevel.HIGH
            percent >= CycleConstants.MEDIUM_CHANCE_MIN_PERCENT -> ChanceLevel.MEDIUM
            else -> ChanceLevel.LOW
        }
        return PregnancyChance(level, percent, offset)
    }
}
