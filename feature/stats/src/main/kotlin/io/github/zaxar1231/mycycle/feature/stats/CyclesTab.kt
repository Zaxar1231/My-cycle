package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.stats.CycleHistoryItem
import io.github.zaxar1231.mycycle.core.domain.stats.CycleSummary
import io.github.zaxar1231.mycycle.core.domain.stats.Regularity
import io.github.zaxar1231.mycycle.core.ui.component.ExpandablePills
import io.github.zaxar1231.mycycle.core.ui.component.Pill
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun CyclesTab(state: StatsUiState, onEdit: (CycleHistoryItem) -> Unit) {
    if (state.history.isEmpty()) {
        EmptyText(R.string.stats_empty_cycles)
        return
    }
    val format = rememberShortDateFormat()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "summary") { SummaryCard(state.summary, format) }
        item(key = "history") { Text(stringResource(R.string.stats_history), style = MaterialTheme.typography.titleMedium) }
        items(state.history, key = { it.cycle.id }) { item -> HistoryCard(item, state.today, format, onEdit) }
    }
}

@Composable
private fun SummaryCard(summary: CycleSummary, format: DateTimeFormatter) {
    val pills = buildList {
        summary.current?.let { current ->
            add(
                Pill("current", stringResource(R.string.stats_pill, stringResource(R.string.stats_current), stringResource(R.string.stats_current_day, current.elapsedDays))) {
                    Text(stringResource(R.string.stats_current_details, current.start.format(format), current.elapsedDays, days(current.periodLength)))
                },
            )
        }
        summary.lastCompleted?.let { last ->
            add(
                Pill("last", stringResource(R.string.stats_pill, stringResource(R.string.stats_last), days(last.segment.length))) {
                    Text(stringResource(R.string.stats_last_details, range(last, format), days(last.segment.length), days(last.periodLength)))
                },
            )
        }
        // Locals, so they smart-cast: the summary comes from another module.
        val average = summary.averageCycleLength
        val shortest = summary.shortestCycle
        val longest = summary.longestCycle
        if (average != null && shortest != null && longest != null) {
            add(
                Pill("average", stringResource(R.string.stats_pill, stringResource(R.string.stats_average), stringResource(R.string.stats_average_value, average))) {
                    Text(
                        stringResource(
                            R.string.stats_average_details,
                            average,
                            summary.averagePeriodLength ?: 0.0,
                            days(shortest),
                            days(longest),
                            summary.completedCount,
                        ),
                    )
                },
            )
        }
        val (value, details) = when (summary.regularity) {
            Regularity.REGULAR -> R.string.stats_regular to stringResource(R.string.stats_regular_details, CycleConstants.REGULARITY_MAX_SPREAD_DAYS)
            Regularity.IRREGULAR -> R.string.stats_irregular to stringResource(R.string.stats_irregular_details, CycleConstants.REGULARITY_MAX_SPREAD_DAYS)
            Regularity.NOT_ENOUGH_DATA -> R.string.stats_regularity_unknown to
                stringResource(R.string.stats_regularity_unknown_details, CycleConstants.MIN_CYCLES_FOR_REGULARITY)
        }
        add(Pill("regularity", stringResource(R.string.stats_pill, stringResource(R.string.stats_regularity), stringResource(value))) { Text(details) })
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.stats_summary), style = MaterialTheme.typography.titleMedium)
            ExpandablePills(pills, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun HistoryCard(item: CycleHistoryItem, today: LocalDate, format: DateTimeFormatter, onEdit: (CycleHistoryItem) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val title = if (item.isCurrent) {
                    stringResource(R.string.stats_range, item.start.format(format), today.format(format))
                } else {
                    range(item, format)
                }
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEdit(item) }) { Text(stringResource(R.string.stats_edit)) }
            }
            CycleStrip(
                days = item.days,
                elapsedDays = item.elapsedDays,
                description = stringResource(R.string.stats_strip_description),
                modifier = Modifier.padding(end = 12.dp),
            )
            val caption = if (item.isCurrent) {
                stringResource(R.string.stats_current_caption, days(item.periodLength), item.elapsedDays)
            } else {
                stringResource(R.string.stats_cycle_caption, days(item.periodLength), days(item.segment.length))
            }
            Text(caption, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
internal fun days(count: Int): String = pluralStringResource(R.plurals.stats_days, count, count)

@Composable
private fun range(item: CycleHistoryItem, format: DateTimeFormatter): String =
    stringResource(R.string.stats_range, item.start.format(format), item.end.format(format))

@Composable
internal fun rememberShortDateFormat(): DateTimeFormatter {
    val pattern = stringResource(R.string.stats_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

@Composable
internal fun EmptyText(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(24.dp),
    )
}
