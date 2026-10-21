package io.github.zaxar1231.mycycle.core.domain.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ThemeMathTest {
    @Test
    fun `tones keep the seed hue and follow lightness`() {
        val seed = 0xFFD4728A.toInt()
        val (hue, chroma) = Tones.hueChroma(seed)
        val light = Tones.color(hue, chroma, 90f)
        val dark = Tones.color(hue, chroma, 20f)
        assertEquals(90f, Tones.tone(light), 1.5f)
        assertEquals(20f, Tones.tone(dark), 1.5f)
        val (lightHue, _) = Tones.hueChroma(light)
        assertTrue(abs(lightHue - hue) < 8f, "hue drifted from $hue to $lightHue")
    }

    @Test
    fun `extreme tones are white and black`() {
        assertEquals(0xFFFFFFFF.toInt(), Tones.color(30f, 40f, 100f))
        assertEquals(0xFF000000.toInt(), Tones.color(30f, 40f, 0f))
    }

    @Test
    fun `hsv round trip`() {
        val color = Tones.fromHsv(200f, 0.6f, 0.8f)
        val (h, s, v) = Tones.toHsv(color)
        assertEquals(200f, h, 1f)
        assertEquals(0.6f, s, 0.01f)
        assertEquals(0.8f, v, 0.01f)
        assertEquals(0xFFFF0000.toInt(), Tones.fromHsv(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), Tones.fromHsv(120f, 1f, 1f))
    }

    @Test
    fun `moon is new on day one and full on ovulation`() {
        val start = MoonPhase.of(cycleDay = 1, cycleLength = 28, ovulationDay = 14)
        assertEquals(0f, start.illumination, 0.001f)
        assertTrue(start.waxing)
        val full = MoonPhase.of(cycleDay = 14, cycleLength = 28, ovulationDay = 14)
        assertEquals(1f, full.illumination, 0.001f)
        val waning = MoonPhase.of(cycleDay = 21, cycleLength = 28, ovulationDay = 14)
        assertFalse(waning.waxing)
        assertTrue(waning.illumination in 0.3f..0.9f)
        assertTrue(MoonPhase.of(28, 28, 14).illumination < 0.1f)
    }
}
