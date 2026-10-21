package io.github.zaxar1231.mycycle.core.domain.model

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase

/** Popular-science texts for a cycle phase, loaded from assets so they can be edited without code. */
data class PhaseContent(
    val phase: CyclePhase,
    val symptoms: List<String>,
    val pros: List<String>,
    val cons: List<String>,
    val tips: List<String>,
    /** What happens in the body during this phase, for the phase analysis screen. */
    val about: String = "",
) {
    /** One tip per day, rotating through the list. */
    fun tipFor(dayOfYear: Int): String? = tips.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(dayOfYear, it.size)] }
}
