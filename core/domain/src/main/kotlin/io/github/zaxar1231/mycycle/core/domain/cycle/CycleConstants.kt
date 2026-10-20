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
}
