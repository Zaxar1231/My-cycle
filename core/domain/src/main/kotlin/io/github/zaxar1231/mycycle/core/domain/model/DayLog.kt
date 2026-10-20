package io.github.zaxar1231.mycycle.core.domain.model

import java.time.LocalDate

/** Everything the user logged for one day. Every field except [date] is optional. */
data class DayLog(
    val date: LocalDate,
    val periodEvent: PeriodEvent? = null,
    val flow: Flow? = null,
    val moods: Set<Mood> = emptySet(),
    val symptoms: Set<Symptom> = emptySet(),
    val sexActivity: SexActivity? = null,
    val temperatureCelsius: Double? = null,
    val weightKg: Double? = null,
    val medicationIds: Set<Long> = emptySet(),
    val note: String? = null,
)

enum class PeriodEvent { STARTED, ENDED }

enum class Flow { SPOTTING, LIGHT, MEDIUM, HEAVY }

enum class Mood { CALM, HAPPY, ENERGETIC, SENSITIVE, SAD, ANXIOUS, IRRITABLE, TIRED, MOOD_SWINGS }

enum class Symptom {
    CRAMPS,
    HEADACHE,
    BLOATING,
    ACNE,
    TENDER_BREASTS,
    FATIGUE,
    SWEET_CRAVINGS,
    BACKACHE,
    NAUSEA,
    INSOMNIA,
}

enum class SexActivity { NONE, PROTECTED, UNPROTECTED }
