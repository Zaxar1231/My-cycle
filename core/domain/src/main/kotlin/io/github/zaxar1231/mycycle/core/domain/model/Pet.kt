package io.github.zaxar1231.mycycle.core.domain.model

/** The mascot chosen once during onboarding. [type] can never change afterwards. */
data class Pet(
    val type: PetType,
    val level: Int = 1,
    val xp: Int = 0,
    val coins: Int = 0,
    val equippedItems: Set<String> = emptySet(),
    val ownedItems: Set<String> = emptySet(),
)

enum class PetType { CAT, FOX, BUNNY, PANDA, PENGUIN }
