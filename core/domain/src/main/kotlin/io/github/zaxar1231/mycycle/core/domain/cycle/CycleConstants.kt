package io.github.zaxar1231.mycycle.core.domain.cycle

/** Every number the cycle logic relies on lives here, so the predictor and the UI never disagree. */
object CycleConstants {
    const val DEFAULT_CYCLE_LENGTH = 28
    const val DEFAULT_PERIOD_LENGTH = 5

    /** Cycles outside this range are shown in history but left out of averages. */
    const val MIN_REGULAR_CYCLE_LENGTH = 21
    const val MAX_REGULAR_CYCLE_LENGTH = 45

    const val MIN_PERIOD_LENGTH = 1
    const val MAX_PERIOD_LENGTH = 14

    /** Ovulation is estimated this many days before the next period. */
    const val LUTEAL_PHASE_LENGTH = 14

    /** The fertile window is these days before ovulation plus the ovulation day itself. */
    const val FERTILE_DAYS_BEFORE_OVULATION = 5

    /** How many recent cycles feed the weighted average. */
    const val PREDICTION_CYCLE_COUNT = 6

    /** Below this many completed cycles the onboarding values are used instead of history. */
    const val MIN_CYCLES_FOR_HISTORY = 2

    /** Days around ovulation (on each side) that count as the ovulatory phase. */
    const val OVULATORY_PHASE_HALF_WIDTH = 1

    /** How many future periods are drawn in the calendar. */
    const val PREDICTED_FUTURE_CYCLES = 3

    /** Basal body temperature: baseline window, length of the shift and the rise that counts. */
    const val BBT_BASELINE_DAYS = 6
    const val BBT_SHIFT_DAYS = 3
    const val BBT_MIN_RISE_CELSIUS = 0.2
    const val BBT_MAX_RISE_CELSIUS = 0.5

    /** Confidence: enough stable cycles give HIGH, a moderate spread gives MEDIUM. */
    const val HIGH_CONFIDENCE_MIN_CYCLES = 4
    const val HIGH_CONFIDENCE_MAX_DEVIATION_DAYS = 2.0
    const val MEDIUM_CONFIDENCE_MAX_DEVIATION_DAYS = 4.0

    /** Accepted ranges for manual input in the day log. */
    const val MIN_TEMPERATURE_CELSIUS = 34.0
    const val MAX_TEMPERATURE_CELSIUS = 42.0
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 300.0

    /** Statistics: completed cycles whose lengths differ by at most this many days are usually called regular. */
    const val REGULARITY_MAX_SPREAD_DAYS = 7
    const val MIN_CYCLES_FOR_REGULARITY = 3

    /**
     * Statistics: a symptom forms a pattern when it was logged on at least [MIN_PATTERN_OCCURRENCES] days
     * and at least [PATTERN_MIN_SHARE] of them fall on the same [PATTERN_WINDOW_DAYS] cycle days.
     */
    const val MIN_PATTERN_OCCURRENCES = 3
    const val PATTERN_WINDOW_DAYS = 2
    const val PATTERN_MIN_SHARE = 0.5

    /**
     * Chance of conception from intercourse on a single day, by day relative to ovulation, in percent.
     * Rounded from Wilcox et al., NEJM 1995. Days outside the window get [CONCEPTION_CHANCE_OUTSIDE_WINDOW].
     */
    val CONCEPTION_CHANCE_BY_DAY: Map<Int, Int> = mapOf(-5 to 10, -4 to 16, -3 to 14, -2 to 27, -1 to 31, 0 to 33)
    const val CONCEPTION_CHANCE_OUTSIDE_WINDOW = 1
    const val HIGH_CHANCE_MIN_PERCENT = 20
    const val MEDIUM_CHANCE_MIN_PERCENT = 5
}
