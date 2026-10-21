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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.wellness.BreathPhase
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
private fun BreathingScreen(onBack: () -> Unit) {
    var technique by rememberSaveable { mutableStateOf(BreathingTechnique.RELAX_478) }
    var minutes by rememberSaveable { mutableIntStateOf(BreathingPlan.SESSION_MINUTES[1]) }
    var running by rememberSaveable { mutableStateOf(false) }
    var elapsed by rememberSaveable { mutableLongStateOf(0L) }
    val total = minutes * MILLIS_PER_MINUTE
    val state = BreathingPlan.stateAt(technique, elapsed, total)
    val view = LocalView.current

    if (running) {
        LaunchedEffect(technique, minutes) {
            // Resumes from the saved elapsed time after rotation.
            val startedAt = withFrameMillis { it } - elapsed
            while (true) {
                elapsed = withFrameMillis { it } - startedAt
                if (elapsed >= total) {
                    running = false
                    break
                }
            }
        }
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }
    // Haptic tick on every change of step; respects the system's touch feedback setting.
    LaunchedEffect(state.phase, state.cycle) {
        if (running) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                            elapsed = 0
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, BreathingTechnique.entries.size),
                    ) { Text(stringResource(option.titleRes())) }
                }
            }
            Text(stringResource(technique.descriptionRes()), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            BreathingCircle(phase = state.phase, progress = state.progress, active = running || elapsed > 0) {
                Text(stringResource(state.phase.labelRes()), style = MaterialTheme.typography.titleLarge)
                Text(state.secondsLeft.toString(), style = MaterialTheme.typography.displayMedium)
            }
            val remainingSeconds = ((total - elapsed).coerceAtLeast(0) / MILLIS_PER_SECOND).toInt()
            Text(
                stringResource(R.string.breathing_remaining, remainingSeconds / SECONDS_PER_MINUTE, remainingSeconds % SECONDS_PER_MINUTE),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BreathingPlan.SESSION_MINUTES.forEach { option ->
                    FilterChip(
                        selected = option == minutes,
                        onClick = {
                            minutes = option
                            running = false
                            elapsed = 0
                        },
                        label = { Text(pluralStringResource(R.plurals.breathing_minutes, option, option)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (state.finished) elapsed = 0
                    running = !running
                }) {
                    Text(stringResource(if (running) R.string.breathing_pause else R.string.breathing_start))
                }
                OutlinedButton(onClick = {
                    running = false
                    elapsed = 0
                }) { Text(stringResource(R.string.breathing_reset)) }
            }
            if (state.finished) Text(stringResource(R.string.breathing_done), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.breathing_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

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
private const val MILLIS_PER_MINUTE = 60_000L
private const val SECONDS_PER_MINUTE = 60
private const val MIN_SCALE = 0.55f
private const val MID_SCALE = 0.75f
private const val MAX_SCALE = 1f
private const val HALO_ALPHA = 0.18f
private const val SMOOTHING_MILLIS = 120
private val CIRCLE_SIZE = 260.dp
private val RING_WIDTH = 6.dp
