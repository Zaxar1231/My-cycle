package io.github.zaxar1231.mycycle.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zaxar1231.mycycle.core.domain.backup.BackupRules
import java.time.LocalDate

/** Settings card content: a report for the doctor, a CSV table, and an encrypted backup. */
@Composable
fun DataSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Not saveable: passwords never go into the saved instance state.
    var pendingBackup by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    val date = LocalDate.now().toString()
    val reportName = stringResource(R.string.data_report_file, date)

    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF)) { uri -> uri?.let(viewModel::saveReport) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri -> uri?.let(viewModel::saveCsv) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BINARY)) { uri ->
        if (uri != null) {
            backupUri = uri
            pendingBackup = true
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(stringResource(R.string.data_intro))
        OutlinedButton(onClick = { report.launch(reportName) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_report))
        }
        OutlinedButton(onClick = { csv.launch("twilight-cycle-$date.csv") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_csv))
        }
        Note(stringResource(R.string.data_csv_note))
        OutlinedButton(onClick = { backup.launch("twilight-cycle-$date.tcbackup") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_backup))
        }
        OutlinedButton(onClick = { restore.launch(arrayOf(BINARY)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_restore))
        }
        Note(stringResource(R.string.data_backup_note))
        if (state.busy) CircularProgressIndicator()
        state.message?.let { message ->
            val error = message in setOf(DataMessage.WRONG_PASSWORD, DataMessage.NOT_A_BACKUP, DataMessage.FAILED)
            Text(
                stringResource(message.textRes()),
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    val target = backupUri
    if (pendingBackup && target != null) {
        PasswordDialog(
            title = stringResource(R.string.data_backup_password_title),
            text = stringResource(R.string.data_backup_password_text),
            confirm = true,
            onDismiss = { pendingBackup = false },
            onPassword = {
                pendingBackup = false
                viewModel.saveBackup(target, it)
            },
        )
    }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.data_restore_title)) },
            text = { Text(stringResource(R.string.data_restore_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    restoreUri = uri
                }) { Text(stringResource(R.string.data_restore_go)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.data_cancel)) } },
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.data_restore_password_title),
            text = null,
            confirm = false,
            onDismiss = { restoreUri = null },
            onPassword = {
                restoreUri = null
                viewModel.restore(uri, it)
            },
        )
    }
}

/** Asks for the backup password; with [confirm], twice. Hands it over as characters, not a String. */
@Composable
private fun PasswordDialog(title: String, text: String?, confirm: Boolean, onDismiss: () -> Unit, onPassword: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = BackupRules.isValidPassword(first.toCharArray()) && (!confirm || first == second)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                PasswordField(first, { first = it }, stringResource(R.string.data_password))
                if (confirm) PasswordField(second, { second = it }, stringResource(R.string.data_password_again))
                Text(
                    stringResource(R.string.data_password_hint, BackupRules.MIN_PASSWORD_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPassword(first.toCharArray()) }, enabled = valid) { Text(stringResource(R.string.data_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.data_cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun DataMessage.textRes() = when (this) {
    DataMessage.REPORT_SAVED -> R.string.data_report_saved
    DataMessage.CSV_SAVED -> R.string.data_csv_saved
    DataMessage.BACKUP_SAVED -> R.string.data_backup_saved
    DataMessage.RESTORED -> R.string.data_restored
    DataMessage.WRONG_PASSWORD -> R.string.data_wrong_password
    DataMessage.NOT_A_BACKUP -> R.string.data_not_a_backup
    DataMessage.FAILED -> R.string.data_failed
}

private const val PDF = "application/pdf"
private const val CSV = "text/csv"
private const val BINARY = "application/octet-stream"
