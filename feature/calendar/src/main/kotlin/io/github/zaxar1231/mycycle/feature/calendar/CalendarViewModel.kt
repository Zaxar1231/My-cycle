package io.github.zaxar1231.mycycle.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.RecordPeriodStartUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val today: LocalDate,
    val month: YearMonth,
    /** Null while data is loading. */
    val prediction: CyclePrediction? = null,
    /** Days of the visible month that have a log entry. */
    val loggedDays: Set<LocalDate> = emptySet(),
    val selectedDay: LocalDate? = null,
    val saveFailed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    cycles: CycleRepository,
    settings: SettingsRepository,
    private val dayLogs: DayLogRepository,
    private val predictor: CyclePredictor,
    private val recordPeriodStart: RecordPeriodStartUseCase,
    private val dateProvider: DateProvider,
) : ViewModel() {
    // "Today" is refreshed whenever the screen resumes: the ViewModel can outlive midnight.
    private val today = MutableStateFlow(dateProvider.today())
    private val month = MutableStateFlow(YearMonth.from(today.value))
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val saveFailed = MutableStateFlow(false)

    private val prediction = today.flatMapLatest { day ->
        combine(
            cycles.observeCycles(),
            settings.settings,
            dayLogs.observeRange(day.minusDays(TEMPERATURE_LOOKBACK_DAYS), day),
        ) { cycleList, s, logs -> predict(day, cycleList, s, logs) }
    }

    private fun predict(
        day: LocalDate,
        cycleList: List<Cycle>,
        s: UserSettings,
        logs: List<DayLog>,
    ): CyclePrediction =
        predictor.predict(
            CyclePredictor.Input(
                cycles = cycleList,
                usualCycleLength = s.usualCycleLength,
                usualPeriodLength = s.usualPeriodLength,
                usualLengthsEstimated = s.usualLengthsEstimated,
                temperatures = logs.mapNotNull { log -> log.temperatureCelsius?.let { log.date to it } }.toMap(),
                today = day,
            ),
        )

    private val loggedDays = month.flatMapLatest { m ->
        dayLogs.observeRange(m.atDay(1), m.atEndOfMonth()).map { logs -> logs.map { it.date }.toSet() }
    }

    val state: StateFlow<CalendarUiState> = combine(today, prediction, loggedDays, month, selected) { day, p, logged, m, sel ->
        CalendarUiState(today = day, month = m, prediction = p, loggedDays = logged, selectedDay = sel)
    }.combine(saveFailed) { s, failed -> s.copy(saveFailed = failed) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            CalendarUiState(today.value, YearMonth.from(today.value)),
        )

    fun refreshToday() {
        val now = dateProvider.today()
        if (now != today.value) {
            if (month.value == YearMonth.from(today.value)) month.value = YearMonth.from(now)
            today.value = now
        }
    }

    fun showPreviousMonth() = month.update { it.minusMonths(1) }

    fun showNextMonth() = month.update { it.plusMonths(1) }

    fun showToday() = month.update { YearMonth.from(today.value) }

    fun select(day: LocalDate?) = selected.update { day }

    fun markPeriodStartedToday() {
        val periodLength = state.value.prediction?.averagePeriodLength ?: return
        viewModelScope.launch {
            try {
                // Asked at tap time, not taken from state: the screen may show yesterday after midnight.
                recordPeriodStart(dateProvider.today(), periodLength)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed.value = true
            }
        }
    }

    fun dismissSaveError() {
        saveFailed.value = false
    }

    private companion object {
        /** Enough days to cover the current cycle when looking for a temperature shift. */
        const val TEMPERATURE_LOOKBACK_DAYS = 60L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
