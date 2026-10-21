package io.github.zaxar1231.mycycle.feature.security

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.zaxar1231.mycycle.core.domain.security.PinCheck
import io.github.zaxar1231.mycycle.core.domain.security.PinPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Asks for a new PIN twice; [onPinChosen] gets it once both entries match. */
@Composable
internal fun PinSetupDialog(onPinChosen: (String) -> Unit, onDismiss: () -> Unit) {
    // Not saveable: the PIN must not end up in the saved instance state. Rotation restarts the entry.
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    FullScreenDialog(onDismiss) {
        PinScreen(
            title = stringResource(if (first == null) R.string.pin_setup_enter else R.string.pin_setup_confirm),
            entered = entered.length,
            message = if (mismatch) stringResource(R.string.pin_setup_mismatch) else null,
            enabled = true,
            onDigit = { digit ->
                if (entered.length < PinPolicy.LENGTH) entered += digit
                if (entered.length == PinPolicy.LENGTH) {
                    val chosen = first
                    when {
                        chosen == null -> {
                            first = entered
                            mismatch = false
                        }
                        chosen == entered -> onPinChosen(entered)
                        else -> {
                            first = null
                            mismatch = true
                        }
                    }
                    entered = ""
                }
            },
            onBackspace = { entered = entered.dropLast(1) },
            bottom = { CancelButton(onDismiss) },
        )
    }
}

/**
 * Asks for the current PIN before it is changed or removed. Wrong attempts count as on the lock
 * screen. With [biometrics] a fingerprint or face also counts, so a forgotten PIN can be replaced.
 */
@Composable
internal fun PinVerifyDialog(check: suspend (String) -> PinCheck, biometrics: Boolean, onVerified: () -> Unit, onDismiss: () -> Unit) {
    // Not saved: after rotation the user starts the entry again, which is fine for four digits.
    var state by remember { mutableStateOf(PinEntryState()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = stringResource(R.string.pin_verify_title)
    val cancel = stringResource(R.string.lock_forgot_cancel)
    FullScreenDialog(onDismiss) {
        PinScreen(
            title = stringResource(R.string.pin_verify_title),
            entered = state.entered.length,
            message = pinMessage(state) { state = state.copy(frozenUntil = null, attemptsLeft = null) },
            enabled = !state.checking && !state.isFrozen(),
            onDigit = { digit ->
                if (state.entered.length < PinPolicy.LENGTH) {
                    val entered = state.entered + digit
                    state = state.copy(entered = entered)
                    if (entered.length == PinPolicy.LENGTH) {
                        state = state.copy(checking = true)
                        scope.launch {
                            val result = try {
                                check(entered)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            state = when (result) {
                                PinCheck.Correct -> PinEntryState().also { onVerified() }
                                is PinCheck.Wrong -> PinEntryState(attemptsLeft = result.attemptsLeft, frozenUntil = result.lockedUntilMillis)
                                is PinCheck.LockedOut -> PinEntryState(frozenUntil = result.untilMillis)
                                null -> PinEntryState()
                            }
                        }
                    }
                }
            },
            onBackspace = { state = state.copy(entered = state.entered.dropLast(1)) },
            onBiometric = if (biometrics && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                { Biometrics.authenticate(context, title, cancel, onSuccess = onVerified, onEnd = {}) }
            } else {
                null
            },
            bottom = { CancelButton(onDismiss) },
        )
    }
}

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        content = content,
    )
}

@Composable
private fun CancelButton(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_forgot_cancel)) }
}
