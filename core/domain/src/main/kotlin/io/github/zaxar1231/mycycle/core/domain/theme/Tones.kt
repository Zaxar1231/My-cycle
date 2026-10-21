package io.github.zaxar1231.mycycle.core.domain.theme

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Colour tones for building a whole colour scheme from one seed colour. A tone is CIE L*
 * lightness (0 black … 100 white) at the seed's hue, so tones of different hues look equally
 * light and text contrast follows from the tone difference, as in Material 3.
 * Colours are ARGB ints; this file has no Android dependencies so it can be unit-tested.
 */
object Tones {
    /** Hue in degrees and chroma of [argb] in CIE LCh(ab). */
    fun hueChroma(argb: Int): Pair<Float, Float> {
        val (_, a, b) = lab(argb)
        val hue = Math.toDegrees(atan2(b, a)).let { if (it < 0) it + FULL_TURN else it }
        return hue.toFloat() to hypot(a, b).toFloat()
    }

    /** The colour at [tone] with [hue] and [chroma]; chroma is reduced until it fits sRGB. */
    fun color(hue: Float, chroma: Float, tone: Float): Int {
        var c = chroma.toDouble()
        val h = Math.toRadians(hue.toDouble())
        while (true) {
            val rgb = labToLinear(tone.toDouble(), c * cos(h), c * sin(h))
            if (rgb.all { it in -EPSILON..1 + EPSILON } || c <= 0.0) return argbOf(rgb)
            c -= CHROMA_STEP
            if (c < 0) c = 0.0
        }
    }

    /** CIE L* of [argb]. */
    fun tone(argb: Int): Float = lab(argb).first.toFloat()

    /** HSV-style colour for the colour wheel: [hue] 0–360, [saturation] and [value] 0–1. */
    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % FULL_TURN.toFloat()) + FULL_TURN.toFloat()) % FULL_TURN.toFloat() / SECTOR
        val i = h.toInt()
        val f = h - i
        val p = value * (1 - saturation)
        val q = value * (1 - saturation * f)
        val t = value * (1 - saturation * (1 - f))
        val (r, g, b) = when (i % SECTORS) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            2 -> Triple(p, value, t)
            3 -> Triple(p, q, value)
            4 -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
        return OPAQUE or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Inverse of [fromHsv]: hue, saturation, value. */
    fun toHsv(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and BYTE) / 255f
        val g = (argb shr 8 and BYTE) / 255f
        val b = (argb and BYTE) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0f -> 0f
            max == r -> SECTOR * (((g - b) / d) % SECTORS)
            max == g -> SECTOR * ((b - r) / d + 2)
            else -> SECTOR * ((r - g) / d + 4)
        }.let { if (it < 0) it + FULL_TURN.toFloat() else it }
        return Triple(hue, if (max == 0f) 0f else d / max, max)
    }

    private fun channel(v: Float) = (v.coerceIn(0f, 1f) * 255).roundToInt()

    private fun lab(argb: Int): Triple<Double, Double, Double> {
        val r = linear((argb shr 16 and BYTE) / 255.0)
        val g = linear((argb shr 8 and BYTE) / 255.0)
        val b = linear((argb and BYTE) / 255.0)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / WHITE_X
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / WHITE_Z
        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun labToLinear(l: Double, a: Double, b: Double): DoubleArray {
        val fy = (l + 16) / 116
        val fx = fy + a / 500
        val fz = fy - b / 200
        val x = labInverse(fx) * WHITE_X
        val y = labInverse(fy)
        val z = labInverse(fz) * WHITE_Z
        return doubleArrayOf(
            3.2406 * x - 1.5372 * y - 0.4986 * z,
            -0.9689 * x + 1.8758 * y + 0.0415 * z,
            0.0557 * x - 0.2040 * y + 1.0570 * z,
        )
    }

    private fun argbOf(linearRgb: DoubleArray): Int {
        val (r, g, b) = linearRgb.map { (gamma(it.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, BYTE) }
        return OPAQUE or (r shl 16) or (g shl 8) or b
    }

    private fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun gamma(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
    private fun labF(t: Double) = if (t > LAB_E) cbrt(t) else (LAB_K * t + 16) / 116
    private fun labInverse(f: Double) = if (f * f * f > LAB_E) f * f * f else (116 * f - 16) / LAB_K

    // Row sums of the sRGB matrix, so greys come out with zero chroma.
    private const val WHITE_X = 0.9505
    private const val WHITE_Z = 1.089
    private const val LAB_E = 216.0 / 24389.0
    private const val LAB_K = 24389.0 / 27.0
    private const val FULL_TURN = 360.0
    private const val SECTOR = 60f
    private const val SECTORS = 6
    private const val CHROMA_STEP = 1.0
    private const val EPSILON = 1e-4
    private const val BYTE = 0xFF
    private const val OPAQUE = -0x1000000
}
