package io.github.zaxar1231.mycycle.feature.security

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Covers the app while it is locked. It is a full-screen dialog window, so it also stays above
 * dialogs that were open when the app was locked.
 */
@Composable
fun AppLockGate(viewModel: AppLockViewModel, biometricUnlock: Boolean) {
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    if (!locked) return
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { (context as? Activity)?.moveTaskToBack(true) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        LockScreen(viewModel, biometricUnlock && Biometrics.isAvailable(context) && Biometrics.isEnrolled())
    }
}

@Composable
private fun LockScreen(viewModel: AppLockViewModel, biometrics: Boolean) {
    val state by viewModel.entry.collectAsStateWithLifecycle()
    val lockCount by viewModel.lockCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val title = stringResource(R.string.lock_biometric_title)
    val cancel = stringResource(R.string.lock_biometric_cancel)
    var prompt by remember { mutableStateOf<CancellationSignal?>(null) }
    var forgot by rememberSaveable { mutableStateOf(false) }

    val showPrompt: () -> Unit = {
        if (biometrics && prompt == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prompt = Biometrics.authenticate(
                context,
                title,
                cancel,
                onSuccess = viewModel::unlockedByBiometrics,
                onEnd = {
                    viewModel.biometricInProgress = false
                    prompt = null
                },
            )
            viewModel.biometricInProgress = prompt != null
        }
    }
    // Opens once per lock, not again after rotation or after the user closed it.
    var promptedFor by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(lockCount, biometrics) {
        if (biometrics && promptedFor != lockCount) {
            promptedFor = lockCount
            showPrompt()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            prompt?.cancel()
            viewModel.biometricInProgress = false
        }
    }

    PinScreen(
        title = stringResource(R.string.lock_title),
        entered = state.entered.length,
        message = pinMessage(state, viewModel::freezeEnded),
        enabled = !state.checking && !state.isFrozen(),
        onDigit = viewModel::digit,
        onBackspace = viewModel::backspace,
        onBiometric = if (biometrics) showPrompt else null,
        bottom = { TextButton(onClick = { forgot = true }) { Text(stringResource(R.string.lock_forgot)) } },
    )

    if (forgot) {
        ForgotPinDialog(
            biometrics = biometrics,
            onBiometric = {
                forgot = false
                showPrompt()
            },
            onDismiss = { forgot = false },
        )
    }
}

/** The PIN cannot be recovered: either biometrics, or erase everything and start over. */
@Composable
private fun ForgotPinDialog(biometrics: Boolean, onBiometric: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    var eraseFailed by rememberSaveable { mutableStateOf(false) }
    if (!confirmErase) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.lock_forgot_title)) },
            text = {
                Text(stringResource(if (biometrics) R.string.lock_forgot_text_biometric else R.string.lock_forgot_text))
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (biometrics) TextButton(onClick = onBiometric) { Text(stringResource(R.string.lock_forgot_use_biometric)) }
                    TextButton(onClick = { confirmErase = true }) { Text(stringResource(R.string.lock_forgot_erase)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_forgot_cancel)) }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.lock_erase_title)) },
            text = { Text(stringResource(if (eraseFailed) R.string.lock_erase_failed else R.string.lock_erase_text)) },
            confirmButton = {
                TextButton(onClick = { if (!eraseAllData(context)) eraseFailed = true }) { Text(stringResource(R.string.lock_erase_confirm)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_forgot_cancel)) } },
        )
    }
}

/**
 * Wipes every file and Keystore key of the app, the PIN with them, and closes the app; the next
 * start is like a fresh install. Returns false if the system refused.
 */
private fun eraseAllData(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
