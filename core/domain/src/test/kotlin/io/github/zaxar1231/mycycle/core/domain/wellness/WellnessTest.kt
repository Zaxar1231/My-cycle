package io.github.zaxar1231.mycycle.core.domain.wellness

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class WellnessTest {
    @Test
    fun `4-7-8 walks through inhale, hold and exhale`() {
        val technique = BreathingTechnique.RELAX_478
        val total = 60_000L
        assertEquals(19, technique.cycleSeconds)
        assertEquals(BreathPhase.INHALE, BreathingPlan.stateAt(technique, 0, total).phase)
        assertEquals(4, BreathingPlan.stateAt(technique, 0, total).secondsLeft)
        val hold = BreathingPlan.stateAt(technique, 5_500, total)
        assertEquals(BreathPhase.HOLD_IN, hold.phase)
        assertEquals(6, hold.secondsLeft)
        assertEquals(BreathPhase.EXHALE, BreathingPlan.stateAt(technique, 11_000, total).phase)
        val nextCycle = BreathingPlan.stateAt(technique, 19_000, total)
        assertEquals(BreathPhase.INHALE, nextCycle.phase)
        assertEquals(1, nextCycle.cycle)
        assertTrue(BreathingPlan.stateAt(technique, total, total).finished)
    }

    @Test
    fun `slow breathing has no holds and box has four equal steps`() {
        assertEquals(listOf(BreathPhase.INHALE, BreathPhase.EXHALE), BreathingTechnique.SLOW.steps.map { it.phase })
        assertEquals(setOf(4), BreathingTechnique.BOX.steps.map { it.seconds }.toSet())
        assertEquals(0.5f, BreathingPlan.stateAt(BreathingTechnique.BOX, 2_000, 60_000).progress)
        assertFalse(BreathingPlan.stateAt(BreathingTechnique.BOX, 59_999, 60_000).finished)
    }

    @Test
    fun `sessions end on a whole breath cycle`() {
        // 60 s of 19 s cycles → 4 cycles; 60 s of 16 s cycles → 4 cycles; 180 s of 10 s cycles → 18.
        assertEquals(76_000L, BreathingPlan.sessionMillis(BreathingTechnique.RELAX_478, 1))
        assertEquals(64_000L, BreathingPlan.sessionMillis(BreathingTechnique.BOX, 1))
        assertEquals(180_000L, BreathingPlan.sessionMillis(BreathingTechnique.SLOW, 3))
        val total = BreathingPlan.sessionMillis(BreathingTechnique.RELAX_478, 1)
        assertEquals(BreathPhase.EXHALE, BreathingPlan.stateAt(BreathingTechnique.RELAX_478, total - 1, total).phase)
    }

    @Test
    fun `mood tone prefers support when any hard mood is logged`() {
        assertNull(emptySet<Mood>().tone())
        assertEquals(MoodTone.UPLIFT, setOf(Mood.HAPPY, Mood.CALM).tone())
        assertEquals(MoodTone.SUPPORT, setOf(Mood.HAPPY, Mood.TIRED).tone())
    }

    private val templates = AffirmationTemplates(
        templates = mapOf(
            "MENSTRUAL" to listOf("Rest {pace}."),
            "SUPPORT" to listOf("Be {care}."),
            "ANY" to listOf("I deserve {care_noun}.", "Broken {missing}."),
        ),
        words = mapOf("pace" to listOf("slowly"), "care" to listOf("gentle"), "care_noun" to listOf("care")),
    )

    @Test
    fun `templates are filled from matching groups only`() {
        val texts = (0L until 50L).map { templates.fill(AffirmationContext(CyclePhase.MENSTRUAL, null, "en"), Random(it)) }.toSet()
        assertEquals(setOf("Rest slowly.", "I deserve care."), texts)
        val support = (0L until 50L).map { templates.fill(AffirmationContext(null, MoodTone.SUPPORT, "en"), Random(it)) }.toSet()
        assertEquals(setOf("Be gentle.", "I deserve care."), support)
    }

    @Test
    fun `the same seed gives the same text`() {
        val context = AffirmationContext(CyclePhase.LUTEAL, MoodTone.SUPPORT, "en")
        assertEquals(templates.fill(context, Random(42)), templates.fill(context, Random(42)))
        assertNull(AffirmationTemplates(emptyMap(), emptyMap()).fill(context, Random(1)))
    }
}
