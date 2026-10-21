package io.github.zaxar1231.mycycle.core.ui.pet

import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.pet.PetItem
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.pet.PetSlot
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Everything that decides how the pet looks at one moment. */
data class PetLook(
    val type: PetType,
    val mood: PetMood,
    val worn: Map<PetSlot, PetItem> = emptyMap(),
    /** Uptime millis of the last stroke; hearts rise for a moment after it. */
    val pettedAt: Long = 0L,
)

/**
 * Draws the pet. The built-in renderer paints it with Compose; a renderer for Lottie or Rive
 * files can replace it through [LocalPetRenderer] once real animation assets exist.
 */
interface PetRenderer {
    @Composable
    fun Pet(look: PetLook, description: String, modifier: Modifier)
}

val LocalPetRenderer = staticCompositionLocalOf<PetRenderer> { DrawnPetRenderer }

@Composable
fun PetPicture(look: PetLook, description: String, modifier: Modifier = Modifier) {
    LocalPetRenderer.current.Pet(look, description, modifier)
}

/** The pet painted in code: it breathes, blinks, sways its tail and shows its mood. */
object DrawnPetRenderer : PetRenderer {
    @Composable
    override fun Pet(look: PetLook, description: String, modifier: Modifier) {
        var onScreen by remember { mutableStateOf(true) }
        val context = LocalContext.current
        val still = remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        val clock = petClock(onScreen && !still)
        // Without animations the hearts stand still for a moment, then one more redraw clears them.
        var heartsGone by remember { mutableLongStateOf(0L) }
        if (still) {
            LaunchedEffect(look.pettedAt) {
                delay(HEARTS_MILLIS)
                heartsGone = look.pettedAt
            }
        }
        Spacer(
            modifier
                .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f }
                .semantics { contentDescription = description }
                .drawBehind {
                    val frozen = if (still) look.pettedAt != heartsGone else null
                    drawPet(look, clock.longValue, frozenHearts = frozen)
                },
        )
    }
}

/** Milliseconds of animation time at a calm frame rate, ticking only while [running]. */
@Composable
private fun petClock(running: Boolean): LongState {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
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

/** Fur, belly and accent colours of each animal. */
private class Coat(val fur: Color, val belly: Color, val accent: Color, val inner: Color)

private fun coatOf(type: PetType) = when (type) {
    PetType.CAT -> Coat(Color(0xFFF2A65A), Color(0xFFFCE6CC), Color(0xFFD9822B), Color(0xFFF7B9B0))
    PetType.FOX -> Coat(Color(0xFFE8743B), Color(0xFFFFF4E8), Color(0xFF5A3A2E), Color(0xFFFFD2BF))
    PetType.BUNNY -> Coat(Color(0xFFF4EEF2), Color(0xFFFFFFFF), Color(0xFFD9C8D2), Color(0xFFF7B7C8))
    PetType.PANDA -> Coat(Color(0xFFF8F8F6), Color(0xFFFFFFFF), Color(0xFF2B2B33), Color(0xFF2B2B33))
    PetType.PENGUIN -> Coat(Color(0xFF2F3B5C), Color(0xFFFDFBF7), Color(0xFFF2A33A), Color(0xFF2F3B5C))
}

/** [frozenHearts]: null animates the hearts, true shows them still, false hides them. */
private fun DrawScope.drawPet(look: PetLook, millis: Long, frozenHearts: Boolean?) {
    val s = size.minDimension
    val left = (size.width - s) / 2
    val top = (size.height - s) / 2
    val coat = coatOf(look.type)
    val breath = wave(millis, BREATH_MILLIS)
    val sleepy = look.mood == PetMood.SLEEPY
    // Playful pets bounce; sleepy ones breathe slowly and deeply.
    val bounce = if (look.mood == PetMood.PLAYFUL) -abs(wave(millis, BOUNCE_MILLIS)) * s * 0.03f else 0f
    val swell = 1f + breath * (if (sleepy) 0.035f else 0.02f)

    withTransform({ translate(left, top + bounce) }) {
        // Shadow.
        drawOval(Color.Black.copy(alpha = 0.10f), Offset(s * 0.22f, s * 0.86f), Size(s * 0.56f, s * 0.08f))
        if (look.worn[PetSlot.BODY]?.id == "cape") drawCape(s, breath)
        drawTail(look.type, coat, s, wave(millis, TAIL_MILLIS))
        // Body, swelling with each breath from its bottom.
        withTransform({ scale(1f, swell, Offset(s * 0.5f, s * 0.88f)) }) {
            drawBody(look.type, coat, s, look.worn[PetSlot.BODY])
        }
        drawEars(look.type, coat, s, sleepy)
        drawHead(look.type, coat, s)
        drawFace(look, coat, s, millis)
        look.worn[PetSlot.NECK]?.let { drawNeckItem(it, s) }
        look.worn[PetSlot.HAIR]?.let { drawHair(it, coat, s) }
        look.worn[PetSlot.HEAD]?.let { drawHeadItem(it, s) }
        look.worn[PetSlot.FACE]?.let { drawFaceItem(it, s) }
        if (look.mood == PetMood.COZY) drawHotWaterBottle(look.type, coat, s, breath)
        if (sleepy) drawZs(s, millis)
        if (look.mood == PetMood.HAPPY) drawSparkles(s, millis)
    }
    when (frozenHearts) {
        null -> drawHearts(look.pettedAt, s, left, top, still = false)
        true -> drawHearts(look.pettedAt, s, left, top, still = true)
        false -> Unit
    }
}

private fun DrawScope.drawTail(type: PetType, coat: Coat, s: Float, sway: Float) {
    val base = Offset(s * 0.72f, s * 0.76f)
    rotate(sway * 8f, base) {
        when (type) {
            PetType.CAT -> drawPath(
                Path().apply {
                    moveTo(base.x, base.y)
                    cubicTo(s * 0.92f, s * 0.78f, s * 0.95f, s * 0.55f, s * 0.84f, s * 0.48f)
                },
                coat.accent,
                style = Stroke(s * 0.05f, cap = StrokeCap.Round),
            )
            PetType.FOX -> {
                val tail = Path().apply {
                    moveTo(base.x - s * 0.02f, base.y + s * 0.04f)
                    cubicTo(s * 1.0f, s * 0.86f, s * 1.02f, s * 0.5f, s * 0.86f, s * 0.42f)
                    cubicTo(s * 0.86f, s * 0.58f, s * 0.8f, s * 0.68f, base.x, base.y - s * 0.04f)
                    close()
                }
                drawPath(tail, coat.fur)
                drawCircle(coat.belly, s * 0.05f, Offset(s * 0.875f, s * 0.45f))
            }
            PetType.BUNNY -> drawCircle(coat.belly, s * 0.06f, Offset(s * 0.76f, s * 0.8f))
            PetType.PANDA -> drawCircle(coat.inner, s * 0.04f, Offset(s * 0.76f, s * 0.82f))
            PetType.PENGUIN -> Unit
        }
    }
}

private fun DrawScope.drawBody(type: PetType, coat: Coat, s: Float, outfit: PetItem?) {
    val bodyColor = when (outfit?.id) {
        "sweater" -> SWEATER
        else -> coat.fur
    }
    drawOval(bodyColor, Offset(s * 0.24f, s * 0.5f), Size(s * 0.52f, s * 0.4f))
    if (outfit?.id == "sweater") {
        // Knit stripes.
        for (i in 0..2) {
            val y = s * (0.62f + i * 0.08f)
            drawLine(SWEATER_STRIPE, Offset(s * 0.28f, y), Offset(s * 0.72f, y), strokeWidth = s * 0.018f, cap = StrokeCap.Round)
        }
    } else {
        drawOval(coat.belly, Offset(s * 0.35f, s * 0.6f), Size(s * 0.3f, s * 0.27f))
    }
    // Arms / flippers and feet.
    val limb = if (type == PetType.PANDA) coat.accent else if (type == PetType.PENGUIN) coat.fur else bodyColor
    drawOval(limb, Offset(s * 0.2f, s * 0.6f), Size(s * 0.12f, s * 0.2f))
    drawOval(limb, Offset(s * 0.68f, s * 0.6f), Size(s * 0.12f, s * 0.2f))
    val feet = when (type) {
        PetType.PENGUIN -> coat.accent
        PetType.PANDA -> coat.accent
        else -> coat.fur
    }
    drawOval(feet, Offset(s * 0.32f, s * 0.84f), Size(s * 0.14f, s * 0.07f))
    drawOval(feet, Offset(s * 0.54f, s * 0.84f), Size(s * 0.14f, s * 0.07f))
}

private fun DrawScope.drawEars(type: PetType, coat: Coat, s: Float, droopy: Boolean) {
    fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    when (type) {
        PetType.CAT, PetType.FOX -> {
            val tip = if (type == PetType.FOX) 0.08f else 0.12f
            listOf(-1f, 1f).forEach { side ->
                val cx = s * 0.5f + side * s * 0.15f
                val outer = triangle(Offset(cx - s * 0.09f, s * 0.3f), Offset(cx + side * s * 0.02f, s * tip), Offset(cx + s * 0.09f, s * 0.3f))
                drawPath(outer, coat.fur)
                val inner = triangle(Offset(cx - s * 0.05f, s * 0.28f), Offset(cx + side * s * 0.015f, s * (tip + 0.06f)), Offset(cx + s * 0.05f, s * 0.28f))
                drawPath(inner, if (type == PetType.FOX) coat.accent else coat.inner)
            }
        }
        PetType.BUNNY -> listOf(-1f, 1f).forEach { side ->
            // Long ears droop to the sides when the bunny is sleepy.
            val tilt = side * if (droopy) 35f else 8f
            val pivot = Offset(s * 0.5f + side * s * 0.09f, s * 0.24f)
            rotate(tilt, pivot) {
                drawOval(coat.fur, Offset(pivot.x - s * 0.055f, s * -0.02f), Size(s * 0.11f, s * 0.3f))
                drawOval(coat.inner, Offset(pivot.x - s * 0.03f, s * 0.02f), Size(s * 0.06f, s * 0.22f))
            }
        }
        PetType.PANDA -> listOf(-1f, 1f).forEach { side ->
            drawCircle(coat.accent, s * 0.075f, Offset(s * 0.5f + side * s * 0.17f, s * 0.2f))
        }
        PetType.PENGUIN -> Unit
    }
}

private fun DrawScope.drawHead(type: PetType, coat: Coat, s: Float) {
    val center = Offset(s * 0.5f, s * 0.38f)
    drawCircle(coat.fur, s * 0.23f, center)
    when (type) {
        PetType.FOX -> drawPath(
            Path().apply {
                moveTo(s * 0.29f, s * 0.38f)
                quadraticTo(s * 0.5f, s * 0.36f, s * 0.71f, s * 0.38f)
                quadraticTo(s * 0.62f, s * 0.6f, s * 0.5f, s * 0.6f)
                quadraticTo(s * 0.38f, s * 0.6f, s * 0.29f, s * 0.38f)
                close()
            },
            coat.belly,
        )
        PetType.PENGUIN -> drawOval(coat.belly, Offset(s * 0.33f, s * 0.27f), Size(s * 0.34f, s * 0.3f))
        PetType.CAT, PetType.BUNNY -> drawOval(coat.belly, Offset(s * 0.38f, s * 0.42f), Size(s * 0.24f, s * 0.15f))
        PetType.PANDA -> Unit
    }
}

private fun DrawScope.drawFace(look: PetLook, coat: Coat, s: Float, millis: Long) {
    val eyeY = s * 0.38f
    val eyes = listOf(s * 0.42f, s * 0.58f)
    val ink = Color(0xFF2B2230)
    if (look.type == PetType.PANDA) {
        eyes.forEachIndexed { i, x ->
            rotate(if (i == 0) -25f else 25f, Offset(x, eyeY)) {
                drawOval(coat.accent, Offset(x - s * 0.055f, eyeY - s * 0.04f), Size(s * 0.11f, s * 0.085f))
            }
        }
    }
    val eyeInk = if (look.type == PetType.PANDA) Color.White else ink
    val line = Stroke(s * 0.016f, cap = StrokeCap.Round)
    // A blink every few seconds, while the eyes are open.
    val blinking = (millis % BLINK_EVERY_MILLIS) < BLINK_MILLIS
    when (look.mood) {
        PetMood.SLEEPY -> eyes.forEach { x -> drawLine(eyeInk, Offset(x - s * 0.03f, eyeY), Offset(x + s * 0.03f, eyeY), strokeWidth = s * 0.016f, cap = StrokeCap.Round) }
        PetMood.HAPPY -> eyes.forEach { x -> drawArc(eyeInk, 200f, 140f, false, Offset(x - s * 0.035f, eyeY - s * 0.02f), Size(s * 0.07f, s * 0.06f), style = line) }
        PetMood.COZY -> eyes.forEach { x -> drawArc(eyeInk, 20f, 140f, false, Offset(x - s * 0.035f, eyeY - s * 0.04f), Size(s * 0.07f, s * 0.05f), style = line) }
        else -> eyes.forEach { x ->
            if (blinking) {
                drawLine(eyeInk, Offset(x - s * 0.028f, eyeY), Offset(x + s * 0.028f, eyeY), strokeWidth = s * 0.014f, cap = StrokeCap.Round)
            } else {
                drawOval(eyeInk, Offset(x - s * 0.026f, eyeY - s * 0.034f), Size(s * 0.052f, s * 0.068f))
                drawCircle(Color.White, s * 0.011f, Offset(x + s * 0.008f, eyeY - s * 0.014f))
            }
        }
    }
    if (look.mood == PetMood.SAD) {
        // Worried brows and a tear.
        drawLine(eyeInk, Offset(s * 0.38f, eyeY - s * 0.06f), Offset(s * 0.44f, eyeY - s * 0.08f), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
        drawLine(eyeInk, Offset(s * 0.62f, eyeY - s * 0.06f), Offset(s * 0.56f, eyeY - s * 0.08f), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
        drawOval(TEAR, Offset(s * 0.6f, eyeY + s * 0.03f), Size(s * 0.022f, s * 0.035f))
    }
    if (look.mood == PetMood.HAPPY || look.mood == PetMood.COZY || look.mood == PetMood.PLAYFUL) {
        listOf(s * 0.36f, s * 0.64f).forEach { x -> drawCircle(BLUSH, s * 0.03f, Offset(x, eyeY + s * 0.06f)) }
    }
    // Nose and mouth; the penguin has a beak.
    val mouthY = s * 0.48f
    if (look.type == PetType.PENGUIN) {
        drawPath(
            Path().apply {
                moveTo(s * 0.46f, mouthY - s * 0.02f)
                lineTo(s * 0.54f, mouthY - s * 0.02f)
                lineTo(s * 0.5f, mouthY + s * 0.035f)
                close()
            },
            coat.accent,
        )
        return
    }
    val nose = if (look.type == PetType.PANDA || look.type == PetType.FOX) ink else NOSE
    drawOval(nose, Offset(s * 0.48f, mouthY - s * 0.03f), Size(s * 0.04f, s * 0.026f))
    if (look.mood == PetMood.SAD) {
        drawArc(ink, 200f, 140f, false, Offset(s * 0.46f, mouthY + s * 0.015f), Size(s * 0.08f, s * 0.05f), style = Stroke(s * 0.012f, cap = StrokeCap.Round))
    } else {
        // A little "w" smile.
        listOf(s * 0.46f, s * 0.5f).forEach { x ->
            drawArc(ink, 20f, 140f, false, Offset(x, mouthY - s * 0.01f), Size(s * 0.04f, s * 0.035f), style = Stroke(s * 0.011f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawHair(item: PetItem, coat: Coat, s: Float) {
    val tuft = if (coat.accent == coat.fur) coat.accent else coat.fur
    when (item.id) {
        "tuft" -> listOf(-0.03f, 0f, 0.03f).forEachIndexed { i, dx ->
            drawPath(
                Path().apply {
                    moveTo(s * (0.5f + dx), s * 0.16f)
                    quadraticTo(s * (0.5f + dx * 2 + (i - 1) * 0.02f), s * 0.08f, s * (0.5f + dx * 3), s * 0.09f)
                },
                tuft,
                style = Stroke(s * 0.022f, cap = StrokeCap.Round),
            )
        }
        "bangs" -> drawArc(tuft, 190f, 160f, true, Offset(s * 0.32f, s * 0.17f), Size(s * 0.36f, s * 0.2f))
    }
}

private fun DrawScope.drawHeadItem(item: PetItem, s: Float) {
    when (item.id) {
        "bow" -> {
            val c = Offset(s * 0.64f, s * 0.19f)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - s * 0.08f, c.y - s * 0.05f); lineTo(c.x - s * 0.08f, c.y + s * 0.05f); close() }, BOW)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + s * 0.08f, c.y - s * 0.05f); lineTo(c.x + s * 0.08f, c.y + s * 0.05f); close() }, BOW)
            drawCircle(BOW_KNOT, s * 0.022f, c)
        }
        "flower_crown" -> for (i in 0..4) {
            val angle = PI * (1.15 + i * 0.175)
            val c = Offset(s * 0.5f + (s * 0.22f * cos(angle)).toFloat(), s * 0.38f + (s * 0.22f * sin(angle)).toFloat())
            drawCircle(FLOWERS[i % FLOWERS.size], s * 0.03f, c)
            drawCircle(FLOWER_HEART, s * 0.011f, c)
        }
        "beret" -> rotate(-12f, Offset(s * 0.5f, s * 0.17f)) {
            drawOval(BERET, Offset(s * 0.3f, s * 0.1f), Size(s * 0.4f, s * 0.12f))
            drawCircle(BERET, s * 0.015f, Offset(s * 0.5f, s * 0.095f))
        }
        "party_hat" -> {
            drawPath(Path().apply { moveTo(s * 0.42f, s * 0.18f); lineTo(s * 0.5f, s * -0.02f); lineTo(s * 0.58f, s * 0.18f); close() }, PARTY)
            drawLine(PARTY_STRIPE, Offset(s * 0.45f, s * 0.11f), Offset(s * 0.55f, s * 0.11f), strokeWidth = s * 0.015f)
            drawCircle(PARTY_STRIPE, s * 0.025f, Offset(s * 0.5f, s * -0.02f))
        }
        "crown" -> {
            val crown = Path().apply {
                moveTo(s * 0.38f, s * 0.17f)
                lineTo(s * 0.38f, s * 0.07f)
                lineTo(s * 0.44f, s * 0.12f)
                lineTo(s * 0.5f, s * 0.04f)
                lineTo(s * 0.56f, s * 0.12f)
                lineTo(s * 0.62f, s * 0.07f)
                lineTo(s * 0.62f, s * 0.17f)
                close()
            }
            drawPath(crown, GOLD)
            drawCircle(GEM, s * 0.015f, Offset(s * 0.5f, s * 0.13f))
        }
    }
}

private fun DrawScope.drawFaceItem(item: PetItem, s: Float) {
    val eyeY = s * 0.38f
    when (item.id) {
        "glasses" -> {
            val frame = Stroke(s * 0.012f)
            listOf(s * 0.42f, s * 0.58f).forEach { x -> drawCircle(FRAME, s * 0.05f, Offset(x, eyeY), style = frame) }
            drawLine(FRAME, Offset(s * 0.47f, eyeY), Offset(s * 0.53f, eyeY), strokeWidth = s * 0.012f)
        }
        "heart_glasses" -> listOf(s * 0.42f, s * 0.58f).forEach { x ->
            drawPath(heart(Offset(x, eyeY + s * 0.01f), s * 0.06f), HEART_LENS.copy(alpha = 0.85f))
        }
    }
}

private fun DrawScope.drawNeckItem(item: PetItem, s: Float) {
    val y = s * 0.58f
    when (item.id) {
        "scarf" -> {
            drawRoundRect(SCARF, Offset(s * 0.32f, y - s * 0.03f), Size(s * 0.36f, s * 0.06f), CornerRadius(s * 0.03f))
            drawRoundRect(SCARF, Offset(s * 0.58f, y), Size(s * 0.06f, s * 0.14f), CornerRadius(s * 0.02f))
        }
        "bowtie" -> {
            val c = Offset(s * 0.5f, y)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - s * 0.07f, c.y - s * 0.04f); lineTo(c.x - s * 0.07f, c.y + s * 0.04f); close() }, BOWTIE)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + s * 0.07f, c.y - s * 0.04f); lineTo(c.x + s * 0.07f, c.y + s * 0.04f); close() }, BOWTIE)
            drawCircle(BOWTIE, s * 0.018f, c)
        }
        "bell" -> {
            drawRoundRect(COLLAR, Offset(s * 0.34f, y - s * 0.02f), Size(s * 0.32f, s * 0.035f), CornerRadius(s * 0.02f))
            drawCircle(GOLD, s * 0.035f, Offset(s * 0.5f, y + s * 0.035f))
            drawLine(BELL_SLOT, Offset(s * 0.5f, y + s * 0.04f), Offset(s * 0.5f, y + s * 0.065f), strokeWidth = s * 0.008f)
        }
    }
}

private fun DrawScope.drawCape(s: Float, breath: Float) {
    val cape = Path().apply {
        moveTo(s * 0.34f, s * 0.56f)
        quadraticTo(s * 0.12f, s * (0.84f + breath * 0.01f), s * 0.2f, s * 0.9f)
        lineTo(s * 0.8f, s * 0.9f)
        quadraticTo(s * 0.88f, s * (0.84f + breath * 0.01f), s * 0.66f, s * 0.56f)
        close()
    }
    drawPath(cape, CAPE)
}

private fun DrawScope.drawHotWaterBottle(type: PetType, coat: Coat, s: Float, breath: Float) {
    val top = s * (0.62f - breath * 0.005f)
    drawRoundRect(BOTTLE, Offset(s * 0.37f, top), Size(s * 0.26f, s * 0.2f), CornerRadius(s * 0.06f))
    drawRoundRect(BOTTLE_CAP, Offset(s * 0.46f, top - s * 0.035f), Size(s * 0.08f, s * 0.05f), CornerRadius(s * 0.015f))
    drawPath(heart(Offset(s * 0.5f, top + s * 0.1f), s * 0.07f), Color.White.copy(alpha = 0.7f))
    // Paws hugging it.
    val paw = if (type == PetType.PANDA) coat.accent else coat.fur
    drawCircle(paw, s * 0.035f, Offset(s * 0.38f, top + s * 0.1f))
    drawCircle(paw, s * 0.035f, Offset(s * 0.62f, top + s * 0.1f))
}

private fun DrawScope.drawZs(s: Float, millis: Long) {
    for (i in 0..2) {
        val t = ((millis + i * Z_MILLIS / 3) % Z_MILLIS).toFloat() / Z_MILLIS
        val size = s * (0.035f + i * 0.012f)
        val x = s * (0.7f + t * 0.12f)
        val y = s * (0.22f - t * 0.16f)
        val alpha = (1f - t).coerceIn(0f, 1f)
        drawPath(
            Path().apply {
                moveTo(x, y)
                lineTo(x + size, y)
                lineTo(x, y + size)
                lineTo(x + size, y + size)
            },
            Z_COLOR.copy(alpha = alpha),
            style = Stroke(s * 0.012f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSparkles(s: Float, millis: Long) {
    listOf(Offset(0.18f, 0.24f), Offset(0.84f, 0.3f), Offset(0.8f, 0.12f)).forEachIndexed { i, p ->
        val glow = abs(wave(millis + i * 400L, SPARKLE_MILLIS))
        val c = Offset(s * p.x, s * p.y)
        val r = s * 0.025f * (0.5f + glow)
        drawLine(SPARKLE.copy(alpha = glow), Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = s * 0.008f, cap = StrokeCap.Round)
        drawLine(SPARKLE.copy(alpha = glow), Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = s * 0.008f, cap = StrokeCap.Round)
    }
}

/** Hearts float up for a moment after a stroke. */
private fun DrawScope.drawHearts(pettedAt: Long, s: Float, left: Float, top: Float, still: Boolean) {
    if (pettedAt == 0L) return
    val age = if (still) 0L else android.os.SystemClock.uptimeMillis() - pettedAt
    if (age !in 0..HEARTS_MILLIS) return
    val t = age.toFloat() / HEARTS_MILLIS
    listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, x ->
        val rise = t * s * (0.25f + i * 0.05f)
        val c = Offset(left + s * x, top + s * 0.3f - rise)
        drawPath(heart(c, s * 0.06f), HEART.copy(alpha = (1f - t).coerceIn(0f, 1f)))
    }
}

private fun heart(center: Offset, size: Float) = Path().apply {
    moveTo(center.x, center.y + size * 0.35f)
    cubicTo(center.x - size * 0.9f, center.y - size * 0.2f, center.x - size * 0.45f, center.y - size * 0.85f, center.x, center.y - size * 0.38f)
    cubicTo(center.x + size * 0.45f, center.y - size * 0.85f, center.x + size * 0.9f, center.y - size * 0.2f, center.x, center.y + size * 0.35f)
    close()
}

/** A sine wave from -1 to 1 with the given period. */
private fun wave(millis: Long, period: Long): Float = sin(2 * PI * (millis % period) / period).toFloat()

private const val FRAME_MILLIS = 33L
private const val BREATH_MILLIS = 3_600L
private const val BOUNCE_MILLIS = 1_200L
private const val TAIL_MILLIS = 2_400L
private const val BLINK_EVERY_MILLIS = 4_200L
private const val BLINK_MILLIS = 140L
private const val Z_MILLIS = 3_000L
private const val SPARKLE_MILLIS = 1_600L
const val HEARTS_MILLIS = 1_400L

private val SWEATER = Color(0xFF4FA3A5)
private val SWEATER_STRIPE = Color(0xFFE9F5F2)
private val TEAR = Color(0xFF8CC8F0)
private val BLUSH = Color(0xFFF59AAE).copy(alpha = 0.55f)
private val NOSE = Color(0xFFE77F96)
private val BOW = Color(0xFFF26D9A)
private val BOW_KNOT = Color(0xFFD94C7C)
private val FLOWERS = listOf(Color(0xFFF7A1C4), Color(0xFFFFD66B), Color(0xFFB9A6F5))
private val FLOWER_HEART = Color(0xFFFFF4DC)
private val BERET = Color(0xFF8E2C48)
private val PARTY = Color(0xFF7C6CF2)
private val PARTY_STRIPE = Color(0xFFFFD66B)
private val GOLD = Color(0xFFF4C542)
private val GEM = Color(0xFFE5486B)
private val FRAME = Color(0xFF3A2F3F)
private val HEART_LENS = Color(0xFFF2557E)
private val SCARF = Color(0xFFE25252)
private val BOWTIE = Color(0xFF3D5AFE)
private val COLLAR = Color(0xFFD9475F)
private val BELL_SLOT = Color(0xFF8A6A14)
private val CAPE = Color(0xFF6A3FB5)
private val BOTTLE = Color(0xFFE86A7E)
private val BOTTLE_CAP = Color(0xFFB54459)
private val Z_COLOR = Color(0xFF8C86C9)
private val SPARKLE = Color(0xFFFFD66B)
private val HEART = Color(0xFFF2557E)
