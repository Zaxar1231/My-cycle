package io.github.zaxar1231.mycycle.feature.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.Affirmation
import io.github.zaxar1231.mycycle.core.domain.repository.AffirmationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import io.github.zaxar1231.mycycle.core.domain.wellness.AffirmationContext
import io.github.zaxar1231.mycycle.core.domain.wellness.AffirmationGenerator
import io.github.zaxar1231.mycycle.core.domain.wellness.tone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Affirmation of the day: the same text all day for the same phase and mood, a new one on "more".
 * Also holds the favourites list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AffirmationViewModel @Inject constructor(
    observePrediction: ObserveCyclePredictionUseCase,
    dayLogs: DayLogRepository,
    private val generator: AffirmationGenerator,
    private val favorites: AffirmationRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {
    private val today = MutableStateFlow(dateProvider.today())
    private val language = MutableStateFlow(Locale.getDefault().language)
    private val extra = MutableStateFlow(0)

    private val context = today.flatMapLatest { day ->
        combine(observePrediction(day), dayLogs.observe(day), language) { prediction, log, lang ->
            AffirmationContext(phase = prediction.today?.phase, tone = log?.moods.orEmpty().tone(), language = lang)
        }
    }.distinctUntilChanged()

    /** Null while loading or if the templates cannot be read. */
    val text: StateFlow<String?> = combine(context, today, extra) { c, day, n -> Triple(c, day, n) }
        .mapLatest { (c, day, n) ->
            try {
                generator.generate(c, seed = day.toEpochDay() * SEED_STEP + n)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val isFavorite: StateFlow<Boolean> = text
        .flatMapLatest { t -> if (t == null) flowOf(false) else favorites.observeIsFavorite(t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val favoriteList: StateFlow<List<Affirmation>> = favorites.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun refreshToday() {
        today.value = dateProvider.today()
    }

    fun setLanguage(lang: String) {
        language.value = lang
    }

    fun more() = extra.update { it + 1 }

    fun toggleFavorite() {
        val current = text.value ?: return
        setFavorite(current, !isFavorite.value)
    }

    fun setFavorite(text: String, favorite: Boolean) {
        viewModelScope.launch {
            try {
                favorites.setFavorite(text, favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed write only leaves the heart unchanged; nothing else depends on it.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Leaves room for many "more" taps per day without repeating another day's seed. */
        const val SEED_STEP = 1_000L
    }
}
