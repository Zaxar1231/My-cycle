package io.github.zaxar1231.mycycle.core.domain.pet

import io.github.zaxar1231.mycycle.core.domain.model.PetType
import java.time.LocalDate

/** What the partner's phone does for the shared pet; sent encrypted to the owner's phone. */
sealed interface PetCare {
    val date: LocalDate

    data class Feed(override val date: LocalDate) : PetCare

    data class Gift(val itemId: String, override val date: LocalDate) : PetCare
}

/** The owner's pet as the partner sees it. */
data class SharedPet(
    val type: PetType,
    val level: Int,
    val worn: Set<String>,
    val ownedItems: Set<String>,
    val mood: PetMood,
)
