package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.pet.PetCare
import io.github.zaxar1231.mycycle.core.domain.pet.PetItem
import io.github.zaxar1231.mycycle.core.domain.pet.PetItems
import io.github.zaxar1231.mycycle.core.domain.pet.PetReward
import io.github.zaxar1231.mycycle.core.domain.pet.PetRules
import io.github.zaxar1231.mycycle.core.domain.pet.PetShop
import io.github.zaxar1231.mycycle.core.domain.pet.PurchaseResult
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import java.time.LocalDate
import javax.inject.Inject

/** Coins and XP for looking after yourself: a day log and a breathing session, each once a day. */
class RewardPetUseCase @Inject constructor(
    private val pets: PetRepository,
    private val transactions: TransactionRunner,
    private val dates: DateProvider,
) {
    /** Only a log for today counts, so filling in old days does not farm coins. */
    suspend fun dayLogged(date: LocalDate): PetReward? {
        val today = dates.today()
        if (date != today) return null
        return claim { PetRules.rewardLog(it, today) }
    }

    suspend fun breathingFinished(): PetReward? {
        val today = dates.today()
        return claim { PetRules.rewardBreathing(it, today) }
    }

    private suspend fun claim(rule: (Pet) -> PetReward?): PetReward? =
        transactions.inTransaction {
            val pet = pets.pet() ?: return@inTransaction null
            rule(pet)?.also { pets.update(it.pet) }
        }
}

/** Applies what the partner did for the shared pet; limits are checked here, on the owner's phone. */
class ApplyPetCareUseCase @Inject constructor(
    private val pets: PetRepository,
    private val transactions: TransactionRunner,
) {
    /** How many of [cares] changed the pet. */
    suspend operator fun invoke(cares: List<PetCare>): Int = transactions.inTransaction {
        var pet = pets.pet() ?: return@inTransaction 0
        var applied = 0
        cares.sortedBy { it.date }.forEach { care ->
            val next = when (care) {
                is PetCare.Feed -> PetRules.partnerFeed(pet, care.date)?.pet
                is PetCare.Gift -> PetItems.byId(care.itemId)?.let { PetRules.partnerGift(pet, it, care.date) }
            }
            if (next != null) {
                pet = next
                applied++
            }
        }
        if (applied > 0) pets.update(pet)
        applied
    }
}

/** Buying and wearing clothes and accessories. */
class PetWardrobeUseCase @Inject constructor(
    private val pets: PetRepository,
    private val transactions: TransactionRunner,
) {
    suspend fun buy(item: PetItem): PurchaseResult = transactions.inTransaction {
        val pet = pets.pet() ?: return@inTransaction PurchaseResult.FAILED
        val result = PetShop.check(pet, item)
        if (result == PurchaseResult.BOUGHT) pets.update(PetShop.buy(pet, item))
        result
    }

    suspend fun wear(item: PetItem, on: Boolean) = transactions.inTransaction {
        val pet = pets.pet() ?: return@inTransaction
        pets.update(if (on) PetShop.wear(pet, item) else PetShop.takeOff(pet, item))
    }
}
