package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.model.HomeStyle
import io.github.zaxar1231.mycycle.core.domain.theme.MoonPhase
import io.github.zaxar1231.mycycle.core.ui.theme.TwilightThemeExtras
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Day numbers of the current cycle, for the pictures. */
internal class CycleShape(val cycleDay: Int, val length: Int, val periodLength: Int, val fertileStart: Int, val ovulation: Int)

internal fun CyclePrediction.shapeOn(today: LocalDate): CycleShape? {
    val status = this.today ?: return null
    // The recorded cycle, not the predicted one that starts today when the period is due or late.
    val segment = timeline.lastOrNull { !it.isPredicted && !today.isBefore(it.start) } ?: segmentFor(today) ?: return null
    fun dayOf(date: LocalDate) = ChronoUnit.DAYS.between(segment.start, date).toInt() + 1
    // A late period stretches the cycle to today.
    val length = maxOf(segment.length, status.cycleDay)
    return CycleShape(
        cycleDay = status.cycleDay,
        length = length,
        periodLength = segment.periodLength.coerceAtMost(length),
        fertileStart = dayOf(segment.fertileStart).coerceIn(1, length),
        ovulation = dayOf(segment.ovulation).coerceIn(1, length),
    )
}

/**
 * The top of the home screen in the style the user picked. Every style shows the same facts:
 * the cycle day and phase, the next period, the fertile window and the chance to conceive.
 */
@Composable
fun CycleHero(
    style: HomeStyle,
    prediction: CyclePrediction,
    today: LocalDate,
    onEditPeriod: () -> Unit,
    onPeriodStarted: () -> Unit,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (style == HomeStyle.RING) {
        RingHero(prediction, today, onEditPeriod, onPeriodStarted, onOpenTheme, modifier)
        return
    }
    val pages = heroPages(prediction, today)
    val shape = prediction.shapeOn(today)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { PaletteButton(onOpenTheme) }
        if (pages.isEmpty()) {
            EmptyHero(prediction, onPeriodStarted)
            return@Column
        }
        when {
            style == HomeStyle.MOSAIC -> MosaicHero(pages)
            // Without a cycle to draw, the facts alone.
            shape == null -> {
                DayTitle(pages)
                Spacer(Modifier.height(AIR))
                HeroFacts(pages)
            }
            style == HomeStyle.MOON -> MoonHero(shape, pages)
            style == HomeStyle.ORBIT -> OrbitHero(shape, pages)
            else -> WaveHero(shape, pages)
        }
        Spacer(Modifier.height(AIR))
        HeroAction(prediction, onEditPeriod, onPeriodStarted)
    }
}

@Composable
private fun EmptyHero(prediction: CyclePrediction, onPeriodStarted: () -> Unit) {
    Column(
        Modifier.padding(vertical = AIR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (prediction.today == null && prediction.timeline.isNotEmpty()) {
            Text(stringResource(R.string.today_cycle_in_future), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.today_no_data_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Button(onClick = onPeriodStarted) { Text(stringResource(R.string.today_period_started)) }
        }
    }
}

/** When the period is due or late, marking its start is the main action. */
@Composable
private fun HeroAction(prediction: CyclePrediction, onEditPeriod: () -> Unit, onPeriodStarted: () -> Unit) {
    val status = prediction.today ?: return
    if (status.daysLate > 0 || status.daysUntilNextPeriod == 0) {
        Button(onClick = onPeriodStarted) { Text(stringResource(R.string.hero_period_started)) }
    } else {
        OutlinedButton(onClick = onEditPeriod) { Text(stringResource(R.string.hero_edit_period)) }
    }
}

/** The cycle day in large light type with the phase under it. */
@Composable
private fun DayTitle(pages: List<HeroPage>) {
    val day = pages.firstOrNull { it.kind == PageKind.CYCLE_DAY } ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.hero_day_title, day.value),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
            textAlign = TextAlign.Center,
        )
        day.note?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** The other facts as quiet rows: a number in the accent colour and what it means. */
@Composable
private fun HeroFacts(pages: List<HeroPage>) {
    val facts = pages.filter { it.kind != PageKind.CYCLE_DAY }
    Column(Modifier.fillMaxWidth()) {
        facts.forEachIndexed { index, page ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    page.value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = FACT_VALUE_WIDTH),
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(page.label, style = MaterialTheme.typography.bodyLarge)
                    page.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

// ---------- Moon ----------

@Composable
private fun MoonHero(shape: CycleShape, pages: List<HeroPage>) {
    val moon = MoonPhase.of(shape.cycleDay, shape.length, shape.ovulation)
    val lit = MaterialTheme.colorScheme.primary
    val shadow = MaterialTheme.colorScheme.surfaceContainerHighest
    val star = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.hero_moon_description, shape.cycleDay, shape.length)
    val stars = remember { Random(STAR_SEED).let { r -> List(STAR_COUNT) { Offset(r.nextFloat(), r.nextFloat()) to r.nextFloat() } } }
    Box(Modifier.fillMaxWidth().height(MOON_AREA).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(MOON_AREA)) {
            stars.forEach { (position, brightness) ->
                val p = Offset(position.x * size.width, position.y * size.height)
                // Stars stay out of the moon itself.
                if ((p - center).getDistance() > MOON_SIZE.toPx() * STAR_CLEARANCE) {
                    drawCircle(star.copy(alpha = STAR_ALPHA * (HALF + brightness / 2)), (1 + brightness * 1.5f).dp.toPx(), p)
                }
            }
        }
        Canvas(Modifier.size(MOON_SIZE)) { drawMoon(moon, lit, shadow) }
    }
    Spacer(Modifier.height(AIR))
    DayTitle(pages)
    Spacer(Modifier.height(AIR))
    HeroFacts(pages)
}

/** A disc in [shadow] with the lit part in [lit]; the terminator is half an ellipse. */
private fun DrawScope.drawMoon(moon: MoonPhase, lit: Color, shadow: Color) {
    val r = size.minDimension / 2
    drawCircle(Brush.radialGradient(listOf(lit.copy(alpha = GLOW_ALPHA), Color.Transparent), center, r * GLOW_SCALE), r * GLOW_SCALE)
    drawCircle(shadow, r)
    val k = moon.illumination
    if (k < MIN_LIT) return
    val rx = r * abs(2 * k - 1)
    val path = Path().apply {
        // The lit right half, from the top down, then back up along the terminator.
        arcTo(Rect(center, r), -90f, 180f, forceMoveTo = true)
        arcTo(Rect(center.x - rx, center.y - r, center.x + rx, center.y + r), 90f, if (k > HALF) 180f else -180f, forceMoveTo = false)
        close()
    }
    // Waning moons are lit from the other side.
    scale(scaleX = if (moon.waxing) 1f else -1f, scaleY = 1f, pivot = center) { drawPath(path, lit) }
}

// ---------- Orbit ----------

@Composable
private fun OrbitHero(shape: CycleShape, pages: List<HeroPage>) {
    val colors = TwilightThemeExtras.cycleColors
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.hero_moon_description, shape.cycleDay, shape.length)
    Box(Modifier.widthIn(max = ORBIT_MAX).fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val r = size.minDimension / 2 - ORBIT_INSET.toPx()
            val stroke = ORBIT_STROKE.toPx()
            fun angleOf(day: Float) = -90f + 360f * (day - 1) / shape.length
            drawCircle(track, r, style = Stroke(1.dp.toPx()))
            val box = Rect(center, r)
            // Day d covers the arc from d-1 to d.
            drawArc(colors.period, angleOf(1f), 360f * shape.periodLength / shape.length, false, box.topLeft, box.size, style = Stroke(stroke, cap = StrokeCap.Round))
            val fertileDays = shape.ovulation - shape.fertileStart + 1
            drawArc(colors.ovulation.copy(alpha = FERTILE_ALPHA), angleOf(shape.fertileStart.toFloat()), 360f * fertileDays / shape.length, false, box.topLeft, box.size, style = Stroke(stroke, cap = StrokeCap.Round))
            drawPlanet(angleOf(shape.ovulation + HALF), r, colors.ovulation, ORBIT_MARK.toPx())
            val now = angleOf(shape.cycleDay + HALF)
            drawPlanet(now, r, accent.copy(alpha = GLOW_ALPHA), ORBIT_GLOW.toPx())
            drawPlanet(now, r, accent, ORBIT_TODAY.toPx())
        }
        DayTitle(pages)
    }
    Spacer(Modifier.height(AIR))
    HeroFacts(pages)
}

private fun DrawScope.drawPlanet(angleDegrees: Float, orbit: Float, color: Color, radius: Float) {
    val a = Math.toRadians(angleDegrees.toDouble())
    drawCircle(color, radius, Offset(center.x + (cos(a) * orbit).toFloat(), center.y + (sin(a) * orbit).toFloat()))
}

// ---------- Wave ----------

@Composable
private fun WaveHero(shape: CycleShape, pages: List<HeroPage>) {
    val colors = TwilightThemeExtras.cycleColors
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.hero_moon_description, shape.cycleDay, shape.length)
    Column(Modifier.fillMaxWidth()) {
        Text(
            pages.first { it.kind == PageKind.CYCLE_DAY }.value,
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light),
        )
        Text(
            stringResource(R.string.hero_cycle_day_phase, stringResource(R.string.hero_cycle_day), pages.first { it.kind == PageKind.CYCLE_DAY }.note.orEmpty()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AIR))
        Canvas(Modifier.fillMaxWidth().height(WAVE_HEIGHT).semantics { contentDescription = description }) {
            val top = WAVE_PAD.toPx()
            val bottom = size.height - WAVE_PAD.toPx()
            fun x(day: Float) = size.width * (day - 1) / (shape.length - 1).coerceAtLeast(1)
            fun y(day: Float) = bottom - (bottom - top) * MoonPhase.of(day.toInt().coerceAtLeast(1), shape.length, shape.ovulation).illumination
            // Period and fertile days as soft bands under the wave.
            drawRect(colors.period.copy(alpha = BAND_ALPHA), Offset(x(1f), 0f), Size(x(shape.periodLength.toFloat()) - x(1f) + BAND_MIN, size.height))
            drawRect(colors.ovulation.copy(alpha = BAND_ALPHA), Offset(x(shape.fertileStart.toFloat()), 0f), Size(x(shape.ovulation.toFloat()) - x(shape.fertileStart.toFloat()) + BAND_MIN, size.height))
            val wave = Path().apply {
                moveTo(x(1f), y(1f))
                for (day in 2..shape.length) lineTo(x(day.toFloat()), y(day.toFloat()))
            }
            drawPath(wave, accent, style = Stroke(WAVE_STROKE.toPx(), cap = StrokeCap.Round))
            val today = Offset(x(shape.cycleDay.toFloat()), y(shape.cycleDay.toFloat()))
            drawLine(onSurface.copy(alpha = BAND_ALPHA), Offset(today.x, 0f), Offset(today.x, size.height), 1.dp.toPx())
            drawCircle(accent.copy(alpha = GLOW_ALPHA), ORBIT_GLOW.toPx(), today)
            drawCircle(accent, ORBIT_TODAY.toPx(), today)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("1", style = MaterialTheme.typography.labelMedium, color = onSurface)
            Spacer(Modifier.weight(1f))
            Text(shape.length.toString(), style = MaterialTheme.typography.labelMedium, color = onSurface)
        }
        Spacer(Modifier.height(AIR))
        HeroFacts(pages)
    }
}

// ---------- Mosaic ----------

@Composable
private fun MosaicHero(pages: List<HeroPage>) {
    val day = pages.first { it.kind == PageKind.CYCLE_DAY }
    val others = pages.filter { it.kind != PageKind.CYCLE_DAY }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        Tile(day, large = true, modifier = Modifier.fillMaxWidth())
        others.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                row.forEach { Tile(it, large = false, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(page: HeroPage, large: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(TILE_CORNER), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                page.value,
                style = (if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge).copy(fontWeight = FontWeight.Light),
                color = if (large) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
            Text(page.label, style = MaterialTheme.typography.bodyLarge)
            page.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Generous side margins: the home screen breathes. */
internal val SIDE = 24.dp
internal val AIR = 28.dp
private val FACT_VALUE_WIDTH = 64.dp
private const val DIVIDER_ALPHA = 0.5f
private val MOON_AREA = 260.dp
private val MOON_SIZE = 180.dp
private const val STAR_COUNT = 18
private const val STAR_SEED = 7
private const val STAR_ALPHA = 0.6f
private const val STAR_CLEARANCE = 0.75f
private const val HALF = 0.5f
private const val GLOW_ALPHA = 0.18f
private const val GLOW_SCALE = 1.35f
private const val MIN_LIT = 0.01f
private val ORBIT_MAX = 320.dp
private val ORBIT_INSET = 20.dp
private val ORBIT_STROKE = 6.dp
private val ORBIT_MARK = 5.dp
private val ORBIT_TODAY = 9.dp
private val ORBIT_GLOW = 18.dp
private val WAVE_HEIGHT = 140.dp
private val WAVE_PAD = 16.dp
private val WAVE_STROKE = 3.dp
private const val BAND_ALPHA = 0.35f
/** Fertile days in the ovulation colour: the container tone is too close to the background for a thin line. */
private const val FERTILE_ALPHA = 0.7f
private const val BAND_MIN = 4f
private val TILE_GAP = 12.dp
private val TILE_CORNER = 28.dp
