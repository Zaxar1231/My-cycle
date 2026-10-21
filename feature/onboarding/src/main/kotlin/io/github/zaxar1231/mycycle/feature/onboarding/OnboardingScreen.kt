package io.github.zaxar1231.mycycle.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.ui.component.ThemePicker
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Serializable
data object OnboardingRoute

/** [pinOffer] is provided by the app, so onboarding does not depend on the security module. */
fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit, pinOffer: @Composable () -> Unit) {
    composable<OnboardingRoute> { OnboardingScreen(onFinished = onFinished, pinOffer = pinOffer) }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, pinOffer: @Composable () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler(enabled = state.stepIndex > 0, onBack = viewModel::back)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        LinearProgressIndicator(
            progress = { (state.stepIndex + 1f) / state.steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.ACCOUNT -> AccountStep()
                OnboardingStep.ROLE -> RoleStep(state.role, viewModel::selectRole)
                OnboardingStep.CYCLE -> CycleStep(state, viewModel)
                OnboardingStep.PET -> PetStep(state.pet, viewModel::requestPet)
                OnboardingStep.THEME -> {
                    StepTitle(R.string.onb_theme_title)
                    ThemePicker(state.theme, viewModel::setTheme)
                }
                OnboardingStep.PIN -> {
                    StepTitle(R.string.onb_pin_title, R.string.onb_pin_text)
                    pinOffer()
                }
            }
            if (state.error) Text(stringResource(R.string.onb_error), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (state.stepIndex > 0) {
                OutlinedButton(onClick = viewModel::back, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onb_back))
                }
            }
            Button(
                onClick = viewModel::next,
                enabled = state.canContinue && !state.saving,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(nextLabel(state)))
            }
        }
    }

    state.pendingPet?.let { pet ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPet,
            title = { Text(stringResource(R.string.onb_pet_confirm_title, stringResource(pet.nameRes()))) },
            text = { Text(stringResource(R.string.onb_pet_confirm_text)) },
            confirmButton = { TextButton(onClick = viewModel::confirmPet) { Text(stringResource(R.string.onb_pet_confirm_yes)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelPet) { Text(stringResource(R.string.onb_pet_confirm_no)) } },
        )
    }
}

private fun nextLabel(state: OnboardingState) = when {
    state.step == OnboardingStep.WELCOME -> R.string.onb_start
    state.step == OnboardingStep.ACCOUNT -> R.string.onb_account_continue
    state.stepIndex == state.steps.lastIndex -> R.string.onb_finish
    else -> R.string.onb_next
}

@Composable
private fun StepTitle(title: Int, text: Int? = null) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    if (text != null) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WelcomeStep() {
    StepTitle(R.string.onb_welcome_title, R.string.onb_welcome_text)
    Text(stringResource(R.string.onb_disclaimer), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AccountStep() {
    StepTitle(R.string.onb_account_title, R.string.onb_account_text)
}

@Composable
private fun RoleStep(role: UserRole, onSelect: (UserRole) -> Unit) {
    StepTitle(R.string.onb_role_title)
    OptionCard(R.string.onb_role_tracker, R.string.onb_role_tracker_hint, role == UserRole.TRACKER) { onSelect(UserRole.TRACKER) }
    OptionCard(R.string.onb_role_partner, R.string.onb_role_partner_hint, role == UserRole.PARTNER) { onSelect(UserRole.PARTNER) }
}

@Composable
private fun OptionCard(title: Int, hint: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    StepTitle(R.string.onb_cycle_title, R.string.onb_cycle_text)

    var showPicker by rememberSaveable { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    Text(stringResource(R.string.onb_last_period), style = MaterialTheme.typography.titleSmall)
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text(state.lastPeriodStart?.format(formatter) ?: stringResource(R.string.onb_pick_date))
    }
    if (state.lastPeriodStart == null) {
        Text(stringResource(R.string.onb_last_period_unknown), style = MaterialTheme.typography.bodySmall)
    }

    LengthPicker(
        title = R.string.onb_cycle_length,
        value = state.cycleLength,
        range = CycleConstants.MIN_REGULAR_CYCLE_LENGTH..CycleConstants.MAX_REGULAR_CYCLE_LENGTH,
        unknown = state.cycleLengthUnknown,
        onValue = viewModel::setCycleLength,
        onUnknown = viewModel::setCycleLengthUnknown,
    )
    LengthPicker(
        title = R.string.onb_period_length,
        value = state.periodLength,
        range = CycleConstants.MIN_PERIOD_LENGTH..CycleConstants.MAX_PERIOD_LENGTH,
        unknown = state.periodLengthUnknown,
        onValue = viewModel::setPeriodLength,
        onUnknown = viewModel::setPeriodLengthUnknown,
    )

    if (showPicker) {
        val today = remember { LocalDate.now() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.lastPeriodStart?.toUtcMillis(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.toLocalDate().isAfter(today)
                    override fun isSelectableYear(year: Int) = year <= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLastPeriodStart(pickerState.selectedDateMillis?.toLocalDate())
                    showPicker = false
                }) { Text(stringResource(R.string.onb_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.onb_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

@Composable
private fun LengthPicker(
    title: Int,
    value: Int,
    range: IntRange,
    unknown: Boolean,
    onValue: (Int) -> Unit,
    onUnknown: (Boolean) -> Unit,
) {
    val label = stringResource(title)
    Text(
        if (unknown) label else stringResource(R.string.onb_length_value, label, pluralStringResource(R.plurals.onb_days, value, value)),
        style = MaterialTheme.typography.titleSmall,
    )
    Slider(
        value = value.toFloat(),
        onValueChange = { onValue(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1,
        enabled = !unknown,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onUnknown(!unknown) },
    ) {
        Checkbox(checked = unknown, onCheckedChange = onUnknown)
        Text(stringResource(R.string.onb_dont_know))
    }
}

@Composable
private fun PetStep(selected: PetType?, onPick: (PetType) -> Unit) {
    StepTitle(R.string.onb_pet_title, R.string.onb_pet_text)
    PetType.entries.forEach { pet ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = pet == selected, onClick = { if (selected == null) onPick(pet) }, role = Role.RadioButton),
            colors = if (pet == selected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pet.emoji(), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(pet.nameRes()), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (selected != null) {
        Text(stringResource(R.string.onb_pet_locked), style = MaterialTheme.typography.bodySmall)
    }
}

private fun PetType.nameRes() = when (this) {
    PetType.CAT -> R.string.pet_cat
    PetType.FOX -> R.string.pet_fox
    PetType.BUNNY -> R.string.pet_bunny
    PetType.PANDA -> R.string.pet_panda
    PetType.PENGUIN -> R.string.pet_penguin
}

/** Placeholder art until the real pet animations arrive in stage 10. */
private fun PetType.emoji() = when (this) {
    PetType.CAT -> "🐱"
    PetType.FOX -> "🦊"
    PetType.BUNNY -> "🐰"
    PetType.PANDA -> "🐼"
    PetType.PENGUIN -> "🐧"
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
