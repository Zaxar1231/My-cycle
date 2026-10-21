package io.github.zaxar1231.mycycle.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zaxar1231.mycycle.core.domain.account.AuthError
import io.github.zaxar1231.mycycle.core.domain.account.AuthResult

/**
 * Settings card content: sign in, create an account or sign out. Accounts are optional; the
 * note says plainly that no health data is sent.
 */
@Composable
fun AccountSection(viewModel: AccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!viewModel.available) {
            Note(R.string.account_unavailable)
            return@Column
        }
        val current = account
        if (current != null) {
            Text(stringResource(R.string.account_signed_in, current.email), style = MaterialTheme.typography.bodyLarge)
            Note(R.string.account_privacy)
            OutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.account_sign_out)) }
        } else {
            Note(R.string.account_why)
            AccountForm(viewModel)
        }
    }
}

/** Onboarding step content: the same form, and [onSignedIn] once an account is ready. */
@Composable
fun AccountStepContent(onSignedIn: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val signedIn = account
    if (!viewModel.available) {
        Note(R.string.account_unavailable)
        return
    }
    if (signedIn != null) {
        LaunchedEffect(signedIn) { onSignedIn() }
        Text(stringResource(R.string.account_signed_in, signedIn.email), style = MaterialTheme.typography.bodyLarge)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AccountForm(viewModel) }
}

@Composable
private fun AccountForm(viewModel: AccountViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    // Not saveable: the password never goes into the saved instance state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.account_password)) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) {
                Text(stringResource(if (showPassword) R.string.account_hide else R.string.account_show))
            }
        },
        supportingText = { Text(stringResource(R.string.account_password_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    message(form)?.let { (text, error) ->
        Text(text, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
    if (form.busy) {
        CircularProgressIndicator()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.signIn(email, password) }) { Text(stringResource(R.string.account_sign_in)) }
            OutlinedButton(onClick = { viewModel.signUp(email, password) }) { Text(stringResource(R.string.account_sign_up)) }
        }
        TextButton(onClick = { viewModel.resetPassword(email) }) { Text(stringResource(R.string.account_forgot)) }
    }
}

/** The text under the form and whether it is an error. */
@Composable
private fun message(form: AccountFormState): Pair<String, Boolean>? {
    form.notice?.let { notice ->
        val res = when (notice) {
            AccountNotice.CONFIRM_EMAIL -> R.string.account_confirm_email
            AccountNotice.RESET_SENT -> R.string.account_reset_sent
            AccountNotice.BAD_EMAIL -> R.string.account_bad_email
            AccountNotice.SHORT_PASSWORD -> R.string.account_short_password
        }
        val error = notice == AccountNotice.BAD_EMAIL || notice == AccountNotice.SHORT_PASSWORD
        return stringResource(res) to error
    }
    val failed = form.result as? AuthResult.Failed ?: return null
    val res = when (failed.error) {
        AuthError.INVALID_CREDENTIALS -> R.string.account_error_credentials
        AuthError.EMAIL_NOT_CONFIRMED -> R.string.account_error_not_confirmed
        AuthError.ALREADY_REGISTERED -> R.string.account_error_exists
        AuthError.WEAK_PASSWORD -> R.string.account_error_weak
        AuthError.INVALID_EMAIL -> R.string.account_bad_email
        AuthError.RATE_LIMITED -> R.string.account_error_rate
        AuthError.NETWORK -> R.string.account_error_network
        AuthError.NOT_CONFIGURED -> R.string.account_unavailable
        AuthError.UNKNOWN -> R.string.account_error_unknown
    }
    return stringResource(res) to true
}

@Composable
private fun Note(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
