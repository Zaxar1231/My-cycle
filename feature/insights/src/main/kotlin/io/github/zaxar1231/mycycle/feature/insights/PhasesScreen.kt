package io.github.zaxar1231.mycycle.feature.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.ui.component.ExpandablePills
import io.github.zaxar1231.mycycle.core.ui.component.Pill
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import io.github.zaxar1231.mycycle.core.ui.theme.LocalCycleColors
import io.github.zaxar1231.mycycle.core.ui.R as UiR

/** Each phase of the user's current cycle: its days, what happens and where the user is now. */
@Composable
internal fun PhasesContent(now: CycleNow, content: Map<CyclePhase, PhaseContent>, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.insights_you_are_here, now.cycleDay), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(now.phase.labelRes()), style = MaterialTheme.typography.bodyLarge)
                PhaseBar(now)
            }
        }
        now.ranges.forEach { (phase, days) -> PhaseCard(phase, days, phase == now.phase, content[phase]) }
        Text(stringResource(UiR.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall)
    }
}

/** The cycle as one bar split into phases, with a mark on today. */
@Composable
private fun PhaseBar(now: CycleNow) {
    val colors = now.ranges.keys.associateWith { it.color() }
    val marker = MaterialTheme.colorScheme.onSurface
    val description = stringResource(R.string.phases_bar_description)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT + MARKER_OVERHANG * 2)
            .semantics { contentDescription = description },
    ) {
        val length = now.segment.length
        val overhang = MARKER_OVERHANG.toPx()
        val barHeight = size.height - overhang * 2
        val dayWidth = size.width / length
        now.ranges.forEach { (phase, days) ->
            drawRoundRect(
                color = colors.getValue(phase),
                topLeft = Offset((days.first - 1) * dayWidth, overhang),
                size = Size(days.count() * dayWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2),
            )
        }
        val x = (now.cycleDay - 0.5f) * dayWidth
        drawLine(marker, Offset(x, 0f), Offset(x, size.height), strokeWidth = MARKER_WIDTH.toPx())
    }
}

@Composable
private fun PhaseCard(phase: CyclePhase, days: IntRange, isCurrent: Boolean, content: PhaseContent?) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(phase.labelRes()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isCurrent) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text(
                            stringResource(R.string.insights_current),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            val range = if (days.first == days.last) {
                stringResource(R.string.insights_day, days.first)
            } else {
                stringResource(R.string.insights_days, days.first, days.last)
            }
            Text(range, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content?.about?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            content?.let { ExpandablePills(contentPills(it), resetKey = phase) }
        }
    }
    if (isCurrent) {
        OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) { body() }
    } else {
        Card(Modifier.fillMaxWidth()) { body() }
    }
}

@Composable
private fun contentPills(content: PhaseContent): List<Pill> = buildList {
    listPill("symptoms", R.string.insights_symptoms, content.symptoms, heading = R.string.insights_symptoms_heading)
    listPill("pros", R.string.insights_pros, content.pros)
    listPill("cons", R.string.insights_cons, content.cons)
    listPill("tips", R.string.insights_tips, content.tips)
}

@Composable
private fun MutableList<Pill>.listPill(key: String, title: Int, items: List<String>, heading: Int? = null) {
    if (items.isEmpty()) return
    add(
        Pill(key, stringResource(title)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                heading?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge) }
                items.forEach { Text(stringResource(R.string.insights_bullet, it), style = MaterialTheme.typography.bodyMedium) }
            }
        },
    )
}

@Composable
private fun CyclePhase.color(): Color {
    val colors = LocalCycleColors.current
    return when (this) {
        CyclePhase.MENSTRUAL -> colors.period
        CyclePhase.FOLLICULAR -> colors.follicular
        CyclePhase.OVULATORY -> colors.ovulation
        CyclePhase.LUTEAL -> colors.luteal
    }
}

private val BAR_HEIGHT = 20.dp
private val MARKER_OVERHANG = 6.dp
private val MARKER_WIDTH = 3.dp
