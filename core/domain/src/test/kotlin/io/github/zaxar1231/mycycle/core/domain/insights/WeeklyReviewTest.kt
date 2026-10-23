package io.github.zaxar1231.mycycle.core.domain.insights

import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WeeklyReviewTest {
    private val day = LocalDate.of(2026, 10, 20)

    @Test
    fun `empty days are not counted as logged`() {
        val logs = listOf(DayLog(day), DayLog(day.plusDays(1), moods = setOf(Mood.CALM)))
        val review = weeklyReview(logs)
        assertEquals(1, review.loggedDays)
        assertEquals(Mood.CALM, review.topMood)
    }

    @Test
    fun `most frequent symptom wins`() {
        val logs = listOf(
            DayLog(day, symptoms = setOf(Symptom.CRAMPS)),
            DayLog(day.plusDays(1), symptoms = setOf(Symptom.ACNE)),
            DayLog(day.plusDays(2), symptoms = setOf(Symptom.ACNE)),
        )
        assertEquals(Symptom.ACNE, weeklyReview(logs).topSymptom)
    }

    @Test
    fun `on a tie the symptom seen first wins`() {
        val logs = listOf(
            DayLog(day, symptoms = setOf(Symptom.CRAMPS)),
            DayLog(day.plusDays(1), symptoms = setOf(Symptom.ACNE)),
        )
        assertEquals(Symptom.CRAMPS, weeklyReview(logs).topSymptom)
    }

    @Test
    fun `nothing logged gives no top values`() {
        val review = weeklyReview(listOf(DayLog(day)))
        assertEquals(0, review.loggedDays)
        assertNull(review.topSymptom)
        assertNull(review.topMood)
    }
}
