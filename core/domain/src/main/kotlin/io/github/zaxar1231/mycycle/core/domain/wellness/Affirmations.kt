package io.github.zaxar1231.mycycle.core.domain.wellness

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import kotlin.random.Random

/** Whether today's logged mood asks for support or for an energetic boost. */
enum class MoodTone { SUPPORT, UPLIFT }

fun Set<Mood>.tone(): MoodTone? {
    if (isEmpty()) return null
    val support = setOf(Mood.SENSITIVE, Mood.SAD, Mood.ANXIOUS, Mood.IRRITABLE, Mood.TIRED, Mood.MOOD_SWINGS)
    return if (any { it in support }) MoodTone.SUPPORT else MoodTone.UPLIFT
}

data class AffirmationContext(val phase: CyclePhase?, val tone: MoodTone?, val language: String)

/**
 * Produces an affirmation. The first implementation fills local templates; an LLM-backed one can
 * replace it later behind this interface. The same [seed] gives the same text.
 */
interface AffirmationGenerator {
    suspend fun generate(context: AffirmationContext, seed: Long): String
}

/**
 * Templates with `{slot}` placeholders and word lists for the slots. Templates are grouped by key:
 * a phase name (`MENSTRUAL`…), a tone name (`SUPPORT`, `UPLIFT`) and `ANY`.
 */
class AffirmationTemplates(
    private val templates: Map<String, List<String>>,
    private val words: Map<String, List<String>>,
) {
    /** Picks a template that fits the context and fills every slot. Null if nothing fits. */
    fun fill(context: AffirmationContext, random: Random): String? {
        val keys = listOfNotNull(context.phase?.name, context.tone?.name, ANY)
        val candidates = keys.flatMap { templates[it].orEmpty() }.filter { isFillable(it) }
        if (candidates.isEmpty()) return null
        val template = candidates[random.nextInt(candidates.size)]
        return SLOT.replace(template) { match ->
            val options = words.getValue(match.groupValues[1])
            options[random.nextInt(options.size)]
        }
    }

    /** A template whose slot has no words is skipped instead of showing a raw `{slot}`. */
    private fun isFillable(template: String) =
        SLOT.findAll(template).all { words[it.groupValues[1]].orEmpty().isNotEmpty() }

    companion object {
        const val ANY = "ANY"
        private val SLOT = Regex("""\{(\w+)}""")
    }
}
