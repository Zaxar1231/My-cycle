package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.PredictionConfidence
import io.github.zaxar1231.mycycle.core.domain.cycle.PredictionSource
import io.github.zaxar1231.mycycle.core.domain.insights.ChanceLevel
import io.github.zaxar1231.mycycle.core.domain.insights.PregnancyChance
import io.github.zaxar1231.mycycle.core.domain.insights.PregnancyChanceModel
import io.github.zaxar1231.mycycle.core.ui.component.ExpandablePills
import io.github.zaxar1231.mycycle.core.ui.component.Pill
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Composable
fun TodayCard(
    prediction: CyclePrediction,
    today: LocalDate,
    onPeriodStarted: () -> Unit,
    onOpenPhases: () -> Unit,
    onOpenHormones: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasCycles = prediction.timeline.isNotEmpty()
    val status = prediction.today
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == null && hasCycles) {
                // Logged cycles start after today, for example after the device clock moved back.
                Text(stringResource(R.string.today_cycle_in_future), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (status == null) {
                Text(stringResource(R.string.today_no_data_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.today_no_data_text), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onPeriodStarted) { Text(stringResource(R.string.today_period_started)) }
                return@Column
            }
            val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
            Text(stringResource(R.string.today_cycle_day, status.cycleDay), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(status.phase.labelRes()), style = MaterialTheme.typography.titleMedium)

            val periodLine = when {
                status.daysLate > 0 -> pluralStringResource(R.plurals.today_period_late, status.daysLate, status.daysLate)
                status.daysUntilNextPeriod == 0 -> stringResource(R.string.today_period_expected_today)
                else -> pluralStringResource(R.plurals.today_until_period, status.daysUntilNextPeriod, status.daysUntilNextPeriod)
            }
            Text(periodLine, style = MaterialTheme.typography.bodyLarge)
            val ovulationLine = if (status.daysUntilOvulation == 0) {
                stringResource(R.string.today_ovulation_today)
            } else {
                pluralStringResource(R.plurals.today_until_ovulation, status.daysUntilOvulation, status.daysUntilOvulation)
            }
            Text(ovulationLine, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.today_fertile_window,
                    status.fertileWindow.start.format(formatter),
                    status.fertileWindow.endInclusive.format(formatter),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            PregnancyChanceModel.on(today, prediction)?.let { chance -> ExpandablePills(listOf(chancePill(chance))) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpenPhases, label = { Text(stringResource(R.string.today_open_phases)) })
                AssistChip(onClick = onOpenHormones, label = { Text(stringResource(R.string.today_open_hormones)) })
            }
            Text(stringResource(prediction.confidence.labelRes()), style = MaterialTheme.typography.labelLarge)
            if (status.currentOvulationConfirmed) {
                Text(stringResource(R.string.today_ovulation_confirmed), style = MaterialTheme.typography.labelLarge)
            }
            prediction.source.noteRes()?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall)
            }
            if (status.phase != CyclePhase.MENSTRUAL || status.daysLate > 0) {
                OutlinedButton(onClick = onPeriodStarted) { Text(stringResource(R.string.today_period_started)) }
            }
            Text(
                stringResource(UiR.string.medical_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = DISCLAIMER_ALPHA),
            )
        }
    }
}

private const val DISCLAIMER_ALPHA = 0.7f

/** "Chance to conceive · high"; opens the model estimate and the contraception warning. */
@Composable
private fun chancePill(chance: PregnancyChance): Pill {
    val level = stringResource(
        when (chance.level) {
            ChanceLevel.LOW -> R.string.chance_low
            ChanceLevel.MEDIUM -> R.string.chance_medium
            ChanceLevel.HIGH -> R.string.chance_high
        },
    )
    return Pill("chance", stringResource(R.string.chance_pill, level)) {
        Text(stringResource(R.string.chance_details, chance.percent), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun PredictionConfidence.labelRes() = when (this) {
    PredictionConfidence.LOW -> R.string.confidence_low
    PredictionConfidence.MEDIUM -> R.string.confidence_medium
    PredictionConfidence.HIGH -> R.string.confidence_high
}

private fun PredictionSource.noteRes() = when (this) {
    PredictionSource.HISTORY -> null
    PredictionSource.ONBOARDING -> R.string.source_onboarding
    PredictionSource.DEFAULTS -> R.string.source_defaults
}
