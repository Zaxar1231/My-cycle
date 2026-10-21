package io.github.zaxar1231.mycycle.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleSegment
import io.github.zaxar1231.mycycle.core.domain.insights.PhaseAnalysis
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.domain.repository.PhaseContentRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** The cycle that contains today, with today's place in it. */
data class CycleNow(
    val segment: CycleSegment,
    val cycleDay: Int,
    val phase: CyclePhase,
    val ovulationDay: Int,
    val ranges: Map<CyclePhase, IntRange>,
)

sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    data object NoData : InsightsUiState

    /** The logged cycle starts after today, for example after the device clock moved back. */
    data object CycleInFuture : InsightsUiState

    /** The period is expected today or late. */
    data class PeriodDue(val daysLate: Int) : InsightsUiState
    data class Ready(val now: CycleNow) : InsightsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    observePrediction: ObserveCyclePredictionUseCase,
    private val phaseContent: PhaseContentRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {
    private val today = MutableStateFlow(dateProvider.today())
    private val language = MutableStateFlow(Locale.getDefault().language)

    val state: StateFlow<InsightsUiState> = today.flatMapLatest { day ->
        observePrediction(day).map<CyclePrediction, InsightsUiState> { prediction ->
            val status = prediction.today
                ?: return@map if (prediction.timeline.isEmpty()) InsightsUiState.NoData else InsightsUiState.CycleInFuture
            // From the expected day on, today lies in a predicted segment: the real cycle is unknown until a new start.
            if (status.daysLate > 0 || status.daysUntilNextPeriod == 0) return@map InsightsUiState.PeriodDue(status.daysLate)
            val segment = prediction.segmentFor(day) ?: return@map InsightsUiState.NoData
            val cycleDay = ChronoUnit.DAYS.between(segment.start, day).toInt() + 1
            InsightsUiState.Ready(
                CycleNow(
                    segment = segment,
                    cycleDay = cycleDay,
                    phase = CyclePredictor.phaseOf(cycleDay, segment),
                    ovulationDay = ChronoUnit.DAYS.between(segment.start, segment.ovulation).toInt() + 1,
                    ranges = PhaseAnalysis.ranges(segment),
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsUiState.Loading)

    /** Texts for every phase. Empty if the content file cannot be read: the screen still works. */
    val content: StateFlow<Map<CyclePhase, PhaseContent>> = language.mapLatest { lang ->
        try {
            CyclePhase.entries.associateWith { phaseContent.content(it, lang) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    fun refreshToday() {
        today.value = dateProvider.today()
    }

    fun setLanguage(lang: String) {
        language.value = lang
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

