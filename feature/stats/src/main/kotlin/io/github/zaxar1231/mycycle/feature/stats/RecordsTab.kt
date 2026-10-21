package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.stats.LogStatistics
import io.github.zaxar1231.mycycle.core.ui.R as UiR
import io.github.zaxar1231.mycycle.core.ui.component.ExpandablePills
import io.github.zaxar1231.mycycle.core.ui.component.Pill
import io.github.zaxar1231.mycycle.core.ui.component.emoji
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import java.time.LocalDate

@Composable
internal fun RecordsTab(state: StatsUiState) {
    val stats = state.logs
    if (stats.loggedDays == 0 && stats.cycleLengths.isEmpty()) {
        EmptyText(R.string.stats_empty_records)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "patterns") { PatternsCard(stats) }
        item(key = "lengths") { CycleLengthsCard(stats) }
        item(key = "symptoms") { SymptomsCard(stats) }
        item(key = "moods") { MoodsCard(stats) }
        if (stats.temperatures.isNotEmpty()) {
            item(key = "temperature") {
                SeriesCard(R.string.stats_temperature, R.string.stats_temperature_value, R.string.stats_temperature_description, stats.temperatures)
            }
        }
        if (stats.weights.isNotEmpty()) {
            item(key = "weight") { SeriesCard(R.string.stats_weight, R.string.stats_weight_value, R.string.stats_weight_description, stats.weights) }
        }
        item(key = "disclaimer") {
            Text(stringResource(UiR.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatsCard(title: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PatternsCard(stats: LogStatistics) = StatsCard(R.string.stats_patterns) {
    if (stats.patterns.isEmpty()) {
        Text(stringResource(R.string.stats_patterns_empty), style = MaterialTheme.typography.bodyMedium)
        return@StatsCard
    }
    val pills = stats.patterns.map { pattern ->
        val name = stringResource(pattern.symptom.labelRes())
        Pill(pattern.symptom.name, stringResource(R.string.stats_pill, name, stringResource(R.string.stats_pattern_days, pattern.fromCycleDay, pattern.toCycleDay))) {
            Text(
                stringResource(
                    R.string.stats_pattern_details,
                    name,
                    pattern.occurrences,
                    pattern.total,
                    pattern.fromCycleDay,
                    pattern.toCycleDay,
                ),
            )
        }
    }
    ExpandablePills(pills)
}

@Composable
private fun CycleLengthsCard(stats: LogStatistics) = StatsCard(R.string.stats_cycle_lengths) {
    if (stats.cycleLengths.isEmpty()) {
        Text(stringResource(R.string.stats_cycle_lengths_empty), style = MaterialTheme.typography.bodyMedium)
        return@StatsCard
    }
    val format = rememberShortDateFormat()
    BarChart(
        values = stats.cycleLengths.map { it.second },
        labels = stats.cycleLengths.map { it.first.format(format) },
        description = stringResource(R.string.stats_cycle_lengths_description),
    )
}

@Composable
private fun SymptomsCard(stats: LogStatistics) = StatsCard(R.string.stats_symptoms) {
    if (stats.symptomFrequency.isEmpty()) {
        Text(stringResource(R.string.stats_symptoms_empty), style = MaterialTheme.typography.bodyMedium)
        return@StatsCard
    }
    val max = stats.symptomFrequency.first().second
    stats.symptomFrequency.forEach { (symptom, count) -> HorizontalBar(stringResource(symptom.labelRes()), count, max) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodsCard(stats: LogStatistics) = StatsCard(R.string.stats_moods) {
    if (stats.moodsByPhase.isEmpty()) {
        Text(stringResource(R.string.stats_moods_empty), style = MaterialTheme.typography.bodyMedium)
        return@StatsCard
    }
    CyclePhase.entries.forEach { phase ->
        val moods = stats.moodsByPhase[phase] ?: return@forEach
        Text(stringResource(phase.labelRes()), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            moods.take(TOP_MOODS).forEach { (mood, count) ->
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(R.string.stats_mood_count, mood.emoji(), stringResource(mood.labelRes()), count),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesCard(title: Int, valueFormat: Int, description: Int, series: List<Pair<LocalDate, Double>>) = StatsCard(title) {
    val pattern = stringResource(valueFormat)
    val locale = LocalConfiguration.current.locales[0]
    val dates = rememberShortDateFormat()
    LineChart(
        positions = series.map { it.first.toEpochDay() },
        values = series.map { it.second },
        format = { String.format(locale, pattern, it) },
        startLabel = series.first().first.format(dates),
        endLabel = series.last().first.format(dates),
        description = stringResource(description),
    )
}

private const val TOP_MOODS = 4
