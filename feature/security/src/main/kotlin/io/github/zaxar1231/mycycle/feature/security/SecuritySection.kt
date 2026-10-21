package io.github.zaxar1231.mycycle.feature.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zaxar1231.mycycle.core.domain.security.AutoLock

private enum class PinAction { SET, CHANGE_VERIFY, CHANGE_SET, REMOVE }

/** Settings card content: PIN, biometrics, auto-lock and hiding the app in recents. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecuritySection(viewModel: SecurityViewModel = hiltViewModel()) {
    val hasPin by viewModel.hasPin.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val pin = hasPin ?: return
    val options = security ?: return
    val context = LocalContext.current
    val biometricsAvailable = Biometrics.isAvailable(context)
    var action by rememberSaveable { mutableStateOf<PinAction?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(R.string.security_pin, R.string.security_pin_hint, checked = pin) { on ->
            action = if (on) PinAction.SET else PinAction.REMOVE
        }
        if (pin) {
            TextButton(onClick = { action = PinAction.CHANGE_VERIFY }) { Text(stringResource(R.string.security_change_pin)) }
            SwitchRow(
                R.string.security_biometric,
                if (biometricsAvailable) R.string.security_biometric_hint else R.string.security_biometric_unavailable,
                checked = options.biometricUnlock && biometricsAvailable,
                enabled = biometricsAvailable,
                onChange = viewModel::setBiometricUnlock,
            )
            Text(stringResource(R.string.security_lock_after), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoLock.OPTIONS_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = minutes == options.lockAfterMinutes,
                        onClick = { viewModel.setLockAfter(minutes) },
                        label = {
                            Text(
                                if (minutes == 0) {
                                    stringResource(R.string.security_lock_now)
                                } else {
                                    pluralStringResource(R.plurals.security_lock_minutes, minutes, minutes)
                                },
                            )
                        },
                    )
                }
            }
        }
        SwitchRow(R.string.security_hide_recents, R.string.security_hide_recents_hint, checked = options.hideInRecents, onChange = viewModel::setHideInRecents)
    }

    when (action) {
        PinAction.SET, PinAction.CHANGE_SET -> PinSetupDialog(
            onPinChosen = {
                viewModel.setPin(it)
                action = null
            },
            onDismiss = { action = null },
        )
        PinAction.CHANGE_VERIFY -> PinVerifyDialog(viewModel::check, onVerified = { action = PinAction.CHANGE_SET }, onDismiss = { action = null })
        PinAction.REMOVE -> PinVerifyDialog(
            viewModel::check,
            onVerified = {
                viewModel.removePin()
                action = null
            },
            onDismiss = { action = null },
        )
        null -> Unit
    }
}

/** Onboarding step content: offer to set a PIN now; it can also be done later in settings. */
@Composable
fun PinOffer(viewModel: SecurityViewModel = hiltViewModel()) {
    val hasPin by viewModel.hasPin.collectAsStateWithLifecycle()
    var setting by rememberSaveable { mutableStateOf(false) }
    when (hasPin) {
        null -> Unit
        true -> Text(stringResource(R.string.security_pin_set), style = MaterialTheme.typography.bodyLarge)
        false -> Button(onClick = { setting = true }) { Text(stringResource(R.string.security_set_pin)) }
    }
    if (setting) {
        PinSetupDialog(
            onPinChosen = {
                viewModel.setPin(it)
                setting = false
            },
            onDismiss = { setting = false },
        )
    }
}

@Composable
private fun SwitchRow(title: Int, hint: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
