package io.github.zaxar1231.mycycle.feature.wellness

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.wellness.BreathPhase
import io.github.zaxar1231.mycycle.core.domain.wellness.BreathState
import io.github.zaxar1231.mycycle.core.domain.wellness.BreathingPlan
import io.github.zaxar1231.mycycle.core.domain.wellness.BreathingTechnique
import kotlinx.serialization.Serializable
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Serializable
data object BreathingRoute

@Serializable
data object FavoriteAffirmationsRoute

fun NavGraphBuilder.wellnessScreens(onBack: () -> Unit) {
    composable<BreathingRoute> { BreathingScreen(onBack) }
    composable<FavoriteAffirmationsRoute> { FavoritesScreen(onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WellnessScaffold(title: Int, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.wellness_back))
                    }
                },
            )
        },
        content = content,
    )
}

/**
 * A circle that grows on the inhale, stays on holds and shrinks on the exhale, with a countdown,
 * a session timer and a light haptic tick when the step changes. The screen stays on meanwhile.
 */
@Composable
private fun BreathingScreen(onBack: () -> Unit, viewModel: BreathingViewModel = hiltViewModel()) {
    var technique by rememberSaveable { mutableStateOf(BreathingTechnique.RELAX_478) }
    var minutes by rememberSaveable { mutableIntStateOf(BreathingPlan.SESSION_MINUTES[1]) }
    var running by rememberSaveable { mutableStateOf(false) }
    val elapsedState = rememberSaveable { mutableLongStateOf(0L) }
    val total = BreathingPlan.sessionMillis(technique, minutes)
    // Only the display reads the elapsed time every frame; the rest of the screen recomposes when the session ends.
    val finished by remember(total) { derivedStateOf { elapsedState.longValue >= total } }
    val view = LocalView.current

    // A locked screen or a call pauses the session instead of letting it run on unseen.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { running = false }
    if (running) {
        LaunchedEffect(technique, minutes) {
            // Adds up frame deltas, so it resumes from the saved time after rotation or a pause.
            var lastFrame = withFrameMillis { it }
            var lastStep = BreathingPlan.stateAt(technique, elapsedState.longValue, total).stepKey()
            while (elapsedState.longValue < total) {
                val now = withFrameMillis { it }
                elapsedState.longValue += (now - lastFrame).coerceAtMost(MAX_FRAME_GAP_MILLIS)
                lastFrame = now
                val step = BreathingPlan.stateAt(technique, elapsedState.longValue, total).stepKey()
                // Haptic tick on every change of step; respects the system's touch feedback setting.
                if (step != lastStep) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                lastStep = step
            }
            // The loop ends only when the whole session was breathed through.
            viewModel.sessionFinished()
            running = false
        }
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    WellnessScaffold(R.string.breathing_title, onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BreathingTechnique.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == technique,
                        onClick = {
                            technique = option
                            running = false
                            elapsedState.longValue = 0
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, BreathingTechnique.entries.size),
                    ) { Text(stringResource(option.titleRes())) }
                }
            }
            Text(stringResource(technique.descriptionRes()), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            BreathingDisplay(technique, total, elapsed = { elapsedState.longValue }, running = running)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BreathingPlan.SESSION_MINUTES.forEach { option ->
                    FilterChip(
                        selected = option == minutes,
                        onClick = {
                            minutes = option
                            running = false
                            elapsedState.longValue = 0
                        },
                        label = { Text(pluralStringResource(R.plurals.breathing_minutes, option, option)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (finished) elapsedState.longValue = 0
                    running = !running
                }) {
                    Text(stringResource(if (running) R.string.breathing_pause else R.string.breathing_start))
                }
                OutlinedButton(onClick = {
                    running = false
                    elapsedState.longValue = 0
                }) { Text(stringResource(R.string.breathing_reset)) }
            }
            if (finished) Text(stringResource(R.string.breathing_done), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.breathing_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The circle, the step countdown and the session timer; recomposes every frame while a session runs. */
@Composable
private fun BreathingDisplay(technique: BreathingTechnique, total: Long, elapsed: () -> Long, running: Boolean) {
    val now = elapsed()
    val state = BreathingPlan.stateAt(technique, now, total)
    BreathingCircle(phase = state.phase, progress = state.progress, active = running || now > 0) {
        Text(stringResource(state.phase.labelRes()), style = MaterialTheme.typography.titleLarge)
        Text(state.secondsLeft.toString(), style = MaterialTheme.typography.displayMedium)
    }
    // Rounded up, like the step countdown: 3:00 right after the start, 0:01 in the last second.
    val remainingSeconds = (((total - now).coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    Text(
        stringResource(R.string.breathing_remaining, remainingSeconds / SECONDS_PER_MINUTE, remainingSeconds % SECONDS_PER_MINUTE),
        style = MaterialTheme.typography.titleMedium,
    )
}

private fun BreathState.stepKey() = phase to cycle

@Composable
private fun BreathingCircle(phase: BreathPhase, progress: Float, active: Boolean, content: @Composable () -> Unit) {
    val target = if (!active) MID_SCALE else when (phase) {
        BreathPhase.INHALE -> MIN_SCALE + (MAX_SCALE - MIN_SCALE) * progress
        BreathPhase.HOLD_IN -> MAX_SCALE
        BreathPhase.EXHALE -> MAX_SCALE - (MAX_SCALE - MIN_SCALE) * progress
        BreathPhase.HOLD_OUT -> MIN_SCALE
    }
    // Progress already moves every frame; the short tween only smooths jumps such as a reset.
    val scale by animateFloatAsState(target, tween(SMOOTHING_MILLIS), label = "breath")
    Box(Modifier.size(CIRCLE_SIZE), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(CIRCLE_SIZE)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = HALO_ALPHA), CircleShape)
                .border(RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun FavoritesScreen(onBack: () -> Unit, viewModel: AffirmationViewModel = hiltViewModel()) {
    val favorites by viewModel.favoriteList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    WellnessScaffold(R.string.affirmation_favorites, onBack) { padding ->
        if (favorites.isEmpty()) {
            Text(stringResource(R.string.affirmation_favorites_empty), modifier = Modifier.padding(padding).padding(24.dp))
            return@WellnessScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(favorites, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.text, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { shareText(context, item.text) }) { Text(stringResource(R.string.affirmation_share)) }
                            TextButton(onClick = { viewModel.setFavorite(item.text, false) }) { Text(stringResource(R.string.affirmation_remove)) }
                        }
                    }
                }
            }
        }
    }
}

private fun BreathingTechnique.titleRes() = when (this) {
    BreathingTechnique.RELAX_478 -> R.string.breathing_478
    BreathingTechnique.BOX -> R.string.breathing_box
    BreathingTechnique.SLOW -> R.string.breathing_slow
}

private fun BreathingTechnique.descriptionRes() = when (this) {
    BreathingTechnique.RELAX_478 -> R.string.breathing_478_description
    BreathingTechnique.BOX -> R.string.breathing_box_description
    BreathingTechnique.SLOW -> R.string.breathing_slow_description
}

private fun BreathPhase.labelRes() = when (this) {
    BreathPhase.INHALE -> R.string.breathing_inhale
    BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> R.string.breathing_hold
    BreathPhase.EXHALE -> R.string.breathing_exhale
}

private const val MILLIS_PER_SECOND = 1_000L
/** Longer gaps between frames (the app was in the background) are not counted as breathing time. */
private const val MAX_FRAME_GAP_MILLIS = 200L
private const val SECONDS_PER_MINUTE = 60
private const val MIN_SCALE = 0.55f
private const val MID_SCALE = 0.75f
private const val MAX_SCALE = 1f
private const val HALO_ALPHA = 0.18f
private const val SMOOTHING_MILLIS = 120
private val CIRCLE_SIZE = 260.dp
private val RING_WIDTH = 6.dp
