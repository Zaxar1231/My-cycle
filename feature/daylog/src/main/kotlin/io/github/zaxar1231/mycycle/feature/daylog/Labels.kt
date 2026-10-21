package io.github.zaxar1231.mycycle.feature.daylog

import io.github.zaxar1231.mycycle.core.domain.model.Flow
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.model.SexActivity
import io.github.zaxar1231.mycycle.core.domain.model.Symptom

fun PeriodEvent.labelRes() = when (this) {
    PeriodEvent.STARTED -> R.string.period_started
    PeriodEvent.ENDED -> R.string.period_ended
}

fun Flow.labelRes() = when (this) {
    Flow.SPOTTING -> R.string.flow_spotting
    Flow.LIGHT -> R.string.flow_light
    Flow.MEDIUM -> R.string.flow_medium
    Flow.HEAVY -> R.string.flow_heavy
}

fun Mood.labelRes() = when (this) {
    Mood.CALM -> R.string.mood_calm
    Mood.HAPPY -> R.string.mood_happy
    Mood.ENERGETIC -> R.string.mood_energetic
    Mood.SENSITIVE -> R.string.mood_sensitive
    Mood.SAD -> R.string.mood_sad
    Mood.ANXIOUS -> R.string.mood_anxious
    Mood.IRRITABLE -> R.string.mood_irritable
    Mood.TIRED -> R.string.mood_tired
    Mood.MOOD_SWINGS -> R.string.mood_swings
}

fun Mood.emoji() = when (this) {
    Mood.CALM -> "😌"
    Mood.HAPPY -> "😊"
    Mood.ENERGETIC -> "⚡"
    Mood.SENSITIVE -> "🥺"
    Mood.SAD -> "😢"
    Mood.ANXIOUS -> "😟"
    Mood.IRRITABLE -> "😠"
    Mood.TIRED -> "😴"
    Mood.MOOD_SWINGS -> "🎭"
}

fun Symptom.labelRes() = when (this) {
    Symptom.CRAMPS -> R.string.symptom_cramps
    Symptom.HEADACHE -> R.string.symptom_headache
    Symptom.BLOATING -> R.string.symptom_bloating
    Symptom.ACNE -> R.string.symptom_acne
    Symptom.TENDER_BREASTS -> R.string.symptom_tender_breasts
    Symptom.FATIGUE -> R.string.symptom_fatigue
    Symptom.SWEET_CRAVINGS -> R.string.symptom_sweet_cravings
    Symptom.BACKACHE -> R.string.symptom_backache
    Symptom.NAUSEA -> R.string.symptom_nausea
    Symptom.INSOMNIA -> R.string.symptom_insomnia
}

fun SexActivity.labelRes() = when (this) {
    SexActivity.NONE -> R.string.sex_none
    SexActivity.PROTECTED -> R.string.sex_protected
    SexActivity.UNPROTECTED -> R.string.sex_unprotected
}
