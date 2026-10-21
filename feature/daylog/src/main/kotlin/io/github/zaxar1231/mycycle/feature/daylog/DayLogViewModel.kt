package io.github.zaxar1231.mycycle.feature.daylog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Flow
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.model.SexActivity
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.usecase.InvalidDayLogException
import io.github.zaxar1231.mycycle.core.domain.usecase.SaveDayLogUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Editable form. Number fields stay text until saved, so half-typed values like "36," are fine. */
data class DayLogForm(
    val log: DayLog,
    val temperatureText: String = log.temperatureCelsius?.toString().orEmpty(),
    val weightText: String = log.weightKg?.toString().orEmpty(),
)

data class DayLogUiState(
    val date: LocalDate,
    val form: DayLogForm? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: InvalidDayLogException.Field? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class DayLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dayLogs: DayLogRepository,
    medications: MedicationRepository,
    private val settings: SettingsRepository,
    private val saveDayLog: SaveDayLogUseCase,
) : ViewModel() {
    private val date = savedStateHandle.toRoute<DayLogRoute>().date
    private val _state = MutableStateFlow(DayLogUiState(date))
    val state: StateFlow<DayLogUiState> = _state.asStateFlow()

    val medications: StateFlow<List<Medication>> = medications.observeMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            val existing = dayLogs.observe(date).first() ?: DayLog(date = date)
            _state.update { it.copy(form = DayLogForm(existing)) }
        }
    }

    private fun edit(transform: (DayLog) -> DayLog) = _state.update { s ->
        s.copy(form = s.form?.let { it.copy(log = transform(it.log)) }, error = null)
    }

    fun setPeriodEvent(event: PeriodEvent?) = edit { it.copy(periodEvent = event) }

    fun setFlow(flow: Flow?) = edit { it.copy(flow = flow) }

    fun toggleMood(mood: Mood) = edit { it.copy(moods = it.moods.toggle(mood)) }

    fun toggleSymptom(symptom: Symptom) = edit { it.copy(symptoms = it.symptoms.toggle(symptom)) }

    fun setSex(activity: SexActivity?) = edit { it.copy(sexActivity = activity) }

    fun toggleMedication(id: Long) = edit { it.copy(medicationIds = it.medicationIds.toggle(id)) }

    fun setNote(note: String) = edit { it.copy(note = note) }

    fun setTemperatureText(text: String) = _state.update { s -> s.copy(form = s.form?.copy(temperatureText = text), error = null) }

    fun setWeightText(text: String) = _state.update { s -> s.copy(form = s.form?.copy(weightText = text), error = null) }

    fun save() {
        val form = _state.value.form ?: return
        if (_state.value.saving) return
        val temperature = parseNumber(form.temperatureText) ?: return fail(InvalidDayLogException.Field.TEMPERATURE)
        val weight = parseNumber(form.weightText) ?: return fail(InvalidDayLogException.Field.WEIGHT)
        _state.update { it.copy(saving = true, error = null, failed = false) }
        viewModelScope.launch {
            try {
                val periodLength = settings.settings.first().usualPeriodLength
                saveDayLog(form.log.copy(temperatureCelsius = temperature.value, weightKg = weight.value), periodLength)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidDayLogException) {
                _state.update { it.copy(saving = false, error = e.field) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    private fun fail(field: InvalidDayLogException.Field) = _state.update { it.copy(error = field) }

    /** Wrapper so "empty field" (null value) differs from "cannot parse" (null wrapper). */
    private class Parsed(val value: Double?)

    private fun parseNumber(text: String): Parsed? {
        val trimmed = text.trim().replace(',', '.')
        if (trimmed.isEmpty()) return Parsed(null)
        return trimmed.toDoubleOrNull()?.let(::Parsed)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
