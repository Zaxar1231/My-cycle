package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.DayKind
import io.github.zaxar1231.mycycle.core.ui.theme.LocalCycleColors

/**
 * Charts drawn on a Canvas: no chart library, colors come from the theme. Each chart takes a
 * [description] that screen readers announce instead of the drawing.
 */

/** A row of small rounded "pills", one per cycle day, colored by what the day was. */
@Composable
fun CycleStrip(days: List<DayKind>, elapsedDays: Int, description: String, modifier: Modifier = Modifier) {
    val colors = LocalCycleColors.current
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        if (days.isEmpty()) return@Canvas
        val gap = STRIP_GAP.toPx()
        val width = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { index, kind ->
            val base = when (kind) {
                DayKind.PERIOD -> colors.period
                DayKind.PREDICTED_PERIOD -> colors.predictedPeriod
                DayKind.FERTILE -> colors.fertile
                DayKind.OVULATION -> colors.ovulation
                DayKind.LUTEAL, DayKind.OTHER -> empty
            }
            // Days of the current cycle that have not come yet are drawn faded.
            val color = if (index >= elapsedDays) base.copy(alpha = base.alpha * FUTURE_ALPHA) else base
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(width / 2, width / 2),
            )
        }
    }
}

/** Vertical bars with the value above each bar and a label below. */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = measurer.measure("0", textStyle).size.height.toFloat()
        val top = labelHeight + LABEL_PADDING.toPx()
        val bottom = size.height - labelHeight - LABEL_PADDING.toPx()
        val max = values.max().coerceAtLeast(1)
        val slot = size.width / values.size
        val barWidth = slot * BAR_FILL
        values.forEachIndexed { index, value ->
            val height = (bottom - top) * value / max
            val left = index * slot + (slot - barWidth) / 2
            drawRoundRect(barColor, Offset(left, bottom - height), Size(barWidth, height), CornerRadius(BAR_CORNER.toPx()))
            val valueText = measurer.measure(value.toString(), textStyle)
            drawText(valueText, topLeft = Offset(left + (barWidth - valueText.size.width) / 2, bottom - height - valueText.size.height - LABEL_PADDING.toPx() / 2))
            val label = measurer.measure(labels.getOrElse(index) { "" }, textStyle)
            if (label.size.width <= slot) {
                drawText(label, topLeft = Offset(index * slot + (slot - label.size.width) / 2, bottom + LABEL_PADDING.toPx()))
            }
        }
    }
}

/**
 * A line through [values] spaced by [positions] (for example epoch days, so gaps between dates show),
 * with the lowest and highest values labelled on the left and [startLabel] / [endLabel] below.
 */
@Composable
fun LineChart(
    positions: List<Long>,
    values: List<Double>,
    format: (Double) -> String,
    startLabel: String,
    endLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val minText = measurer.measure(format(min), textStyle)
        val maxText = measurer.measure(format(max), textStyle)
        val axisWidth = maxOf(minText.size.width, maxText.size.width) + LABEL_PADDING.toPx()
        val bottomLabels = measurer.measure(startLabel, textStyle).size.height + LABEL_PADDING.toPx()
        val top = maxText.size.height / 2f
        val bottom = size.height - bottomLabels
        val left = axisWidth + DOT_RADIUS.toPx()
        val right = size.width - DOT_RADIUS.toPx()
        val first = positions.first()
        val span = (positions.last() - first).coerceAtLeast(1)
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        fun x(position: Long) = if (positions.size == 1) (left + right) / 2 else left + (right - left) * (position - first) / span
        fun y(value: Double) = if (max == min) (top + bottom) / 2 else bottom - ((value - min) / range * (bottom - top)).toFloat()

        drawLine(gridColor, Offset(left, y(max)), Offset(right, y(max)))
        drawLine(gridColor, Offset(left, y(min)), Offset(right, y(min)))
        drawText(maxText, topLeft = Offset(0f, y(max) - maxText.size.height / 2))
        if (max != min) drawText(minText, topLeft = Offset(0f, y(min) - minText.size.height / 2))

        val path = Path()
        positions.zip(values).forEachIndexed { index, (position, value) ->
            val point = Offset(x(position), y(value))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            drawCircle(lineColor, DOT_RADIUS.toPx(), point)
        }
        drawPath(path, lineColor, style = Stroke(width = LINE_WIDTH.toPx()))

        val start = measurer.measure(startLabel, textStyle)
        val end = measurer.measure(endLabel, textStyle)
        drawText(start, topLeft = Offset(left, size.height - start.size.height))
        if (positions.size > 1 && end.size.width + start.size.width < right - left) {
            drawText(end, topLeft = Offset(right - end.size.width, size.height - end.size.height))
        }
    }
}

/** A labelled horizontal bar whose length is [value] relative to [max]. */
@Composable
fun HorizontalBar(label: String, value: Int, max: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(BAR_HEIGHT)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.toFloat() / max.coerceAtLeast(1))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(BAR_HEIGHT)),
            )
        }
    }
}

private val STRIP_HEIGHT = 18.dp
private val STRIP_GAP = 2.dp
private const val FUTURE_ALPHA = 0.35f
private val CHART_HEIGHT = 160.dp
private val LABEL_PADDING = 4.dp
private const val BAR_FILL = 0.6f
private val BAR_CORNER = 4.dp
private val BAR_HEIGHT = 8.dp
private val DOT_RADIUS = 3.dp
private val LINE_WIDTH = 2.dp
