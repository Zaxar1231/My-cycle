package io.github.zaxar1231.mycycle.core.ui.pet

import androidx.annotation.StringRes
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.pet.PetItem
import io.github.zaxar1231.mycycle.core.ui.R

@StringRes
fun PetType.nameRes(): Int = when (this) {
    PetType.CAT -> R.string.pet_name_cat
    PetType.FOX -> R.string.pet_name_fox
    PetType.BUNNY -> R.string.pet_name_bunny
    PetType.PANDA -> R.string.pet_name_panda
    PetType.PENGUIN -> R.string.pet_name_penguin
}

@StringRes
fun PetItem.nameRes(): Int = when (id) {
    "tuft" -> R.string.pet_item_tuft
    "bangs" -> R.string.pet_item_bangs
    "bow" -> R.string.pet_item_bow
    "flower_crown" -> R.string.pet_item_flower_crown
    "beret" -> R.string.pet_item_beret
    "party_hat" -> R.string.pet_item_party_hat
    "crown" -> R.string.pet_item_crown
    "glasses" -> R.string.pet_item_glasses
    "heart_glasses" -> R.string.pet_item_heart_glasses
    "scarf" -> R.string.pet_item_scarf
    "bowtie" -> R.string.pet_item_bowtie
    "bell" -> R.string.pet_item_bell
    "sweater" -> R.string.pet_item_sweater
    "cape" -> R.string.pet_item_cape
    else -> R.string.pet_item_unknown
}
