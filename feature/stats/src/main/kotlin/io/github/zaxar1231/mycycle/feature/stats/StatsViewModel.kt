package io.github.zaxar1231.mycycle.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.domain.repository.PhaseContentRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.stats.ChronologyEntry
import io.github.zaxar1231.mycycle.core.domain.stats.CycleHistoryItem
import io.github.zaxar1231.mycycle.core.domain.stats.CycleStatisticsCalculator
import io.github.zaxar1231.mycycle.core.domain.stats.CycleSummary
import io.github.zaxar1231.mycycle.core.domain.stats.LogStatistics
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.DeleteCycleUseCase
import io.github.zaxar1231.mycycle.core.domain.usecase.EditCycleUseCase
import io.github.zaxar1231.mycycle.core.domain.usecase.InvalidCycleEditException
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

data class StatsUiState(
    val today: LocalDate,
    /** Newest first. */
    val history: List<CycleHistoryItem>,
    val summary: CycleSummary,
    val logs: LogStatistics,
    val chronology: List<ChronologyEntry>,
    /** Today's phase, null without a current cycle or while the period is due. */
    val todayPhase: CyclePhase? = null,
)

/** The cycle being edited and what went wrong with the last attempt. */
data class CycleEditState(val cycleId: Long, val error: InvalidCycleEditException.Reason? = null, val failed: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    dayLogs: DayLogRepository,
    observePrediction: ObserveCyclePredictionUseCase,
    calculator: CycleStatisticsCalculator,
    private val editCycle: EditCycleUseCase,
    private val deleteCycle: DeleteCycleUseCase,
    private val dateProvider: DateProvider,
    private val phaseContent: PhaseContentRepository,
) : ViewModel() {
    // Refreshed on resume: the ViewModel can outlive midnight.
    private val today = MutableStateFlow(dateProvider.today())

    /** Null while loading. */
    val state: StateFlow<StatsUiState?> = today.flatMapLatest { day ->
        // One subscription to the cycles: the prediction and the list always describe the same data.
        combine(observePrediction.withCycles(day), dayLogs.observeAll()) { (prediction, cycleList), logs ->
            val history = calculator.history(prediction, cycleList, day)
            StatsUiState(
                today = day,
                history = history,
                summary = calculator.summary(history),
                logs = calculator.logs(prediction, history, logs),
                chronology = calculator.chronology(prediction, history, logs),
                todayPhase = prediction.today?.takeIf { it.daysLate == 0 && it.daysUntilNextPeriod > 0 }?.phase,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val language = MutableStateFlow(Locale.getDefault().language)

    /** Texts for today's phase; null if there is no phase or the content file cannot be read. */
    val todayContent: StateFlow<PhaseContent?> = combine(state.map { it?.todayPhase }.distinctUntilChanged(), language) { phase, lang ->
        phase?.let {
            try {
                phaseContent.content(it, lang)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setLanguage(lang: String) {
        language.value = lang
    }

    // Kept here rather than in the UI, so an open edit dialog survives rotation.
    private val _editing = MutableStateFlow<CycleEditState?>(null)
    val editing: StateFlow<CycleEditState?> = _editing.asStateFlow()

    fun refreshToday() {
        today.value = dateProvider.today()
    }

    fun startEditing(cycle: Cycle) {
        _editing.value = CycleEditState(cycle.id)
    }

    fun stopEditing() {
        _editing.value = null
    }

    fun save(cycle: Cycle, start: LocalDate, periodLength: Int) = launchEdit { editCycle(cycle, start, periodLength) }

    fun delete(cycle: Cycle) = launchEdit { deleteCycle(cycle) }

    private fun launchEdit(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _editing.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidCycleEditException) {
                _editing.value = _editing.value?.copy(error = e.reason, failed = false)
            } catch (e: Exception) {
                _editing.value = _editing.value?.copy(error = null, failed = true)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
