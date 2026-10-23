package io.github.zaxar1231.mycycle.core.domain.insights

import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import io.github.zaxar1231.mycycle.core.domain.usecase.isEmpty

/** What the last few days say at a glance: how often the user logged, and what came up most. */
data class WeeklyReview(
    /** Days with something logged. */
    val loggedDays: Int,
    /** Most frequent symptom; null when none was logged. */
    val topSymptom: Symptom?,
    /** Most frequent mood; null when none was logged. */
    val topMood: Mood?,
)

/** Counts the given logs. Ties go to the entry that appears first in the logs. */
fun weeklyReview(logs: List<DayLog>): WeeklyReview {
    val filled = logs.filterNot { it.isEmpty() }
    return WeeklyReview(
        loggedDays = filled.size,
        topSymptom = filled.flatMap { it.symptoms }.mostCommon(),
        topMood = filled.flatMap { it.moods }.mostCommon(),
    )
}

private fun <T> List<T>.mostCommon(): T? =
    groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
