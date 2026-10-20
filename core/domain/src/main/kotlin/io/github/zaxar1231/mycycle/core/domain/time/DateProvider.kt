package io.github.zaxar1231.mycycle.core.domain.time

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Source of "today", injected so tests and the predictor can run on a fixed date. */
fun interface DateProvider {
    fun today(): LocalDate
}

class SystemDateProvider @Inject constructor() : DateProvider {
    override fun today(): LocalDate = LocalDate.now(Clock.systemDefaultZone())
}
