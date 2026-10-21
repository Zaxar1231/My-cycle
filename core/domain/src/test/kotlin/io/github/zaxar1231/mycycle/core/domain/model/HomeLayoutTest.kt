package io.github.zaxar1231.mycycle.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class HomeLayoutTest {
    @Test
    fun `encode and decode round trip`() {
        val layout = HomeLayout.DEFAULT
            .moved(from = 4, to = 0)
            .withStyle(HomeBlock.TODAY, BlockStyle(visible = false, look = BlockLook.GLASS, size = BlockSize.LARGE, cornerDp = 12, color = 0xFF8E6BBF.toInt()))
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals(layout.normalized().order, decoded.order)
        assertEquals(layout.style(HomeBlock.TODAY), decoded.style(HomeBlock.TODAY))
        assertEquals(HomeBlock.AFFIRMATION, decoded.order.first())
    }

    @Test
    fun `broken or old text falls back to defaults and keeps every block`() {
        assertEquals(HomeLayout.DEFAULT.order, HomeLayoutCodec.decode(null).order)
        val partial = HomeLayoutCodec.decode("TODAY,0,FILLED,NORMAL,28,-;NOPE,1,X,Y,1,-;TODAY,1,FILLED,NORMAL,28,-;garbage")
        assertEquals(HomeBlock.entries.size, partial.order.size)
        assertEquals(HomeBlock.TODAY, partial.order.first())
        assertFalse(partial.style(HomeBlock.TODAY).visible)
        assertEquals(BlockStyle.MAX_CORNER, HomeLayoutCodec.decode("HERO,1,NONE,NORMAL,999,-").style(HomeBlock.HERO).cornerDp)
    }

    @Test
    fun `moving keeps the other blocks in order`() {
        val moved = HomeLayout.DEFAULT.moved(from = 0, to = 2)
        assertEquals(listOf(HomeBlock.KEY_DATES, HomeBlock.CALENDAR, HomeBlock.HERO, HomeBlock.TODAY, HomeBlock.AFFIRMATION), moved.order)
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.moved(0, 9))
    }
}
