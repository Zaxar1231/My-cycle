package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.stats.CycleHistoryItem
import io.github.zaxar1231.mycycle.core.domain.usecase.InvalidCycleEditException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Change the start date and period length of a cycle, or delete it after a confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditCycleDialog(
    item: CycleHistoryItem,
    today: LocalDate,
    edit: CycleEditState,
    onSave: (LocalDate, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var startDay by rememberSaveable(item.cycle.id) { mutableLongStateOf(item.start.toEpochDay()) }
    var periodLength by rememberSaveable(item.cycle.id) { mutableIntStateOf(item.periodLength) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val start = LocalDate.ofEpochDay(startDay)
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stats_edit_start, start.format(formatter)))
                TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.stats_edit_change_date)) }
                Text(stringResource(R.string.stats_edit_period_length), style = MaterialTheme.typography.labelLarge)
                Text(days(periodLength))
                Slider(
                    value = periodLength.toFloat(),
                    onValueChange = { periodLength = it.roundToInt() },
                    valueRange = CycleConstants.MIN_PERIOD_LENGTH.toFloat()..CycleConstants.MAX_PERIOD_LENGTH.toFloat(),
                    steps = CycleConstants.MAX_PERIOD_LENGTH - CycleConstants.MIN_PERIOD_LENGTH - 1,
                )
                edit.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
                if (edit.failed) Text(stringResource(R.string.stats_error_save), color = MaterialTheme.colorScheme.error)
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.stats_edit_delete)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(start, periodLength) }) { Text(stringResource(R.string.stats_edit_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stats_edit_cancel)) } },
    )

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = start.toUtcMillis(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.toUtcDate().isAfter(today)
                    override fun isSelectableYear(year: Int) = year <= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDay = it.toUtcDate().toEpochDay() }
                    pickDate = false
                }) { Text(stringResource(R.string.stats_edit_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.stats_edit_cancel)) } },
        ) { DatePicker(pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.stats_delete_title)) },
            text = { Text(stringResource(R.string.stats_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.stats_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.stats_edit_cancel)) } },
        )
    }
}

private fun InvalidCycleEditException.Reason.messageRes(): Int = when (this) {
    InvalidCycleEditException.Reason.DATE -> R.string.stats_error_date
    InvalidCycleEditException.Reason.PERIOD_LENGTH -> R.string.stats_error_period_length
    InvalidCycleEditException.Reason.OVERLAP -> R.string.stats_error_overlap
}

// The date picker works in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
