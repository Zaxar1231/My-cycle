package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.CompositionLocalProvider
import io.github.zaxar1231.mycycle.core.domain.insights.WeeklyReview
import io.github.zaxar1231.mycycle.core.domain.model.HomeBlock
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.ui.component.BlockCard
import io.github.zaxar1231.mycycle.core.ui.component.LocalBlockStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.ui.component.PhaseContentCard
import io.github.zaxar1231.mycycle.core.ui.component.ThemePicker
import io.github.zaxar1231.mycycle.core.ui.component.emoji
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

@Serializable
data object CalendarRoute

/** [homeExtras] and [petCard] are cards from other features: the affirmation and the pet. */
fun NavGraphBuilder.calendarScreen(
    onOpenDayLog: (LocalDate) -> Unit,
    onOpenPhases: () -> Unit,
    onOpenHormones: () -> Unit,
    onCustomizeHome: () -> Unit,
    homeExtras: @Composable () -> Unit = {},
    petCard: @Composable () -> Unit = {},
) {
    composable<CalendarRoute> {
        CalendarScreen(
            onOpenDayLog = onOpenDayLog,
            onOpenPhases = onOpenPhases,
            onOpenHormones = onOpenHormones,
            onCustomizeHome = onCustomizeHome,
            homeExtras = homeExtras,
            petCard = petCard,
        )
    }
}

@Composable
fun CalendarScreen(
    onOpenDayLog: (LocalDate) -> Unit,
    onOpenPhases: () -> Unit,
    onOpenHormones: () -> Unit,
    onCustomizeHome: () -> Unit,
    modifier: Modifier = Modifier,
    homeExtras: @Composable () -> Unit = {},
    petCard: @Composable () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasPet by viewModel.hasPet.collectAsStateWithLifecycle()
    val selectedContent by viewModel.selectedContent.collectAsStateWithLifecycle()
    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val week by viewModel.weekReview.collectAsStateWithLifecycle()
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
    var showTheme by rememberSaveable { mutableStateOf(false) }
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    // Drawn once the saved style and layout are known, so nothing switches after a moment.
    val saved = theme
    val blocks = layout
    if (saved == null || blocks == null) return
    // A soft glow of the primary colour from the top, so the screen has depth without a picture.
    val glow = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = HOME_GLOW_ALPHA), Color.Transparent))
    Column(
        modifier
            .fillMaxSize()
            .background(glow)
            .verticalScroll(rememberScrollState())
            .padding(bottom = AIR),
        verticalArrangement = Arrangement.spacedBy(AIR),
    ) {
        HomeHeader(
            today = state.today,
            phase = prediction.today?.phase,
            moods = todayLog?.moods.orEmpty(),
            week = week,
            onToggleMood = viewModel::toggleMood,
            onPeriodStarted = viewModel::markPeriodStartedToday,
            onOpenToday = { onOpenDayLog(state.today) },
        )
        // Blocks with nothing to show yet (no current cycle) leave no empty gap.
        val hasShape = prediction.shapeOn(state.today) != null
        val visible = blocks.order.filter { block ->
            blocks.style(block).visible && when (block) {
                HomeBlock.KEY_DATES -> hasShape
                HomeBlock.TODAY -> prediction.today != null
                HomeBlock.PET -> hasPet
                else -> true
            }
        }
        if (visible.isEmpty()) {
            Column(Modifier.padding(SIDE), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.home_all_hidden), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onCustomizeHome) { Text(stringResource(R.string.home_customize)) }
            }
        }
        visible.forEach { block ->
            // Cards of every feature read the block style through BlockCard.
            CompositionLocalProvider(LocalBlockStyle provides blocks.style(block)) {
                val side = Modifier.padding(horizontal = SIDE)
                when (block) {
                    HomeBlock.HERO -> BlockCard {
                        CycleHero(
                            style = saved.home,
                            prediction = prediction,
                            today = state.today,
                            onEditPeriod = { onOpenDayLog(state.today) },
                            onPeriodStarted = viewModel::markPeriodStartedToday,
                            onOpenTheme = { showTheme = true },
                        )
                    }
                    HomeBlock.KEY_DATES -> Box(side) { KeyDatesCard(prediction, state.today) }
                    HomeBlock.CALENDAR -> BlockCard(side) {
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
                    HomeBlock.TODAY -> Box(side) {
                        TodayCard(
                            prediction = prediction,
                            today = state.today,
                            onPeriodStarted = viewModel::markPeriodStartedToday,
                            onOpenPhases = onOpenPhases,
                            onOpenHormones = onOpenHormones,
                        )
                    }
                    HomeBlock.AFFIRMATION -> Box(side) { homeExtras() }
                    HomeBlock.PET -> Box(side) { petCard() }
                }
            }
        }
    }

    if (showTheme) {
        ThemeSheet(
            saved,
            onChange = viewModel::setTheme,
            onSeedChange = viewModel::setSeedColor,
            onCustomizeHome = {
                showTheme = false
                onCustomizeHome()
            },
            onDismiss = { showTheme = false },
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

private const val HOME_GLOW_ALPHA = 0.3f

/**
 * Today's date, phase, quick actions, mood check-in and the week so far, above the cards:
 * the first thing the home screen says.
 */
@Composable
private fun HomeHeader(
    today: LocalDate,
    phase: CyclePhase?,
    moods: Set<Mood>,
    week: WeeklyReview,
    onToggleMood: (Mood) -> Unit,
    onPeriodStarted: () -> Unit,
    onOpenToday: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    Column(Modifier.padding(horizontal = SIDE, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(today.format(formatter), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (phase != null) {
            Text(
                stringResource(phase.labelRes()),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onPeriodStarted, label = { Text(stringResource(R.string.today_period_started)) })
            AssistChip(onClick = onOpenToday, label = { Text(stringResource(R.string.day_log_button)) })
        }
        Text(
            stringResource(R.string.checkin_mood_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mood.entries.forEach { mood ->
                FilterChip(
                    selected = mood in moods,
                    onClick = { onToggleMood(mood) },
                    label = { Text("${mood.emoji()} ${stringResource(mood.labelRes())}") },
                )
            }
        }
        WeekReview(week)
    }
}

/** The last seven days in a line or two: how many were logged and what came up most. */
@Composable
private fun WeekReview(week: WeeklyReview) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.labelLarge, color = muted)
        if (week.loggedDays == 0) {
            Text(stringResource(R.string.review_empty), style = MaterialTheme.typography.bodyMedium, color = muted)
            return
        }
        Text(stringResource(R.string.review_logged, week.loggedDays), style = MaterialTheme.typography.bodyMedium)
        week.topSymptom?.let { Text(stringResource(R.string.review_symptom, stringResource(it.labelRes())), style = MaterialTheme.typography.bodyMedium) }
        week.topMood?.let { Text(stringResource(R.string.review_mood, stringResource(it.labelRes())), style = MaterialTheme.typography.bodyMedium) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(
    theme: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
    onSeedChange: (Int) -> Unit,
    onCustomizeHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.theme_sheet_title), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onCustomizeHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_customize)) }
            ThemePicker(theme, onChange, onSeedChange = onSeedChange)
        }
    }
}
