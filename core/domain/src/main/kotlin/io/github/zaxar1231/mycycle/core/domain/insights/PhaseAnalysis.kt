package io.github.zaxar1231.mycycle.core.domain.insights

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleSegment

object PhaseAnalysis {
    /** Cycle days (1-based) of each phase in [segment], in phase order. Every day belongs to one phase. */
    fun ranges(segment: CycleSegment): Map<CyclePhase, IntRange> =
        (1..segment.length)
            .groupBy { CyclePredictor.phaseOf(it, segment) }
            .mapValues { (_, days) -> days.first()..days.last() }
            .toSortedMap(compareBy { it.ordinal })
}
