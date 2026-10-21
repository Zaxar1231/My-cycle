package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import java.time.LocalDate

/** What many people notice in this phase: possible symptoms, upsides, downsides and a tip of the day. */
@Composable
fun PhaseContentCard(content: PhaseContent, day: LocalDate, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(content.phase.labelRes()), style = MaterialTheme.typography.titleMedium)
            content.tipFor(day.dayOfYear)?.let { tip ->
                Text(stringResource(R.string.content_tip), style = MaterialTheme.typography.labelLarge)
                Text(tip, style = MaterialTheme.typography.bodyMedium)
            }
            BulletList(R.string.content_symptoms, content.symptoms)
            BulletList(R.string.content_pros, content.pros)
            BulletList(R.string.content_cons, content.cons)
        }
    }
}

@Composable
private fun BulletList(title: Int, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        items.forEach { Text(stringResource(R.string.content_bullet, it), style = MaterialTheme.typography.bodyMedium) }
    }
}
