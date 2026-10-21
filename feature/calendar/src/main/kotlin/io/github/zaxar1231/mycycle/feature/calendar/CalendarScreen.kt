package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

@Serializable
data object CalendarRoute

fun NavGraphBuilder.calendarScreen(onOpenDayLog: (LocalDate) -> Unit, onOpenPhases: () -> Unit, onOpenHormones: () -> Unit) {
    composable<CalendarRoute> {
        CalendarScreen(onOpenDayLog = onOpenDayLog, onOpenPhases = onOpenPhases, onOpenHormones = onOpenHormones)
    }
}

@Composable
fun CalendarScreen(
    onOpenDayLog: (LocalDate) -> Unit,
    onOpenPhases: () -> Unit,
    onOpenHormones: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val todayContent by viewModel.todayContent.collectAsStateWithLifecycle()
    val selectedContent by viewModel.selectedContent.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    if (state.saveFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            text = { Text(stringResource(R.string.save_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissSaveError) { Text(stringResource(R.string.ok)) } },
        )
    }
    val prediction = state.prediction
    if (prediction == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TodayCard(
            prediction = prediction,
            today = state.today,
            onPeriodStarted = viewModel::markPeriodStartedToday,
            onOpenPhases = onOpenPhases,
            onOpenHormones = onOpenHormones,
        )
        todayContent?.let { PhaseContentCard(it, state.today) }
        MonthCalendar(
            month = state.month,
            today = state.today,
            prediction = prediction,
            loggedDays = state.loggedDays,
            onPrevious = viewModel::showPreviousMonth,
            onNext = viewModel::showNextMonth,
            onToday = viewModel::showToday,
            onDayClick = viewModel::select,
        )
    }

    state.selectedDay?.let { day ->
        DayDetailsSheet(
            day = day,
            today = state.today,
            prediction = prediction,
            // Shown only once it belongs to this day, never the previous selection's text.
            content = selectedContent?.takeIf { it.day == day }?.content,
            onDismiss = { viewModel.select(null) },
            onLog = {
                viewModel.select(null)
                onOpenDayLog(day)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailsSheet(
    day: LocalDate,
    today: LocalDate,
    prediction: CyclePrediction,
    content: PhaseContent?,
    onDismiss: () -> Unit,
    onLog: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val segment = prediction.segmentFor(day)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(day.format(formatter), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(prediction.dayKind(day).labelRes()), style = MaterialTheme.typography.bodyLarge)
            if (segment != null) {
                val cycleDay = ChronoUnit.DAYS.between(segment.start, day).toInt() + 1
                Text(stringResource(R.string.today_cycle_day, cycleDay), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(CyclePredictor.phaseOf(cycleDay, segment).labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (segment.isPredicted) {
                    Text(stringResource(R.string.day_predicted_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            content?.let { PhaseContentCard(it, day) }
            if (!day.isAfter(today)) {
                Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.day_log_button)) }
            }
        }
    }
}
