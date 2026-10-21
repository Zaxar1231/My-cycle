package io.github.zaxar1231.mycycle.core.domain.pet

import io.github.zaxar1231.mycycle.core.domain.model.Pet

/** Where an item sits; one item per slot is worn at a time. */
enum class PetSlot { HAIR, HEAD, FACE, NECK, BODY }

/** Something to buy and wear. [id] is stored, so it never changes once released. */
data class PetItem(val id: String, val slot: PetSlot, val price: Int, val minLevel: Int)

object PetItems {
    val ALL: List<PetItem> = listOf(
        PetItem("tuft", PetSlot.HAIR, price = 10, minLevel = 1),
        PetItem("bangs", PetSlot.HAIR, price = 20, minLevel = 2),
        PetItem("bow", PetSlot.HEAD, price = 10, minLevel = 1),
        PetItem("flower_crown", PetSlot.HEAD, price = 25, minLevel = 2),
        PetItem("beret", PetSlot.HEAD, price = 30, minLevel = 3),
        PetItem("party_hat", PetSlot.HEAD, price = 40, minLevel = 4),
        PetItem("crown", PetSlot.HEAD, price = 80, minLevel = 6),
        PetItem("glasses", PetSlot.FACE, price = 20, minLevel = 1),
        PetItem("heart_glasses", PetSlot.FACE, price = 35, minLevel = 3),
        PetItem("scarf", PetSlot.NECK, price = 15, minLevel = 1),
        PetItem("bowtie", PetSlot.NECK, price = 20, minLevel = 2),
        PetItem("bell", PetSlot.NECK, price = 25, minLevel = 2),
        PetItem("sweater", PetSlot.BODY, price = 40, minLevel = 3),
        PetItem("cape", PetSlot.BODY, price = 60, minLevel = 5),
    )

    fun byId(id: String): PetItem? = ALL.firstOrNull { it.id == id }
}

enum class PurchaseResult { BOUGHT, NOT_ENOUGH_COINS, LEVEL_TOO_LOW, ALREADY_OWNED, FAILED }

object PetShop {
    fun check(pet: Pet, item: PetItem): PurchaseResult = when {
        item.id in pet.ownedItems -> PurchaseResult.ALREADY_OWNED
        pet.level < item.minLevel -> PurchaseResult.LEVEL_TOO_LOW
        pet.coins < item.price -> PurchaseResult.NOT_ENOUGH_COINS
        else -> PurchaseResult.BOUGHT
    }

    /** The pet after buying [item], which is put on right away; unchanged when it cannot be bought. */
    fun buy(pet: Pet, item: PetItem): Pet {
        if (check(pet, item) != PurchaseResult.BOUGHT) return pet
        return wear(pet.copy(coins = pet.coins - item.price, ownedItems = pet.ownedItems + item.id), item)
    }

    /** Puts on an owned [item], taking off whatever was in its slot. */
    fun wear(pet: Pet, item: PetItem): Pet {
        if (item.id !in pet.ownedItems) return pet
        val others = pet.equippedItems.filterNot { PetItems.byId(it)?.slot == item.slot }.toSet()
        return pet.copy(equippedItems = others + item.id)
    }

    fun takeOff(pet: Pet, item: PetItem): Pet = pet.copy(equippedItems = pet.equippedItems - item.id)

    /** Worn items that still exist, by slot. */
    fun worn(pet: Pet): Map<PetSlot, PetItem> =
        pet.equippedItems.mapNotNull(PetItems::byId).associateBy { it.slot }
}
