package io.github.zaxar1231.mycycle.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Soft, translucent flowers drawn with vectors, placed around a centred element. Purely decorative:
 * no images to ship and no accessibility semantics. Positions are fractions of the canvas size.
 */
@Composable
fun FlowerBackdrop(colors: List<Color>, dark: Boolean, modifier: Modifier = Modifier) {
    // Flowers are drawn opaque and faded as one layer, so overlapping petals leave no darker seams.
    Canvas(
        modifier.graphicsLayer {
            alpha = if (dark) DARK_ALPHA else LIGHT_ALPHA
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        val unit = minOf(size.width, size.height)
        FLOWERS.forEachIndexed { index, flower ->
            drawFlower(
                center = Offset(size.width * flower.x, size.height * flower.y),
                radius = unit * flower.size,
                color = colors[index % colors.size],
                petals = flower.petals,
                rotation = flower.rotation,
                alpha = 1f,
            )
        }
    }
}

private class Flower(val x: Float, val y: Float, val size: Float, val petals: Int, val rotation: Float)

// Two large flowers at the sides, one behind the top, a small one at the lower left.
private val FLOWERS = listOf(
    Flower(x = 0.13f, y = 0.33f, size = 0.36f, petals = 5, rotation = 10f),
    Flower(x = 0.88f, y = 0.55f, size = 0.38f, petals = 6, rotation = -15f),
    Flower(x = 0.62f, y = 0.08f, size = 0.26f, petals = 5, rotation = 35f),
    Flower(x = 0.2f, y = 0.8f, size = 0.22f, petals = 6, rotation = 0f),
)

private fun DrawScope.drawFlower(center: Offset, radius: Float, color: Color, petals: Int, rotation: Float, alpha: Float) {
    val petal = petalPath(radius)
    val vein = lerp(color, Color.White, VEIN_LIGHTEN).copy(alpha = alpha * VEIN_ALPHA)
    repeat(petals) { i ->
        rotate(rotation + i * FULL_TURN / petals, pivot = center) {
            translate(center.x, center.y) {
                drawPath(
                    petal,
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), lerp(color, Color.White, PETAL_EDGE_LIGHTEN).copy(alpha = alpha * EDGE_ALPHA)),
                        center = Offset.Zero,
                        radius = radius,
                    ),
                )
                // A few fine veins from the centre towards the petal tip.
                for (v in -1..1) {
                    val angle = v * VEIN_SPREAD
                    drawLine(
                        vein,
                        Offset.Zero,
                        Offset(radius * VEIN_LENGTH * sin(angle), -radius * VEIN_LENGTH * cos(angle)),
                        strokeWidth = radius * VEIN_WIDTH,
                    )
                }
            }
        }
    }
    // Dark centre with a ring of stamen dots.
    drawCircle(Color(0xFF1F1214).copy(alpha = alpha), radius * CENTER_SIZE, center)
    val dots = STAMEN_COUNT
    repeat(dots) { i ->
        val a = 2 * PI * i / dots
        val r = radius * CENTER_SIZE * STAMEN_RING
        drawCircle(
            Color(0xFF120A0B).copy(alpha = alpha),
            radius * STAMEN_SIZE,
            Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()),
        )
    }
    drawCircle(lerp(color, Color.Yellow, POLLEN_TINT).copy(alpha = alpha * POLLEN_ALPHA), radius * POLLEN_SIZE, center, style = Stroke(radius * POLLEN_SIZE / 2))
}

/** A rounded petal pointing up from the origin. */
private fun petalPath(radius: Float) = Path().apply {
    moveTo(0f, 0f)
    cubicTo(-radius * PETAL_WIDTH, -radius * 0.3f, -radius * PETAL_WIDTH, -radius * 0.95f, 0f, -radius)
    cubicTo(radius * PETAL_WIDTH, -radius * 0.95f, radius * PETAL_WIDTH, -radius * 0.3f, 0f, 0f)
    close()
}

private inline fun DrawScope.translate(x: Float, y: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(x, y)
    block()
    drawContext.transform.translate(-x, -y)
}

private const val FULL_TURN = 360f
private const val DARK_ALPHA = 0.85f
private const val LIGHT_ALPHA = 0.55f
private const val PETAL_WIDTH = 0.62f
private const val PETAL_EDGE_LIGHTEN = 0.25f
private const val EDGE_ALPHA = 1f
private const val VEIN_LIGHTEN = 0.5f
private const val VEIN_ALPHA = 0.35f
private const val VEIN_SPREAD = 0.25f
private const val VEIN_LENGTH = 0.8f
private const val VEIN_WIDTH = 0.012f
private const val CENTER_SIZE = 0.16f
private const val STAMEN_COUNT = 10
private const val STAMEN_RING = 0.8f
private const val STAMEN_SIZE = 0.03f
private const val POLLEN_TINT = 0.3f
private const val POLLEN_ALPHA = 0.6f
private const val POLLEN_SIZE = 0.06f
