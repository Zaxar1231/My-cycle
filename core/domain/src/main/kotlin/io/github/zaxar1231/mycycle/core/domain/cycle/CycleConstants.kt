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
}
