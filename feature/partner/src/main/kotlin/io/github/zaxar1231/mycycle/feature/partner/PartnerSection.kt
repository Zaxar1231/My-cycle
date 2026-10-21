package io.github.zaxar1231.mycycle.feature.partner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.zaxar1231.mycycle.core.domain.partner.InviteCode
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerPermission
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerState
import kotlinx.coroutines.delay

/**
 * Settings card content for partner mode: invite a partner or join with a code, then choose what
 * is shared, open the partner's cycle or unlink.
 */
@Composable
fun PartnerSection(onOpenPartner: () -> Unit, viewModel: PartnerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            PartnerState.Unavailable -> Note(stringResource(R.string.partner_unavailable))
            PartnerState.SignedOut -> Note(stringResource(R.string.partner_sign_in_first))
            is PartnerState.None -> NotLinked(s.endedByOther, viewModel, form.busy)
            is PartnerState.Inviting -> Inviting(s, viewModel)
            is PartnerState.Confirming -> Confirming(s, viewModel, form.busy)
            is PartnerState.Sharing -> Sharing(s, viewModel, form.busy)
            is PartnerState.Following -> Following(s, onOpenPartner, viewModel, form.busy)
            PartnerState.Orphaned -> {
                Note(stringResource(R.string.partner_orphaned))
                UnlinkButton(viewModel, form.busy)
            }
        }
        form.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun NotLinked(endedByOther: Boolean, viewModel: PartnerViewModel, busy: Boolean) {
    val incoming by viewModel.incomingCode.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var fromLink by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(incoming) {
        incoming?.let {
            code = it
            fromLink = true
            viewModel.consumeIncoming()
        }
    }
    if (endedByOther) Note(stringResource(R.string.partner_ended))
    Note(stringResource(R.string.partner_intro))
    Note(stringResource(R.string.partner_e2e))
    Button(onClick = viewModel::invite, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.partner_invite)) }
    Text(stringResource(R.string.partner_have_code), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = code,
        onValueChange = {
            code = it.trim()
            fromLink = false
        },
        label = { Text(stringResource(R.string.partner_code)) },
        singleLine = true,
        // The code holds the key: no suggestions, no learning by the keyboard.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    if (fromLink) Note(stringResource(R.string.partner_from_link))
    if (busy) {
        CircularProgressIndicator()
    } else {
        OutlinedButton(onClick = { viewModel.accept(code) }, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.partner_join))
        }
    }
}

@Composable
private fun Inviting(state: PartnerState.Inviting, viewModel: PartnerViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The code holds the key: no screenshots or recents preview while it is shown.
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val wasSecure = window != null && window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // Checks every few seconds, while the screen is visible, whether the partner has joined.
    LaunchedEffect(state.code) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(POLL_MILLIS)
                viewModel.poll()
            }
        }
    }
    val link = InviteCode.LINK_PREFIX + state.code
    Note(stringResource(R.string.partner_invite_hint))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        QrCode(link, stringResource(R.string.partner_qr_description))
    }
    Text(state.code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    Note(stringResource(R.string.partner_key_warning))
    val copyLabel = stringResource(R.string.partner_code)
    TextButton(onClick = { copySensitive(context, copyLabel, state.code) }) { Text(stringResource(R.string.partner_copy)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val shareText = stringResource(R.string.partner_share_text, link, state.code)
        val chooserTitle = stringResource(R.string.partner_share)
        Button(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }) { Text(stringResource(R.string.partner_share)) }
        OutlinedButton(onClick = viewModel::cancelInvite) { Text(stringResource(R.string.partner_cancel_invite)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.partner_waiting), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Confirming(state: PartnerState.Confirming, viewModel: PartnerViewModel, busy: Boolean) {
    Text(stringResource(R.string.partner_confirm_title), style = MaterialTheme.typography.bodyLarge)
    VerificationCode(state.verification)
    Note(stringResource(R.string.partner_confirm_hint))
    Button(onClick = viewModel::confirmPartner, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.partner_confirm))
    }
    UnlinkButton(viewModel, busy)
}

@Composable
private fun VerificationCode(code: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(code, style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Sharing(state: PartnerState.Sharing, viewModel: PartnerViewModel, busy: Boolean) {
    Text(stringResource(R.string.partner_linked_owner), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.partner_what_shared), style = MaterialTheme.typography.titleSmall)
    PartnerPermission.entries.forEach { permission ->
        SwitchRow(
            label = stringResource(permission.labelRes()),
            checked = permission in state.permissions,
            onChange = { viewModel.setPermission(permission, it) },
        )
    }
    Note(stringResource(R.string.partner_never_shared))
    UnlinkButton(viewModel, busy)
}

@Composable
private fun Following(state: PartnerState.Following, onOpenPartner: () -> Unit, viewModel: PartnerViewModel, busy: Boolean) {
    Text(stringResource(R.string.partner_linked_partner), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onOpenPartner, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.partner_open)) }
    SwitchRow(stringResource(R.string.partner_notify), state.notify, viewModel::setNotify)
    Text(stringResource(R.string.partner_verification), style = MaterialTheme.typography.titleSmall)
    VerificationCode(state.verification)
    Note(stringResource(R.string.partner_verification_hint))
    UnlinkButton(viewModel, busy)
}

@Composable
private fun UnlinkButton(viewModel: PartnerViewModel, busy: Boolean) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, enabled = !busy) { Text(stringResource(R.string.partner_unlink)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.partner_unlink_title)) },
            text = { Text(stringResource(R.string.partner_unlink_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.unlink()
                }) { Text(stringResource(R.string.partner_unlink)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.partner_keep)) } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun PartnerPermission.labelRes() = when (this) {
    PartnerPermission.PHASE -> R.string.partner_share_phase
    PartnerPermission.MOOD -> R.string.partner_share_mood
    PartnerPermission.SYMPTOMS -> R.string.partner_share_symptoms
    PartnerPermission.FERTILITY -> R.string.partner_share_fertility
    PartnerPermission.PET -> R.string.partner_share_pet
}

internal fun PartnerError.messageRes() = when (this) {
    PartnerError.INVALID_CODE -> R.string.partner_error_code
    PartnerError.INVITE_NOT_FOUND -> R.string.partner_error_not_found
    PartnerError.OWN_INVITE -> R.string.partner_error_own
    PartnerError.ALREADY_LINKED -> R.string.partner_error_linked
    PartnerError.SERVER_NOT_READY -> R.string.partner_error_server
    PartnerError.SIGNED_OUT -> R.string.partner_sign_in_first
    PartnerError.NETWORK -> R.string.partner_error_network
    PartnerError.UNKNOWN -> R.string.partner_error_unknown
}

/** Copies the invitation code marked as sensitive, so Android 13+ hides it in the clipboard preview. */
private fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

private const val POLL_MILLIS = 5_000L

/** ClipDescription.EXTRA_IS_SENSITIVE, spelled out: the constant exists only from API 33, the key works everywhere. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
