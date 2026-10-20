package io.github.zaxar1231.mycycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Shown when the encryption key is gone and the stored data can never be read again. */
@Composable
fun DataLostScreen(onStartOver: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.data_lost_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.data_lost_text), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { confirm = true }) { Text(stringResource(R.string.data_lost_start_over)) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.data_lost_confirm_title)) },
            text = { Text(stringResource(R.string.data_lost_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onStartOver()
                }) { Text(stringResource(R.string.data_lost_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.data_lost_confirm_no)) } },
        )
    }
}
