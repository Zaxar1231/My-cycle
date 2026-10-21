package io.github.zaxar1231.mycycle.core.ui.component

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
import io.github.zaxar1231.mycycle.core.ui.R
import java.time.LocalDate

/**
 * What many people notice in this phase. Each group (tip of the day, possible symptoms, upsides,
 * downsides) is a pill; tapping it opens the details below, tapping again closes them.
 */
@Composable
fun PhaseContentCard(content: PhaseContent, day: LocalDate, modifier: Modifier = Modifier) {
    val pills = buildList {
        content.tipFor(day.dayOfYear)?.let { tip ->
            add(Pill("tip", stringResource(R.string.content_tip)) { Text(tip, style = MaterialTheme.typography.bodyMedium) })
        }
        listGroup("symptoms", R.string.content_symptoms, content.symptoms, heading = R.string.content_symptoms_heading)
        listGroup("pros", R.string.content_pros, content.pros)
        listGroup("cons", R.string.content_cons, content.cons)
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(content.phase.labelRes()), style = MaterialTheme.typography.titleMedium)
            ExpandablePills(pills, Modifier.padding(top = 12.dp), resetKey = content.phase)
        }
    }
}

@Composable
private fun MutableList<Pill>.listGroup(key: String, title: Int, items: List<String>, heading: Int? = null) {
    if (items.isEmpty()) return
    add(
        Pill(key, stringResource(R.string.content_pill_count, stringResource(title), items.size)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                heading?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge) }
                items.forEach { Text(stringResource(R.string.content_bullet, it), style = MaterialTheme.typography.bodyMedium) }
            }
        },
    )
}
