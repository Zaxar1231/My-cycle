package io.github.zaxar1231.mycycle.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.reminders.ReminderSettings
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val reminders: StateFlow<ReminderSettings?> = settings.settings.map { it.reminders }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun change(transform: (ReminderSettings) -> ReminderSettings) {
        val current = reminders.value ?: return
        viewModelScope.launch { settings.setReminders(transform(current)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Settings card content: which local reminders come and when. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersSection(viewModel: RemindersViewModel = hiltViewModel()) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val r = reminders ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(stringResource(R.string.reminders_period), r.periodSoon) { on -> viewModel.change { it.copy(periodSoon = on) } }
        if (r.periodSoon) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderSettings.DAYS_BEFORE_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = r.periodDaysBefore == days,
                        onClick = { viewModel.change { it.copy(periodDaysBefore = days) } },
                        label = { Text(pluralStringResource(R.plurals.reminders_days_before, days, days)) },
                    )
                }
            }
        }
        SwitchRow(stringResource(R.string.reminders_fertile), r.fertileWindow) { on -> viewModel.change { it.copy(fertileWindow = on) } }
        SwitchRow(stringResource(R.string.reminders_ovulation), r.ovulation) { on -> viewModel.change { it.copy(ovulation = on) } }
        SwitchRow(stringResource(R.string.reminders_log), r.dailyLog) { on -> viewModel.change { it.copy(dailyLog = on) } }
        if (r.dailyLog) {
            val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderSettings.LOG_TIME_OPTIONS.forEach { time ->
                    FilterChip(
                        selected = r.dailyLogTime == time,
                        onClick = { viewModel.change { it.copy(dailyLogTime = time) } },
                        label = { Text(time.format(format)) },
                    )
                }
            }
        }
        SwitchRow(stringResource(R.string.reminders_medications), r.medications) { on -> viewModel.change { it.copy(medications = on) } }
        Text(
            stringResource(R.string.reminders_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
