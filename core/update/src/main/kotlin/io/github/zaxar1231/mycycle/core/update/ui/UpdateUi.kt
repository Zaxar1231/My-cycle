package io.github.zaxar1231.mycycle.core.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.update.R
import io.github.zaxar1231.mycycle.core.update.ReleaseInfo
import io.github.zaxar1231.mycycle.core.update.UpdateState

@Composable
fun UpdatesCard(
    state: UpdateState,
    currentVersion: String,
    autoCheck: Boolean,
    onCheck: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.version_label, currentVersion))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onCheck, enabled = state !is UpdateState.Checking) {
                    Text(stringResource(R.string.update_check))
                }
                Spacer(Modifier.size(12.dp))
                if (state is UpdateState.Checking) CircularProgressIndicator(Modifier.size(24.dp))
            }
            val status = when (state) {
                UpdateState.Checking -> stringResource(R.string.update_checking)
                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                UpdateState.NoReleases -> stringResource(R.string.update_no_releases)
                is UpdateState.Error -> stringResource(R.string.update_error, state.message)
                else -> null
            }
            if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_auto))
                    Text(stringResource(R.string.update_auto_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoCheck, onCheckedChange = onAutoCheckChange)
            }
        }
    }
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: (ReleaseInfo) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.NeedsPermission -> state.release
        is UpdateState.DownloadFailed -> state.release
        else -> return
    }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title, release.version)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    is UpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()))
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.NeedsPermission -> Text(stringResource(R.string.update_permission))
                    is UpdateState.DownloadFailed -> Text(stringResource(R.string.update_download_failed, state.message))
                    else -> Unit
                }
                if (release.apkUrl == null) Text(stringResource(R.string.update_no_apk))
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            when {
                state is UpdateState.NeedsPermission -> TextButton(onClick = onOpenInstallSettings) {
                    Text(stringResource(R.string.update_open_settings))
                }
                release.apkUrl == null -> TextButton(onClick = { uriHandler.openUri(release.pageUrl) }) {
                    Text(stringResource(R.string.update_release_page))
                }
                else -> TextButton(onClick = { onDownload(release) }, enabled = state !is UpdateState.Downloading) {
                    Text(stringResource(R.string.update_install))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}
