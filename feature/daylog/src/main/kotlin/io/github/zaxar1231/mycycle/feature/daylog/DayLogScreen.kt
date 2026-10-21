package io.github.zaxar1231.mycycle.feature.daylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.model.Flow
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.model.SexActivity
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import io.github.zaxar1231.mycycle.core.domain.usecase.InvalidDayLogException
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.zaxar1231.mycycle.core.ui.R as UiR

/** Log for one day. The date travels as epoch day, a plain Long works with type-safe routes. */
@Serializable
data class DayLogRoute(val epochDay: Long) {
    constructor(date: LocalDate) : this(date.toEpochDay())

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

fun NavGraphBuilder.dayLogScreen(onBack: () -> Unit, onManageMedications: () -> Unit) {
    composable<DayLogRoute> { DayLogScreen(onBack = onBack, onManageMedications = onManageMedications) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(
    onBack: () -> Unit,
    onManageMedications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.date.format(formatter)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.daylog_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.form != null && !state.saving) {
                        Text(stringResource(R.string.daylog_save))
                    }
                },
            )
        },
    ) { padding ->
        val form = state.form
        if (form == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.failed) ErrorText(R.string.daylog_error_load) else CircularProgressIndicator()
            }
            return@Scaffold
        }
        val log = form.log
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(R.string.daylog_period) {
                ChipGroup(PeriodEvent.entries, selected = setOfNotNull(log.periodEvent), label = { stringResource(it.labelRes()) }) {
                    viewModel.setPeriodEvent(if (log.periodEvent == it) null else it)
                }
                if (state.error == InvalidDayLogException.Field.PERIOD_END) ErrorText(R.string.daylog_error_period_end)
            }
            Section(R.string.daylog_flow) {
                ChipGroup(Flow.entries, selected = setOfNotNull(log.flow), label = { stringResource(it.labelRes()) }) {
                    viewModel.setFlow(if (log.flow == it) null else it)
                }
            }
            Section(R.string.daylog_mood) {
                ChipGroup(Mood.entries, selected = log.moods, label = { "${it.emoji()} ${stringResource(it.labelRes())}" }, onToggle = viewModel::toggleMood)
            }
            Section(R.string.daylog_symptoms) {
                ChipGroup(Symptom.entries, selected = log.symptoms, label = { stringResource(it.labelRes()) }, onToggle = viewModel::toggleSymptom)
            }
            Section(R.string.daylog_sex) {
                ChipGroup(SexActivity.entries, selected = setOfNotNull(log.sexActivity), label = { stringResource(it.labelRes()) }) {
                    viewModel.setSex(if (log.sexActivity == it) null else it)
                }
            }
            Section(R.string.daylog_body) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = form.temperatureText,
                        onValueChange = viewModel::setTemperatureText,
                        label = R.string.daylog_temperature,
                        isError = state.error == InvalidDayLogException.Field.TEMPERATURE,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = form.weightText,
                        onValueChange = viewModel::setWeightText,
                        label = R.string.daylog_weight,
                        isError = state.error == InvalidDayLogException.Field.WEIGHT,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.error == InvalidDayLogException.Field.TEMPERATURE) ErrorText(R.string.daylog_error_temperature)
                if (state.error == InvalidDayLogException.Field.WEIGHT) ErrorText(R.string.daylog_error_weight)
            }
            Section(R.string.daylog_medications) {
                MedicationChips(medications, log.medicationIds, viewModel::toggleMedication)
                TextButton(onClick = onManageMedications) { Text(stringResource(R.string.daylog_manage_medications)) }
            }
            Section(R.string.daylog_note) {
                OutlinedTextField(
                    value = log.note.orEmpty(),
                    onValueChange = viewModel::setNote,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text(stringResource(R.string.daylog_note_hint)) },
                )
            }
            // Repeated next to the button: the field with the problem may be scrolled out of view.
            state.error?.let { ErrorText(it.messageRes()) }
            if (state.failed) ErrorText(R.string.daylog_error_save)
            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.daylog_save))
            }
        }
    }
}

private fun InvalidDayLogException.Field.messageRes(): Int = when (this) {
    InvalidDayLogException.Field.DATE -> R.string.daylog_error_date
    InvalidDayLogException.Field.TEMPERATURE -> R.string.daylog_error_temperature
    InvalidDayLogException.Field.WEIGHT -> R.string.daylog_error_weight
    InvalidDayLogException.Field.PERIOD_END -> R.string.daylog_error_period_end
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(items: List<T>, selected: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item in selected, onClick = { onToggle(item) }, label = { Text(label(item)) })
        }
    }
}

@Composable
private fun MedicationChips(medications: List<Medication>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    val active = medications.filter { it.isActive || it.id in selected }
    if (active.isEmpty()) {
        Text(stringResource(R.string.daylog_no_medications), style = MaterialTheme.typography.bodySmall)
        return
    }
    ChipGroup(active.map { it.id }, selected = selected, label = { id -> active.first { it.id == id }.displayName() }, onToggle = onToggle)
}

private fun Medication.displayName() = if (dosage.isNullOrBlank()) name else "$name, $dosage"

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: Int, isError: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun ErrorText(text: Int) {
    Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
