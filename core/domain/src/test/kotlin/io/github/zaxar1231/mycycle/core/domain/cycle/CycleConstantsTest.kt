package io.github.zaxar1231.mycycle.core.domain.cycle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CycleConstantsTest {
    @Test
    fun `default cycle is inside the regular range`() {
        assertTrue(CycleConstants.DEFAULT_CYCLE_LENGTH in
            CycleConstants.MIN_REGULAR_CYCLE_LENGTH..CycleConstants.MAX_REGULAR_CYCLE_LENGTH)
    }

    @Test
    fun `fertile window and luteal phase fit into the shortest regular cycle`() {
        val needed = CycleConstants.LUTEAL_PHASE_LENGTH + CycleConstants.FERTILE_DAYS_BEFORE_OVULATION + 1
        assertTrue(needed <= CycleConstants.MIN_REGULAR_CYCLE_LENGTH)
    }

    @Test
    fun `default period is inside the allowed range`() {
        assertTrue(CycleConstants.DEFAULT_PERIOD_LENGTH in
            CycleConstants.MIN_PERIOD_LENGTH..CycleConstants.MAX_PERIOD_LENGTH)
    }
}
