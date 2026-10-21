package io.github.zaxar1231.mycycle.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.theme.Tones
import io.github.zaxar1231.mycycle.core.ui.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A colour wheel: the angle picks the hue, the distance from the centre the saturation, and the
 * slider below the brightness. [onChange] gets the picked colour as ARGB on every move.
 */
@Composable
fun ColorWheel(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val initial = remember { Tones.toHsv(color) }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var saturation by remember { mutableFloatStateOf(initial.second) }
    var value by remember { mutableFloatStateOf(initial.third.coerceAtLeast(MIN_VALUE)) }
    val emit by rememberUpdatedState(onChange)
    val description = stringResource(R.string.color_wheel_description)

    fun pick(position: Offset, size: Float) {
        val center = size / 2
        val dx = position.x - center
        val dy = position.y - center
        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + FULL_TURN) % FULL_TURN).toFloat()
        saturation = (hypot(dx, dy) / center).coerceIn(0f, 1f)
        emit(Tones.fromHsv(hue, saturation, value))
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier
                .widthIn(max = MAX_WHEEL)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = description }
                .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat()) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position, size.width.toFloat()) } },
        ) {
            val radius = size.minDimension / 2
            val hues = List(HUE_STOPS + 1) { Color(Tones.fromHsv(it * FULL_TURN.toFloat() / HUE_STOPS, 1f, value)) }
            drawCircle(Brush.sweepGradient(hues, center), radius)
            drawCircle(Brush.radialGradient(listOf(Color(Tones.fromHsv(0f, 0f, value)), Color.Transparent), center, radius), radius)
            val angle = Math.toRadians(hue.toDouble())
            val knob = Offset(center.x + (cos(angle) * saturation * radius).toFloat(), center.y + (sin(angle) * saturation * radius).toFloat())
            drawCircle(Color(Tones.fromHsv(hue, saturation, value)), KNOB_RADIUS.toPx(), knob)
            drawCircle(Color.White, KNOB_RADIUS.toPx(), knob, style = Stroke(KNOB_RING.toPx()))
            drawCircle(Color.Black.copy(alpha = KNOB_SHADOW_ALPHA), KNOB_RADIUS.toPx() + KNOB_RING.toPx(), knob, style = Stroke(1.dp.toPx()))
        }
        Text(stringResource(R.string.color_wheel_brightness), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = {
                value = it
                emit(Tones.fromHsv(hue, saturation, value))
            },
            valueRange = MIN_VALUE..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val MAX_WHEEL = 280.dp
private val KNOB_RADIUS = 12.dp
private val KNOB_RING = 3.dp
private const val KNOB_SHADOW_ALPHA = 0.4f
private const val HUE_STOPS = 12
private const val FULL_TURN = 360.0
/** Very dark seeds give muddy schemes; the scheme sets its own lightness anyway. */
private const val MIN_VALUE = 0.3f
