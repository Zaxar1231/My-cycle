package io.github.zaxar1231.mycycle.core.domain.model

import java.time.LocalDate

/**
 * One menstrual cycle, from the first day of a period to the day before the next one.
 * [endDate] and [cycleLength] are null while the cycle is still running.
 */
data class Cycle(
    val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val periodLength: Int,
    val cycleLength: Int? = null,
    val isPredicted: Boolean = false,
)
