package io.github.zaxar1231.mycycle.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zaxar1231.mycycle.core.domain.backup.BackupRules

/** Settings card content: a report for the doctor, a CSV table, and an encrypted backup. */
@Composable
fun DataSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The password is asked before the file is created and never goes into the saved instance
    // state; if the app is restarted in between, the new empty file is removed.
    var askBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    // Uris and choices survive a restart of the app while the file picker is open.
    var csvOptions by rememberSaveable { mutableStateOf(false) }
    var csvNotes by rememberSaveable { mutableStateOf(false) }
    var csvSex by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val date = viewModel.today().toString()
    val reportName = stringResource(R.string.data_report_file, date)
    val csvName = stringResource(R.string.data_csv_file, date)
    val backupName = stringResource(R.string.data_backup_file, date)

    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF)) { uri -> uri?.let(viewModel::saveReport) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        uri?.let { viewModel.saveCsv(it, includeNotes = csvNotes, includeSex = csvSex) }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BINARY)) { uri ->
        val password = backupPassword
        backupPassword = null
        when {
            uri != null && password != null -> viewModel.saveBackup(uri, password)
            uri != null -> viewModel.discard(uri)
            else -> password?.fill('\u0000')
        }
    }
    // Any type: file managers often label a backup as something else, and the content is checked anyway.
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(stringResource(R.string.data_intro))
        OutlinedButton(onClick = { report.launch(reportName) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_report))
        }
        Note(stringResource(R.string.data_report_note))
        OutlinedButton(onClick = { csvOptions = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_csv))
        }
        Note(stringResource(R.string.data_csv_note))
        OutlinedButton(onClick = { askBackupPassword = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_backup))
        }
        OutlinedButton(onClick = { restore.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_restore))
        }
        Note(stringResource(R.string.data_backup_note))
        if (state.busy) CircularProgressIndicator()
        state.message?.let { message ->
            val error = message in setOf(DataMessage.WRONG_PASSWORD, DataMessage.NOT_A_BACKUP, DataMessage.TOO_LARGE, DataMessage.FAILED)
            Text(
                stringResource(message.textRes()),
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (csvOptions) {
        AlertDialog(
            onDismissRequest = { csvOptions = false },
            title = { Text(stringResource(R.string.data_csv_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.data_csv_note), style = MaterialTheme.typography.bodyMedium)
                    CheckRow(stringResource(R.string.data_csv_include_notes), csvNotes) { csvNotes = it }
                    CheckRow(stringResource(R.string.data_csv_include_sex), csvSex) { csvSex = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    csvOptions = false
                    csv.launch(csvName)
                }) { Text(stringResource(R.string.data_csv_go)) }
            },
            dismissButton = { TextButton(onClick = { csvOptions = false }) { Text(stringResource(R.string.data_cancel)) } },
        )
    }
    if (askBackupPassword) {
        PasswordDialog(
            title = stringResource(R.string.data_backup_password_title),
            text = stringResource(R.string.data_backup_password_text),
            confirm = true,
            onDismiss = { askBackupPassword = false },
            onPassword = {
                askBackupPassword = false
                backupPassword?.fill('\u0000')
                backupPassword = it
                backup.launch(backupName)
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

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Asks for the backup password; with [confirm], twice. Hands it over as characters, not a String. */
@Composable
private fun PasswordDialog(title: String, text: String?, confirm: Boolean, onDismiss: () -> Unit, onPassword: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = BackupRules.isValidPassword(first.length) && (!confirm || first == second)
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
        confirmButton = {
            TextButton(onClick = {
                val password = first.toCharArray()
                first = ""
                second = ""
                onPassword(password)
            }, enabled = valid) { Text(stringResource(R.string.data_ok)) }
        },
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
    DataMessage.TOO_LARGE -> R.string.data_too_large
    DataMessage.FAILED -> R.string.data_failed
}

private const val PDF = "application/pdf"
private const val CSV = "text/csv"
private const val BINARY = "application/octet-stream"
