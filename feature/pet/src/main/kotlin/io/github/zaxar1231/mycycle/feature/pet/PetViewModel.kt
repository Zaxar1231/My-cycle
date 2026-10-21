package io.github.zaxar1231.mycycle.feature.pet

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.pet.PetItem
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.pet.PetRules
import io.github.zaxar1231.mycycle.core.domain.pet.PetShop
import io.github.zaxar1231.mycycle.core.domain.pet.PurchaseResult
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import io.github.zaxar1231.mycycle.core.domain.usecase.PetWardrobeUseCase
import io.github.zaxar1231.mycycle.core.ui.pet.PetLook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class PetUiState(
    val loaded: Boolean = false,
    val pet: Pet? = null,
    val mood: PetMood = PetMood.CALM,
    val streak: Int = 0,
    /** An item tried on from the shop or wardrobe, drawn on the pet but not saved. */
    val tryOn: PetItem? = null,
    val pettedAt: Long = 0L,
    val today: LocalDate? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PetViewModel @Inject constructor(
    pets: PetRepository,
    observePrediction: ObserveCyclePredictionUseCase,
    dayLogs: DayLogRepository,
    private val dates: DateProvider,
    private val wardrobe: PetWardrobeUseCase,
) : ViewModel() {
    private val tryOn = MutableStateFlow<PetItem?>(null)
    private val pettedAt = MutableStateFlow(0L)
    private val _purchase = MutableStateFlow<PurchaseResult?>(null)

    /** The result of the last purchase, shown once. */
    val purchase: StateFlow<PurchaseResult?> = _purchase.asStateFlow()

    private val moment = clock().flatMapLatest { (today, hour) ->
        combine(observePrediction(today), dayLogs.observe(today)) { prediction, log ->
            today to PetRules.mood(prediction.today?.phase, log?.moods.orEmpty(), hour)
        }
    }

    val state: StateFlow<PetUiState> = combine(pets.observePet(), moment, tryOn, pettedAt) { pet, (today, mood), trying, petted ->
        PetUiState(
            loaded = true,
            pet = pet,
            mood = mood,
            streak = pet?.let { PetRules.currentStreak(it, today) } ?: 0,
            tryOn = trying,
            pettedAt = petted,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PetUiState())

    fun stroke() {
        pettedAt.value = SystemClock.uptimeMillis()
    }

    fun tryOn(item: PetItem?) {
        tryOn.value = item
    }

    fun buy(item: PetItem) {
        viewModelScope.launch {
            _purchase.value = guarded { wardrobe.buy(item) } ?: PurchaseResult.FAILED
            tryOn.value = null
        }
    }

    fun wear(item: PetItem, on: Boolean) {
        viewModelScope.launch {
            guarded { wardrobe.wear(item, on) }
            tryOn.value = null
        }
    }

    fun purchaseShown() {
        _purchase.value = null
    }

    /** What the pet wears, with a tried-on item in place of its slot. */
    fun look(state: PetUiState): PetLook? {
        val pet = state.pet ?: return null
        val worn = PetShop.worn(pet).toMutableMap()
        state.tryOn?.let { worn[it.slot] = it }
        return PetLook(pet.type, state.mood, worn, state.pettedAt)
    }

    /** The date and hour, again each minute: the pet falls asleep at night and wakes in the morning. */
    private fun clock(): Flow<Pair<LocalDate, Int>> = flow {
        while (true) {
            emit(dates.today() to LocalTime.now().hour)
            delay(MINUTE_MILLIS)
        }
    }.distinctUntilChanged()

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MINUTE_MILLIS = 60_000L
    }
}
