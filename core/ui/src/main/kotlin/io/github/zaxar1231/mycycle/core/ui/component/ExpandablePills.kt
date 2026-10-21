package io.github.zaxar1231.mycycle.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One pill: a short [label] and the [details] it opens. [key] must be stable and unique in its row. */
class Pill(val key: String, val label: String, val details: @Composable () -> Unit)

/**
 * A row of pills; tapping one opens its details below, tapping it again closes them. The open pill
 * survives rotation and is reset when [resetKey] changes.
 */
@Composable
fun ExpandablePills(pills: List<Pill>, modifier: Modifier = Modifier, resetKey: Any? = null) {
    var openKey by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    val open = pills.firstOrNull { it.key == openKey }
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pills.forEach { pill ->
                FilterChip(
                    selected = pill == open,
                    onClick = { openKey = if (pill == open) null else pill.key },
                    label = { Text(pill.label) },
                )
            }
        }
        // Keyed by the string: pills are rebuilt on every recomposition and would restart the animation.
        AnimatedContent(
            targetState = open?.key,
            transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) },
            label = "pill-details",
        ) { key ->
            val pill = pills.firstOrNull { it.key == key }
            // The gap lives here: closed details take no room at all.
            if (pill != null) Column(Modifier.fillMaxWidth().padding(top = 12.dp)) { pill.details() }
        }
    }
}
