package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.stats.ChronologyEntry
import io.github.zaxar1231.mycycle.core.ui.component.emoji
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Every day log, newest first. Tapping one opens it in the day log form. */
@Composable
internal fun ChronologyTab(state: StatsUiState, onOpenDayLog: (LocalDate) -> Unit) {
    if (state.chronology.isEmpty()) {
        EmptyText(R.string.stats_empty_chronology)
        return
    }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.chronology, key = { it.log.date.toEpochDay() }) { entry ->
            ChronologyItem(entry, formatter, onClick = { onOpenDayLog(entry.log.date) })
        }
    }
}

@Composable
private fun ChronologyItem(entry: ChronologyEntry, formatter: DateTimeFormatter, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.log.date.format(formatter), style = MaterialTheme.typography.titleSmall)
            // Locals, so they smart-cast: the entry comes from another module.
            val cycleDay = entry.cycleDay
            val phase = entry.phase
            if (cycleDay != null && phase != null) {
                Text(
                    stringResource(R.string.stats_chronology_place, cycleDay, stringResource(phase.labelRes())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val summary = summaryOf(entry.log)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium)
            entry.log.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = NOTE_LINES, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** One line with everything logged that day except the note. */
@Composable
private fun summaryOf(log: DayLog): String {
    val locale = LocalConfiguration.current.locales[0]
    val temperature = stringResource(R.string.stats_temperature_value)
    val weight = stringResource(R.string.stats_weight_value)
    return buildList {
        log.periodEvent?.let { add(stringResource(it.labelRes())) }
        log.flow?.let { add(stringResource(it.labelRes())) }
        log.moods.forEach { add("${it.emoji()} ${stringResource(it.labelRes())}") }
        log.symptoms.forEach { add(stringResource(it.labelRes())) }
        log.sexActivity?.let { add(stringResource(it.labelRes())) }
        log.temperatureCelsius?.let { add(String.format(locale, temperature, it)) }
        log.weightKg?.let { add(String.format(locale, weight, it)) }
    }.joinToString(SEPARATOR)
}

private const val SEPARATOR = " · "
private const val NOTE_LINES = 2
