package io.github.zaxar1231.mycycle.core.domain.pet

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PetRulesTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val pet = Pet(PetType.FOX)

    @Test
    fun `the pet follows the night, the period and the day's mood`() {
        assertEquals(PetMood.SLEEPY, PetRules.mood(CyclePhase.MENSTRUAL, emptySet(), hour = 2))
        assertEquals(PetMood.COZY, PetRules.mood(CyclePhase.MENSTRUAL, setOf(Mood.HAPPY), hour = 12))
        assertEquals(PetMood.SAD, PetRules.mood(CyclePhase.FOLLICULAR, setOf(Mood.ANXIOUS, Mood.HAPPY), hour = 12))
        assertEquals(PetMood.SLEEPY, PetRules.mood(CyclePhase.LUTEAL, setOf(Mood.TIRED), hour = 12))
        assertEquals(PetMood.HAPPY, PetRules.mood(CyclePhase.LUTEAL, setOf(Mood.ENERGETIC), hour = 12))
        assertEquals(PetMood.PLAYFUL, PetRules.mood(CyclePhase.FOLLICULAR, emptySet(), hour = 12))
        assertEquals(PetMood.HAPPY, PetRules.mood(CyclePhase.OVULATORY, emptySet(), hour = 12))
        assertEquals(PetMood.CALM, PetRules.mood(null, emptySet(), hour = 12))
    }

    @Test
    fun `a day log pays once a day`() {
        val first = PetRules.rewardLog(pet, today)!!
        assertEquals(PetConstants.LOG_COINS, first.pet.coins)
        assertEquals(1, first.pet.logStreak)
        assertNull(PetRules.rewardLog(first.pet, today))
    }

    @Test
    fun `a week in a row brings a bonus and a broken streak starts over`() {
        var current = pet
        repeat(PetConstants.STREAK_BONUS_DAYS) { day -> current = PetRules.rewardLog(current, today.plusDays(day.toLong()))!!.pet }
        assertEquals(PetConstants.STREAK_BONUS_DAYS, current.logStreak)
        assertEquals(PetConstants.LOG_COINS * PetConstants.STREAK_BONUS_DAYS + PetConstants.STREAK_BONUS_COINS, current.coins)
        val afterGap = PetRules.rewardLog(current, today.plusDays(PetConstants.STREAK_BONUS_DAYS + 1L))!!
        assertEquals(1, afterGap.pet.logStreak)
        assertFalse(afterGap.streakBonus)
    }

    @Test
    fun `the shown streak lapses after a missed day`() {
        val logged = PetRules.rewardLog(pet, today)!!.pet
        assertEquals(1, PetRules.currentStreak(logged, today.plusDays(1)))
        assertEquals(0, PetRules.currentStreak(logged, today.plusDays(2)))
    }

    @Test
    fun `xp raises the level`() {
        val nearly = pet.copy(xp = PetConstants.XP_PER_LEVEL - 1)
        val reward = PetRules.rewardBreathing(nearly, today)!!
        assertTrue(reward.levelUp)
        assertEquals(2, reward.pet.level)
        assertNull(PetRules.rewardBreathing(reward.pet, today))
    }

    @Test
    fun `items are bought with coins and level and one is worn per slot`() {
        val bow = PetItems.byId("bow")!!
        val crown = PetItems.byId("crown")!!
        val beret = PetItems.byId("beret")!!
        assertEquals(PurchaseResult.NOT_ENOUGH_COINS, PetShop.check(pet, bow))
        assertEquals(PurchaseResult.LEVEL_TOO_LOW, PetShop.check(pet.copy(coins = 500), crown))
        val rich = pet.copy(coins = 100, level = 3)
        val withBow = PetShop.buy(rich, bow)
        assertEquals(90, withBow.coins)
        assertEquals(setOf("bow"), withBow.equippedItems)
        assertEquals(PurchaseResult.ALREADY_OWNED, PetShop.check(withBow, bow))
        val withBeret = PetShop.buy(withBow, beret)
        assertEquals(setOf("beret"), withBeret.equippedItems)
        assertEquals(setOf("bow", "beret"), withBeret.ownedItems)
        assertEquals(setOf("bow"), PetShop.wear(withBeret, bow).equippedItems)
    }

    @Test
    fun `item ids are unique`() {
        assertEquals(PetItems.ALL.size, PetItems.ALL.map { it.id }.toSet().size)
    }
}
