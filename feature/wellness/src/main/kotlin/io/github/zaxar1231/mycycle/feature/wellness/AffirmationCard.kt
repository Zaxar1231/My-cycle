package io.github.zaxar1231.mycycle.feature.wellness

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Affirmation of the day for the home screen, with "more", favourite, share and breathing. */
@Composable
fun AffirmationCard(
    onOpenBreathing: () -> Unit,
    onOpenFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AffirmationViewModel = hiltViewModel(),
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.affirmation_title), style = MaterialTheme.typography.titleMedium)
            text?.let { Text(stringResource(R.string.affirmation_quote, it), style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Italic) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = viewModel::more, label = { Text(stringResource(R.string.affirmation_more)) })
                FilterChip(
                    selected = isFavorite,
                    onClick = viewModel::toggleFavorite,
                    enabled = text != null,
                    label = { Text(stringResource(if (isFavorite) R.string.affirmation_saved else R.string.affirmation_save)) },
                )
                AssistChip(
                    onClick = { text?.let { shareText(context, it) } },
                    enabled = text != null,
                    label = { Text(stringResource(R.string.affirmation_share)) },
                )
                AssistChip(onClick = onOpenFavorites, label = { Text(stringResource(R.string.affirmation_favorites)) })
                AssistChip(onClick = onOpenBreathing, label = { Text(stringResource(R.string.breathing_open)) })
            }
        }
    }
}

/** System share sheet: the text goes only where the user sends it. */
internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
