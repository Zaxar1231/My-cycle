package io.github.zaxar1231.mycycle.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.insights.Hormone
import io.github.zaxar1231.mycycle.core.domain.insights.HormoneLevel
import io.github.zaxar1231.mycycle.core.domain.insights.HormoneModel
import io.github.zaxar1231.mycycle.core.ui.component.ExpandablePills
import io.github.zaxar1231.mycycle.core.ui.component.Pill
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Composable
internal fun HormonesContent(now: CycleNow, padding: PaddingValues) {
    val length = now.segment.length
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HormoneChart(now)
                Legend()
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.hormones_now, now.cycleDay), style = MaterialTheme.typography.titleMedium)
                val pills = Hormone.entries.map { hormone ->
                    val level = HormoneModel.levelOf(HormoneModel.level(hormone, now.cycleDay, length, now.ovulationDay))
                    Pill(hormone.name, stringResource(R.string.insights_pill, stringResource(hormone.nameRes()), stringResource(level.labelRes()))) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(hormone.roleRes()), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(hormone.nowRes(now.phase)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                ExpandablePills(pills, Modifier.padding(top = 12.dp))
            }
        }
        Text(stringResource(R.string.hormones_model_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(UiR.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall)
    }
}

/** All five curves over the user's cycle, with a vertical "you are here" line on today. */
@Composable
private fun HormoneChart(now: CycleNow) {
    val length = now.segment.length
    val colors = Hormone.entries.associateWith { it.color() }
    val curves = Hormone.entries.associateWith { HormoneModel.curve(it, length, now.ovulationDay) }
    val measurer = rememberTextMeasurer()
    val markerColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = markerColor)
    val marker = stringResource(R.string.insights_you_are_here, now.cycleDay)
    val description = stringResource(R.string.hormones_chart_description)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        val label = measurer.measure(marker, labelStyle)
        val top = label.size.height + LABEL_GAP.toPx()
        val bottom = size.height
        fun x(day: Int) = if (length <= 1) 0f else size.width * (day - 1) / (length - 1)
        fun y(level: Double) = bottom - (level * (bottom - top)).toFloat()

        drawLine(gridColor, Offset(0f, bottom), Offset(size.width, bottom))
        curves.forEach { (hormone, values) ->
            val path = Path()
            values.forEachIndexed { index, value ->
                if (index == 0) path.moveTo(x(1), y(value)) else path.lineTo(x(index + 1), y(value))
            }
            val dashed = hormone == Hormone.FSH || hormone == Hormone.TESTOSTERONE
            drawPath(
                path,
                colors.getValue(hormone),
                style = Stroke(
                    width = LINE_WIDTH.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx())) else null,
                ),
            )
        }
        val markerX = x(now.cycleDay)
        drawLine(markerColor, Offset(markerX, top), Offset(markerX, bottom), strokeWidth = MARKER_WIDTH.toPx())
        val labelX = (markerX - label.size.width / 2).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
        drawText(label, topLeft = Offset(labelX, 0f))
    }
}

@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hormone.entries.forEach { hormone ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(LEGEND_DOT).background(hormone.color(), CircleShape))
                Text(stringResource(hormone.nameRes()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun Hormone.color(): Color = when (this) {
    Hormone.ESTRADIOL -> MaterialTheme.colorScheme.primary
    Hormone.PROGESTERONE -> MaterialTheme.colorScheme.secondary
    Hormone.LH -> MaterialTheme.colorScheme.tertiary
    Hormone.FSH -> MaterialTheme.colorScheme.outline
    Hormone.TESTOSTERONE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun Hormone.nameRes() = when (this) {
    Hormone.ESTRADIOL -> R.string.hormone_estradiol
    Hormone.PROGESTERONE -> R.string.hormone_progesterone
    Hormone.LH -> R.string.hormone_lh
    Hormone.FSH -> R.string.hormone_fsh
    Hormone.TESTOSTERONE -> R.string.hormone_testosterone
}

private fun Hormone.roleRes() = when (this) {
    Hormone.ESTRADIOL -> R.string.hormone_estradiol_role
    Hormone.PROGESTERONE -> R.string.hormone_progesterone_role
    Hormone.LH -> R.string.hormone_lh_role
    Hormone.FSH -> R.string.hormone_fsh_role
    Hormone.TESTOSTERONE -> R.string.hormone_testosterone_role
}

private fun Hormone.nowRes(phase: CyclePhase): Int = when (this) {
    Hormone.ESTRADIOL -> when (phase) {
        CyclePhase.MENSTRUAL -> R.string.hormone_now_estradiol_menstrual
        CyclePhase.FOLLICULAR -> R.string.hormone_now_estradiol_follicular
        CyclePhase.OVULATORY -> R.string.hormone_now_estradiol_ovulatory
        CyclePhase.LUTEAL -> R.string.hormone_now_estradiol_luteal
    }
    Hormone.PROGESTERONE -> when (phase) {
        CyclePhase.MENSTRUAL -> R.string.hormone_now_progesterone_menstrual
        CyclePhase.FOLLICULAR -> R.string.hormone_now_progesterone_follicular
        CyclePhase.OVULATORY -> R.string.hormone_now_progesterone_ovulatory
        CyclePhase.LUTEAL -> R.string.hormone_now_progesterone_luteal
    }
    Hormone.LH -> when (phase) {
        CyclePhase.MENSTRUAL -> R.string.hormone_now_lh_menstrual
        CyclePhase.FOLLICULAR -> R.string.hormone_now_lh_follicular
        CyclePhase.OVULATORY -> R.string.hormone_now_lh_ovulatory
        CyclePhase.LUTEAL -> R.string.hormone_now_lh_luteal
    }
    Hormone.FSH -> when (phase) {
        CyclePhase.MENSTRUAL -> R.string.hormone_now_fsh_menstrual
        CyclePhase.FOLLICULAR -> R.string.hormone_now_fsh_follicular
        CyclePhase.OVULATORY -> R.string.hormone_now_fsh_ovulatory
        CyclePhase.LUTEAL -> R.string.hormone_now_fsh_luteal
    }
    Hormone.TESTOSTERONE -> when (phase) {
        CyclePhase.MENSTRUAL -> R.string.hormone_now_testosterone_menstrual
        CyclePhase.FOLLICULAR -> R.string.hormone_now_testosterone_follicular
        CyclePhase.OVULATORY -> R.string.hormone_now_testosterone_ovulatory
        CyclePhase.LUTEAL -> R.string.hormone_now_testosterone_luteal
    }
}

private fun HormoneLevel.labelRes() = when (this) {
    HormoneLevel.LOW -> R.string.hormone_level_low
    HormoneLevel.MEDIUM -> R.string.hormone_level_medium
    HormoneLevel.HIGH -> R.string.hormone_level_high
}

private val CHART_HEIGHT = 200.dp
private val LABEL_GAP = 4.dp
private val LINE_WIDTH = 2.5.dp
private val MARKER_WIDTH = 1.5.dp
private val DASH = 6.dp
private val LEGEND_DOT = 10.dp
