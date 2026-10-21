package io.github.zaxar1231.mycycle.core.ui.component

import androidx.annotation.StringRes
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.ui.R

@StringRes
fun CyclePhase.labelRes(): Int = when (this) {
    CyclePhase.MENSTRUAL -> R.string.phase_menstrual
    CyclePhase.FOLLICULAR -> R.string.phase_follicular
    CyclePhase.OVULATORY -> R.string.phase_ovulatory
    CyclePhase.LUTEAL -> R.string.phase_luteal
}
