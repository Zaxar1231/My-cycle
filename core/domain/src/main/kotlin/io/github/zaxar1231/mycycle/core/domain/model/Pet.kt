package io.github.zaxar1231.mycycle.core.domain.model

import java.time.LocalDate

/** The mascot chosen once during onboarding. [type] can never change afterwards. */
data class Pet(
    val type: PetType,
    val level: Int = 1,
    val xp: Int = 0,
    val coins: Int = 0,
    val equippedItems: Set<String> = emptySet(),
    val ownedItems: Set<String> = emptySet(),
    /** The last day a day log earned a reward, so each day pays once. */
    val lastLogReward: LocalDate? = null,
    /** Days in a row with a log, counted on rewards. */
    val logStreak: Int = 0,
    val lastBreathingReward: LocalDate? = null,
)

enum class PetType { CAT, FOX, BUNNY, PANDA, PENGUIN }
