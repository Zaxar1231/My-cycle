package io.github.zaxar1231.mycycle.core.domain.pet

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import java.time.LocalDate

/** Every number of the pet's economy lives here. */
object PetConstants {
    const val XP_PER_LEVEL = 50
    const val LOG_COINS = 5
    const val LOG_XP = 10
    const val BREATHING_COINS = 5
    const val BREATHING_XP = 10

    /** Every this many days in a row with a log bring [STREAK_BONUS_COINS] more. */
    const val STREAK_BONUS_DAYS = 7
    const val STREAK_BONUS_COINS = 15

    /** From this hour until [WAKE_HOUR] the pet sleeps. */
    const val SLEEP_HOUR = 23
    const val WAKE_HOUR = 6
}

/** How the pet looks right now; each mood has its own drawing and animation. */
enum class PetMood {
    SLEEPY,
    HAPPY,
    PLAYFUL,
    CALM,
    SAD,

    /** During the period: wrapped up, hugging a hot water bottle. */
    COZY,
}

/** What happened when a reward was claimed, for a short note on screen. */
data class PetReward(val pet: Pet, val coins: Int, val xp: Int, val streakBonus: Boolean, val levelUp: Boolean)

object PetRules {
    fun level(xp: Int): Int = 1 + xp / PetConstants.XP_PER_LEVEL

    /** XP gathered inside the current level and needed for the next one. */
    fun progress(xp: Int): Pair<Int, Int> = xp % PetConstants.XP_PER_LEVEL to PetConstants.XP_PER_LEVEL

    /**
     * The pet follows the cycle and today's mood: it sleeps at night, hugs a hot water bottle during
     * the period, is sad along with a sad day, and otherwise shows the phase's usual energy.
     */
    fun mood(phase: CyclePhase?, todayMoods: Set<Mood>, hour: Int): PetMood = when {
        hour >= PetConstants.SLEEP_HOUR || hour < PetConstants.WAKE_HOUR -> PetMood.SLEEPY
        phase == CyclePhase.MENSTRUAL -> PetMood.COZY
        todayMoods.any { it in SAD_MOODS } -> PetMood.SAD
        Mood.TIRED in todayMoods -> PetMood.SLEEPY
        todayMoods.any { it in GLAD_MOODS } -> PetMood.HAPPY
        phase == CyclePhase.FOLLICULAR -> PetMood.PLAYFUL
        phase == CyclePhase.OVULATORY -> PetMood.HAPPY
        else -> PetMood.CALM
    }

    /** A day log saved for [today]; null when today already paid. */
    fun rewardLog(pet: Pet, today: LocalDate): PetReward? {
        if (pet.lastLogReward == today) return null
        val streak = if (pet.lastLogReward == today.minusDays(1)) pet.logStreak + 1 else 1
        val bonus = streak % PetConstants.STREAK_BONUS_DAYS == 0
        val coins = PetConstants.LOG_COINS + if (bonus) PetConstants.STREAK_BONUS_COINS else 0
        return grant(pet.copy(lastLogReward = today, logStreak = streak), coins, PetConstants.LOG_XP, bonus)
    }

    /** A breathing session finished; pays once a day. */
    fun rewardBreathing(pet: Pet, today: LocalDate): PetReward? {
        if (pet.lastBreathingReward == today) return null
        return grant(pet.copy(lastBreathingReward = today), PetConstants.BREATHING_COINS, PetConstants.BREATHING_XP, streakBonus = false)
    }

    /** The streak shown today: it lapses once a whole day passed without a log. */
    fun currentStreak(pet: Pet, today: LocalDate): Int {
        val last = pet.lastLogReward ?: return 0
        return if (last == today || last == today.minusDays(1)) pet.logStreak else 0
    }

    private fun grant(pet: Pet, coins: Int, xp: Int, streakBonus: Boolean): PetReward {
        val newXp = pet.xp + xp
        val newLevel = level(newXp)
        return PetReward(pet.copy(coins = pet.coins + coins, xp = newXp, level = newLevel), coins, xp, streakBonus, newLevel > pet.level)
    }

    private val SAD_MOODS = setOf(Mood.SAD, Mood.ANXIOUS, Mood.IRRITABLE, Mood.SENSITIVE)
    private val GLAD_MOODS = setOf(Mood.HAPPY, Mood.ENERGETIC, Mood.CALM)
}
