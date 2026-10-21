package io.github.zaxar1231.mycycle.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.domain.repository.PhaseContentRepository
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.SaveDayLogUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class DayContent(val day: LocalDate, val content: PhaseContent)

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
    private val settings: SettingsRepository,
    private val dayLogs: DayLogRepository,
    private val predictor: CyclePredictor,
    private val saveDayLog: SaveDayLogUseCase,
    private val dateProvider: DateProvider,
    private val phaseContent: PhaseContentRepository,
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

    private val language = MutableStateFlow(Locale.getDefault().language)

    /** Content for today's phase. */
    val todayContent: StateFlow<PhaseContent?> = combine(state, language) { s, lang -> phaseOn(s, s.today)?.let { it to lang } }
        .distinctUntilChanged()
        .map { key -> key?.let { (phase, lang) -> loadContent(phase, lang) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Content for the selected day's phase, tagged with the day so the sheet never shows a stale one. */
    val selectedContent: StateFlow<DayContent?> = combine(state, language) { s, lang ->
        s.selectedDay?.let { day -> phaseOn(s, day)?.let { Triple(day, it, lang) } }
    }
        .distinctUntilChanged()
        .map { key -> key?.let { (day, phase, lang) -> loadContent(phase, lang)?.let { DayContent(day, it) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun phaseOn(s: CalendarUiState, day: LocalDate): CyclePhase? =
        s.prediction?.segmentFor(day)?.let { segment ->
            CyclePredictor.phaseOf(ChronoUnit.DAYS.between(segment.start, day).toInt() + 1, segment)
        }

    /** A broken content file hides the card instead of crashing the calendar. */
    private suspend fun loadContent(phase: CyclePhase, lang: String): PhaseContent? =
        try {
            phaseContent.content(phase, lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Follows the UI language: the ViewModel survives a per-app or system language change. */
    fun setLanguage(lang: String) {
        language.value = lang
    }

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

    /** Goes through the day log, like the FAB form, so the mark can be undone there. */
    fun markPeriodStartedToday() {
        viewModelScope.launch {
            try {
                // Asked at tap time, not taken from state: the screen may show yesterday after midnight.
                val day = dateProvider.today()
                val log = dayLogs.get(day) ?: DayLog(date = day)
                saveDayLog(log.copy(periodEvent = PeriodEvent.STARTED), settings.settings.first().usualPeriodLength)
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
