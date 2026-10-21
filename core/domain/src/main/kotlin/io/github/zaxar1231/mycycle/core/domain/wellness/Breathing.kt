package io.github.zaxar1231.mycycle.core.domain.wellness

enum class BreathPhase { INHALE, HOLD_IN, EXHALE, HOLD_OUT }

/** One step of a breathing pattern. */
data class BreathStep(val phase: BreathPhase, val seconds: Int)

/**
 * Breathing patterns. Steps of zero seconds are left out, so every pattern is a list of real steps.
 * These are relaxation exercises, not treatment.
 */
enum class BreathingTechnique(val steps: List<BreathStep>) {
    /** 4-7-8: a long exhale that many people find calming before sleep. */
    RELAX_478(steps(inhale = 4, holdIn = 7, exhale = 8)),

    /** Box breathing: four equal sides. */
    BOX(steps(inhale = 4, holdIn = 4, exhale = 4, holdOut = 4)),

    /** Slow breathing, about six breaths a minute with a longer exhale; some people use it for cramps. */
    SLOW(steps(inhale = 4, exhale = 6)),
    ;

    val cycleSeconds: Int get() = steps.sumOf { it.seconds }
}

private fun steps(inhale: Int, holdIn: Int = 0, exhale: Int, holdOut: Int = 0): List<BreathStep> =
    listOf(
        BreathStep(BreathPhase.INHALE, inhale),
        BreathStep(BreathPhase.HOLD_IN, holdIn),
        BreathStep(BreathPhase.EXHALE, exhale),
        BreathStep(BreathPhase.HOLD_OUT, holdOut),
    ).filter { it.seconds > 0 }

/** Where a session is at a moment in time. */
data class BreathState(
    val phase: BreathPhase,
    /** 0 at the start of the step, 1 at its end. */
    val progress: Float,
    /** Whole seconds left in the step, counting down to 1. */
    val secondsLeft: Int,
    /** 0-based index of the breath cycle. */
    val cycle: Int,
    val finished: Boolean,
)

object BreathingPlan {
    /** Session lengths offered to the user, in minutes. */
    val SESSION_MINUTES = listOf(1, 3, 5)
    private const val MILLIS_PER_SECOND = 1_000L

    /** State [elapsedMillis] after the start of a session of [totalMillis] with [technique]. */
    fun stateAt(technique: BreathingTechnique, elapsedMillis: Long, totalMillis: Long): BreathState {
        val elapsed = elapsedMillis.coerceAtLeast(0)
        val cycleMillis = technique.cycleSeconds * MILLIS_PER_SECOND
        if (elapsed >= totalMillis) {
            val last = technique.steps.last()
            return BreathState(last.phase, 1f, 0, ((totalMillis - 1).coerceAtLeast(0) / cycleMillis).toInt(), finished = true)
        }
        var inCycle = elapsed % cycleMillis
        for (step in technique.steps) {
            val stepMillis = step.seconds * MILLIS_PER_SECOND
            if (inCycle < stepMillis) {
                val left = stepMillis - inCycle
                return BreathState(
                    phase = step.phase,
                    progress = inCycle.toFloat() / stepMillis,
                    secondsLeft = ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt(),
                    cycle = (elapsed / cycleMillis).toInt(),
                    finished = false,
                )
            }
            inCycle -= stepMillis
        }
        error("unreachable: a cycle is the sum of its steps")
    }
}
