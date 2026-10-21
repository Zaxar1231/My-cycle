package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.pet.PetCare
import io.github.zaxar1231.mycycle.core.domain.pet.PetConstants
import io.github.zaxar1231.mycycle.core.domain.pet.PetItems
import io.github.zaxar1231.mycycle.core.domain.pet.PurchaseResult
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PetUseCasesTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val pets = FakePets(Pet(PetType.CAT))
    private val transactions = object : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }
    private val reward = RewardPetUseCase(pets, transactions) { today }
    private val wardrobe = PetWardrobeUseCase(pets, transactions)

    @Test
    fun `only a log of today pays, once`() = runTest {
        assertNull(reward.dayLogged(today.minusDays(1)))
        assertNotNull(reward.dayLogged(today))
        assertNull(reward.dayLogged(today))
        assertEquals(PetConstants.LOG_COINS, pets.pet()!!.coins)
    }

    @Test
    fun `breathing pays once a day`() = runTest {
        assertNotNull(reward.breathingFinished())
        assertNull(reward.breathingFinished())
        assertEquals(PetConstants.BREATHING_COINS, pets.pet()!!.coins)
    }

    @Test
    fun `buying, taking off and wearing again`() = runTest {
        val bow = PetItems.byId("bow")!!
        assertEquals(PurchaseResult.NOT_ENOUGH_COINS, wardrobe.buy(bow))
        pets.update(pets.pet()!!.copy(coins = bow.price))
        assertEquals(PurchaseResult.BOUGHT, wardrobe.buy(bow))
        assertEquals(0, pets.pet()!!.coins)
        wardrobe.wear(bow, on = false)
        assertEquals(emptySet<String>(), pets.pet()!!.equippedItems)
        wardrobe.wear(bow, on = true)
        assertEquals(setOf("bow"), pets.pet()!!.equippedItems)
    }

    @Test
    fun `an item that is not owned cannot be worn`() = runTest {
        wardrobe.wear(PetItems.byId("crown")!!, on = true)
        assertEquals(emptySet<String>(), pets.pet()!!.equippedItems)
    }

    @Test
    fun `without a pet nothing happens`() = runTest {
        val none = FakePets(null)
        assertNull(RewardPetUseCase(none, transactions) { today }.dayLogged(today))
        assertEquals(PurchaseResult.FAILED, PetWardrobeUseCase(none, transactions).buy(PetItems.ALL.first()))
    }

    @Test
    fun `partner care counts by this phone's date and one of each per batch`() = runTest {
        val apply = ApplyPetCareUseCase(pets, transactions) { today }
        val far = PetCare.Feed(LocalDate.of(2099, 1, 1))
        assertEquals(0, apply(listOf(far)))
        val batch = listOf(PetCare.Feed(today.minusDays(2)), PetCare.Feed(today.minusDays(1)), PetCare.Feed(today), PetCare.Gift("bow", today))
        assertEquals(2, apply(batch))
        val pet = pets.pet()!!
        assertEquals(PetConstants.PARTNER_FEED_COINS, pet.coins)
        assertEquals(today, pet.lastPartnerFeed)
        assertEquals(setOf("bow"), pet.ownedItems)
        assertEquals(0, apply(listOf(PetCare.Feed(today), PetCare.Gift("scarf", today))))
        assertNull(ApplyPetCareUseCase(FakePets(null), transactions) { today }(listOf(PetCare.Feed(today))))
    }

    private class FakePets(initial: Pet?) : PetRepository {
        private val state = MutableStateFlow(initial)
        override fun observePet(): Flow<Pet?> = state
        override suspend fun adopt(pet: Pet) {
            if (state.value == null) state.value = pet
        }
        override suspend fun update(pet: Pet) {
            state.value = pet
        }
    }
}
