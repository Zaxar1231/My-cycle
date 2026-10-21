package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import java.time.LocalDate

/** Groups of phase content, each behind its own pill. */
private enum class ContentGroup(val title: Int) {
    TIP(R.string.content_tip),
    SYMPTOMS(R.string.content_symptoms),
    PROS(R.string.content_pros),
    CONS(R.string.content_cons),
}

/**
 * What many people notice in this phase. Each group (tip of the day, possible symptoms, upsides,
 * downsides) is a pill; tapping it opens the details below, tapping again closes them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhaseContentCard(content: PhaseContent, day: LocalDate, modifier: Modifier = Modifier) {
    val tip = content.tipFor(day.dayOfYear)
    val groups = ContentGroup.entries.associateWith { group ->
        when (group) {
            ContentGroup.TIP -> listOfNotNull(tip)
            ContentGroup.SYMPTOMS -> content.symptoms
            ContentGroup.PROS -> content.pros
            ContentGroup.CONS -> content.cons
        }
    }.filterValues { it.isNotEmpty() }
    // Saved by name so the open pill survives rotation; reset when the phase changes.
    var openName by rememberSaveable(content.phase) { mutableStateOf<String?>(null) }
    val open = openName?.let { name -> groups.keys.firstOrNull { it.name == name } }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(content.phase.labelRes()), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (group, items) ->
                    FilterChip(
                        selected = group == open,
                        onClick = { openName = if (group == open) null else group.name },
                        label = {
                            val title = stringResource(group.title)
                            Text(if (group == ContentGroup.TIP) title else stringResource(R.string.content_pill_count, title, items.size))
                        },
                    )
                }
            }
            AnimatedContent(
                targetState = open,
                transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) },
                label = "phase-content",
            ) { group ->
                group?.let { g -> groups[g]?.let { Details(g, it) } }
            }
        }
    }
}

@Composable
private fun Details(group: ContentGroup, items: List<String>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (group == ContentGroup.TIP) {
            Text(items.first(), style = MaterialTheme.typography.bodyMedium)
        } else {
            items.forEach { Text(stringResource(R.string.content_bullet, it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
