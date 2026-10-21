package io.github.zaxar1231.mycycle.feature.daylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Serializable
data object MedicationsRoute

fun NavGraphBuilder.medicationsScreen(onBack: () -> Unit) {
    composable<MedicationsRoute> { MedicationsScreen(onBack = onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(onBack: () -> Unit, viewModel: MedicationsViewModel = hiltViewModel()) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    // Only the id survives rotation; the medication itself is looked up in the list.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> if (id == NEW_MEDICATION) Medication(name = "") else medications.find { it.id == id } }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.medications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.daylog_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editingId = NEW_MEDICATION }) {
                Text(stringResource(R.string.medications_add))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.medications_reminder_note), style = MaterialTheme.typography.bodySmall)
            }
            if (medications.isEmpty()) {
                item { Text(stringResource(R.string.daylog_no_medications)) }
            }
            items(medications, key = { it.id }) { medication ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(medication.name, style = MaterialTheme.typography.titleMedium)
                            medication.dosage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            medication.reminderTime?.let {
                                Text(stringResource(R.string.medications_reminder_at, it.format(timeFormatter)), style = MaterialTheme.typography.bodySmall)
                            }
                            if (!medication.isActive) {
                                Text(stringResource(R.string.medications_archived), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = { editingId = medication.id }) { Text(stringResource(R.string.medications_edit)) }
                        if (medication.isActive) {
                            TextButton(onClick = { viewModel.archive(medication) }) { Text(stringResource(R.string.medications_archive)) }
                        } else {
                            TextButton(onClick = { viewModel.restore(medication) }) { Text(stringResource(R.string.medications_restore)) }
                        }
                    }
                }
            }
        }
    }

    editing?.let { medication ->
        MedicationDialog(
            initial = medication,
            onDismiss = { editingId = null },
            onSave = { name, dosage, time ->
                viewModel.save(medication, name, dosage, time)
                editingId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationDialog(initial: Medication, onDismiss: () -> Unit, onSave: (String, String, LocalTime?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var dosage by rememberSaveable { mutableStateOf(initial.dosage.orEmpty()) }
    var withReminder by rememberSaveable { mutableStateOf(initial.reminderTime != null) }
    val reminder = initial.reminderTime ?: DEFAULT_REMINDER
    val timeState = rememberTimePickerState(initialHour = reminder.hour, initialMinute = reminder.minute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.medications_add else R.string.medications_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.medications_name)) }, singleLine = true)
                OutlinedTextField(dosage, { dosage = it }, label = { Text(stringResource(R.string.medications_dosage)) }, singleLine = true)
                OutlinedButton(onClick = { withReminder = !withReminder }) {
                    Text(stringResource(if (withReminder) R.string.medications_remove_reminder else R.string.medications_add_reminder))
                }
                if (withReminder) TimePicker(timeState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, dosage, if (withReminder) LocalTime.of(timeState.hour, timeState.minute) else null) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.daylog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.medications_cancel)) } },
    )
}

private val DEFAULT_REMINDER: LocalTime = LocalTime.of(9, 0)

/** Room ids start at 1, so 0 marks a medication that is not saved yet. */
private const val NEW_MEDICATION = 0L
