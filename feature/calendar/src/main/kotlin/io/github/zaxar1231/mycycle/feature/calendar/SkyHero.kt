package io.github.zaxar1231.mycycle.feature.calendar

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.theme.MoonPhase
import io.github.zaxar1231.mycycle.core.ui.theme.TwilightThemeExtras
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/** Sky colours from the top to the horizon, and how many stars and how much cloud show. */
private class SkyPalette(val top: Color, val middle: Color, val horizon: Color, val stars: Float, val clouds: Float)

/**
 * Each phase has its own sky: a rose dusk during the period, dawn in the follicular phase,
 * a clear day with a heart-shaped cloud around ovulation and violet twilight in the luteal phase.
 */
private fun paletteOf(phase: CyclePhase?) = when (phase) {
    CyclePhase.MENSTRUAL -> SkyPalette(Color(0xFF2A0F2E), Color(0xFF6A2143), Color(0xFFE07A86), stars = 0.7f, clouds = 0.35f)
    CyclePhase.FOLLICULAR -> SkyPalette(Color(0xFF15294D), Color(0xFF4572A8), Color(0xFFF3BBCB), stars = 0.35f, clouds = 0.5f)
    CyclePhase.OVULATORY -> SkyPalette(Color(0xFF0F3B68), Color(0xFF3F8FD0), Color(0xFFC4E5FF), stars = 0f, clouds = 0.5f)
    CyclePhase.LUTEAL, null -> SkyPalette(Color(0xFF110C2D), Color(0xFF3C2A6C), Color(0xFFB48CD8), stars = 1f, clouds = 0.3f)
}

/** Positions are fractions of the scene, made once so the picture does not jump on recomposition. */
private class SceneLayout(seed: Int) {
    private val random = Random(seed)
    val stars = List(STAR_COUNT) { Triple(random.nextFloat(), random.nextFloat() * STAR_BAND, random.nextFloat()) }
    val clouds = List(CLOUD_COUNT) { Triple(random.nextFloat(), CLOUD_TOP + random.nextFloat() * CLOUD_BAND, 0.7f + random.nextFloat() * 0.6f) }
    val forests = List(FOREST_LAYERS) { layer -> pines(Random(seed + layer + 1)) }

    /** Trees of one layer as (x start, width, height) fractions. */
    private fun pines(r: Random): List<Triple<Float, Float, Float>> {
        val trees = mutableListOf<Triple<Float, Float, Float>>()
        var x = -0.05f
        while (x < 1.05f) {
            val width = 0.05f + r.nextFloat() * 0.05f
            trees += Triple(x, width, 0.45f + r.nextFloat() * 0.55f)
            x += width * 0.62f
        }
        return trees
    }
}

/**
 * The "Twilight sky" home picture: an illustrated scene that follows the cycle. The moon waxes
 * and wanes with it, clouds drift, stars twinkle, pine forests stand on the horizon, and the
 * whole cycle is a path of lights across the sky with today glowing on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkyHero(
    prediction: CyclePrediction,
    today: LocalDate,
    onEditPeriod: () -> Unit,
    onPeriodStarted: () -> Unit,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = prediction.today
    val shape = prediction.shapeOn(today)
    val pages = heroPages(prediction, today)
    val palette = paletteOf(status?.phase)
    val layout = remember { SceneLayout(SCENE_SEED) }
    val ground = TwilightThemeExtras.solidBackground
    val colors = TwilightThemeExtras.cycleColors
    var onScreen by remember { mutableStateOf(true) }
    val clock = sceneClock(onScreen)
    val description = shape?.let { stringResource(R.string.hero_moon_description, it.cycleDay, it.length) }.orEmpty()
    val heart = status?.phase == CyclePhase.OVULATORY ||
        (shape != null && shape.cycleDay in shape.fertileStart..shape.ovulation)
    val inFuture = status == null && prediction.timeline.isNotEmpty()

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(SCENE_HEIGHT)) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(SCENE_HEIGHT)
                    .clipToBounds()
                    .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f }
                    .semantics { contentDescription = description }
                    .drawWithCache {
                        // The sky, forest and mist only change with the size or the phase, so they are built once.
                        val sky = skyBrush(palette, ground)
                        val forest = forestLayers(layout, palette, ground)
                        val mist = mistBrush(palette)
                        onDrawBehind {
                            val millis = clock.longValue
                            val drift = (millis % DRIFT_MILLIS).toFloat() / DRIFT_MILLIS
                            val twinkle = abs(1f - 2f * (millis % (TWINKLE_MILLIS * 2)).toFloat() / (TWINKLE_MILLIS * 2))
                            drawRect(sky)
                            drawStars(layout, palette, twinkle)
                            shape?.let { drawSceneMoon(MoonPhase.of(it.cycleDay, it.length, it.ovulation), palette) }
                            drawClouds(layout, palette, drift)
                            if (heart) drawHeartCloud(Offset(size.width * HEART_X, size.height * HEART_Y), HEART_SIZE.toPx())
                            forest.forEach { (path, tone) -> drawPath(path, tone) }
                            drawRect(mist, Offset(0f, size.height * MIST_TOP), Size(size.width, size.height * MIST_HEIGHT))
                            shape?.let { drawCyclePath(it, colors.period, FERTILE_GOLD, twinkle) }
                        }
                    },
            )
            PaletteButton(onOpenTheme, Modifier.align(Alignment.TopEnd).padding(16.dp))
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = TITLE_TOP, start = SIDE, end = SIDE),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val day = pages.firstOrNull { it.kind == PageKind.CYCLE_DAY }
                if (day != null) {
                    Text(
                        stringResource(R.string.hero_day_title, day.value),
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light, shadow = GLOW),
                        color = Color.White,
                    )
                    day.note?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium.copy(shadow = GLOW), color = Color.White)
                    }
                } else {
                    Text(
                        stringResource(if (inFuture) R.string.today_cycle_in_future else R.string.today_no_data_title),
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = GLOW),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // Key numbers as soft tiles right under the horizon.
        val facts = pages.filter { it.kind != PageKind.CYCLE_DAY }
        if (facts.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = SIDE),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                facts.forEach { FactTile(it) }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (status == null) {
            // Nothing to show yet: marking the first period is the way in.
            if (!inFuture) Button(onClick = onPeriodStarted) { Text(stringResource(R.string.today_period_started)) }
        } else {
            HeroAction(prediction, onEditPeriod, onPeriodStarted)
        }
    }
}

/**
 * Milliseconds of scene time. It runs at a calm frame rate only while the scene is on screen
 * and stands still when the system asks for no animations, so the picture costs little battery.
 * The state is read only while drawing, so ticking it redraws the scene without recomposing.
 */
@Composable
private fun sceneClock(onScreen: Boolean): LongState {
    val context = LocalContext.current
    val clock = remember { mutableLongStateOf(0L) }
    val still = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(still, onScreen) {
        if (still || !onScreen) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            delay(FRAME_MILLIS)
            withFrameMillis { now ->
                clock.longValue += (now - last).coerceAtMost(FRAME_MILLIS * 4)
                last = now
            }
        }
    }
    return clock
}

@Composable
private fun FactTile(page: HeroPage) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(page.value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light), color = MaterialTheme.colorScheme.primary)
            Text(page.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

private fun skyBrush(palette: SkyPalette, ground: Color) = Brush.verticalGradient(
    0f to palette.top,
    0.45f to palette.middle,
    HORIZON to palette.horizon,
    1f to lerp(palette.horizon, ground, 0.6f),
)

private fun CacheDrawScope.mistBrush(palette: SkyPalette) = Brush.verticalGradient(
    listOf(Color.Transparent, palette.horizon.copy(alpha = 0.18f), Color.Transparent),
    size.height * MIST_TOP,
    size.height * (MIST_TOP + MIST_HEIGHT),
)

private fun DrawScope.drawStars(layout: SceneLayout, palette: SkyPalette, twinkle: Float) {
    if (palette.stars <= 0f) return
    layout.stars.forEach { (x, y, seed) ->
        // Each star twinkles at its own phase.
        val shimmer = 0.5f + 0.5f * abs(kotlin.math.sin((twinkle + seed) * Math.PI)).toFloat()
        val alpha = palette.stars * shimmer * (0.4f + seed * 0.6f)
        drawCircle(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), (0.8f + seed * 1.6f).dp.toPx(), Offset(x * size.width, y * size.height))
    }
}

private fun DrawScope.drawSceneMoon(moon: MoonPhase, palette: SkyPalette) {
    val center = Offset(size.width * MOON_X, size.height * MOON_Y)
    val r = MOON_RADIUS.toPx()
    drawCircle(Brush.radialGradient(listOf(MOON_LIGHT.copy(alpha = 0.35f), Color.Transparent), center, r * 3f), r * 3f, center)
    drawMoon(moon, MOON_LIGHT, lerp(palette.middle, palette.top, 0.5f), center, r)
}

private fun DrawScope.drawClouds(layout: SceneLayout, palette: SkyPalette, drift: Float) {
    layout.clouds.forEachIndexed { index, (x, y, scale) ->
        // Clouds drift right at one or two laps per loop, so the loop closes without a jump,
        // and leave the scene fully before they come back from the left.
        val width = CLOUD_SIZE.toPx() * scale
        val lap = (x + drift * (1 + index % 2)) % 1f
        val cx = -width + lap * (size.width + 2 * width)
        drawCloud(Offset(cx, y * size.height), width, Color.White.copy(alpha = palette.clouds))
    }
}

private fun DrawScope.drawCloud(at: Offset, width: Float, color: Color) {
    val puffs = listOf(Offset(-0.32f, 0.06f) to 0.26f, Offset(-0.08f, -0.08f) to 0.34f, Offset(0.2f, -0.02f) to 0.3f, Offset(0.4f, 0.08f) to 0.22f)
    puffs.forEach { (o, r) ->
        val c = Offset(at.x + o.x * width, at.y + o.y * width)
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), c, r * width), r * width, c)
    }
}

/** A soft heart of cloud, the same as on the app icon, drawn in layers so its edges blur. */
private fun DrawScope.drawHeartCloud(center: Offset, size: Float) {
    fun heart(s: Float) = Path().apply {
        moveTo(center.x, center.y + s * 0.35f)
        cubicTo(center.x - s * 0.9f, center.y - s * 0.2f, center.x - s * 0.45f, center.y - s * 0.85f, center.x, center.y - s * 0.38f)
        cubicTo(center.x + s * 0.45f, center.y - s * 0.85f, center.x + s * 0.9f, center.y - s * 0.2f, center.x, center.y + s * 0.35f)
        close()
    }
    listOf(1.25f to 0.12f, 1.1f to 0.2f, 0.95f to 0.35f, 0.8f to 0.45f).forEach { (scale, alpha) ->
        drawPath(heart(size * scale), Color.White.copy(alpha = alpha))
    }
}

/**
 * The cycle as lights along an arc across the sky, from the left horizon to the right one:
 * period days in the period colour, fertile days golden, today large and glowing.
 */
private fun DrawScope.drawCyclePath(shape: CycleShape, period: Color, fertile: Color, twinkle: Float) {
    val start = Offset(size.width * 0.07f, size.height * ARC_BASE)
    val end = Offset(size.width * 0.93f, size.height * ARC_BASE)
    val control = Offset(size.width * 0.5f, size.height * ARC_CONTROL)
    fun at(t: Float) = Offset(
        (1 - t) * (1 - t) * start.x + 2 * (1 - t) * t * control.x + t * t * end.x,
        (1 - t) * (1 - t) * start.y + 2 * (1 - t) * t * control.y + t * t * end.y,
    )
    val last = (shape.length - 1).coerceAtLeast(1)
    val arc = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }
    drawPath(arc, Color.White.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
    for (day in 1..shape.length) {
        val p = at((day - 1f) / last)
        val color = when {
            day <= shape.periodLength -> period
            day == shape.ovulation -> Color.White
            day in shape.fertileStart..shape.ovulation -> fertile
            else -> Color.White.copy(alpha = 0.45f)
        }
        val r = if (day == shape.ovulation) 4.5.dp.toPx() else 2.6.dp.toPx()
        drawCircle(color, r, p)
    }
    val now = at((shape.cycleDay - 1f) / last)
    val glow = TODAY_GLOW.toPx() * (0.85f + 0.15f * twinkle)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), now, glow), glow, now)
    drawCircle(Color.White, 6.dp.toPx(), now)
    drawCircle(period, 6.dp.toPx(), now, style = Stroke(2.dp.toPx()))
}

/** Three rows of pine silhouettes, each nearer one closer to the page colour, so the scene melts into it. */
private fun CacheDrawScope.forestLayers(layout: SceneLayout, palette: SkyPalette, ground: Color): List<Pair<Path, Color>> =
    layout.forests.mapIndexed { layer, trees ->
        val baseY = size.height * (FOREST_BASE + layer * FOREST_STEP)
        val maxHeight = size.height * (FOREST_HEIGHT - layer * 0.03f)
        val tone = lerp(lerp(palette.top, Color.Black, 0.35f), ground, FOREST_MIX[layer])
        val path = Path()
        trees.forEach { (x, width, height) ->
            val left = x * size.width
            val w = width * size.width
            val h = height * maxHeight
            // A pine in three tiers.
            for (tier in 0 until 3) {
                val tierBase = baseY - h * tier * 0.28f
                val tierWidth = w * (1f - tier * 0.25f)
                val offset = (w - tierWidth) / 2
                path.moveTo(left + offset, tierBase)
                path.lineTo(left + w / 2, tierBase - h * (0.5f - tier * 0.05f))
                path.lineTo(left + offset + tierWidth, tierBase)
                path.close()
            }
            path.addRect(Rect(left + w * 0.45f, baseY - 2f, left + w * 0.55f, baseY + 6.dp.toPx()))
        }
        path.addRect(Rect(0f, baseY, size.width, size.height))
        path to tone
    }

private val SCENE_HEIGHT = 440.dp
private val TITLE_TOP = 64.dp
private val MOON_RADIUS = 30.dp
private val CLOUD_SIZE = 150.dp
private val HEART_SIZE = 56.dp
private val TODAY_GLOW = 22.dp
private val MOON_LIGHT = Color(0xFFFFF4DC)
private val FERTILE_GOLD = Color(0xFFFFD66B)
private val GLOW = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 14f)
private const val SCENE_SEED = 21
private const val STAR_COUNT = 40
private const val STAR_BAND = 0.55f
private const val CLOUD_COUNT = 4
private const val CLOUD_TOP = 0.36f
private const val CLOUD_BAND = 0.2f
private const val FOREST_LAYERS = 3
private const val FOREST_BASE = 0.8f
private const val FOREST_STEP = 0.07f
private const val FOREST_HEIGHT = 0.2f
private val FOREST_MIX = listOf(0.25f, 0.55f, 1f)
private const val HORIZON = 0.78f
private const val MOON_X = 0.72f
private const val MOON_Y = 0.21f
private const val HEART_X = 0.26f
private const val HEART_Y = 0.47f
private const val ARC_BASE = 0.56f
private const val ARC_CONTROL = 0.16f
private const val MIST_TOP = 0.62f
private const val MIST_HEIGHT = 0.23f
private const val DRIFT_MILLIS = 90_000
private const val TWINKLE_MILLIS = 2_400
private const val FRAME_MILLIS = 33L
