package io.github.zaxar1231.mycycle.core.domain.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityTest {
    @Test
    fun `only four digits make a PIN`() {
        assertTrue(PinPolicy.isValid("0123"))
        assertFalse(PinPolicy.isValid("123"))
        assertFalse(PinPolicy.isValid("12345"))
        assertFalse(PinPolicy.isValid("12a4"))
        assertFalse(PinPolicy.isValid("١٢٣٤"))
    }

    @Test
    fun `lockout starts after the free attempts and doubles up to the cap`() {
        assertEquals(0L, PinPolicy.lockoutAfter(4))
        assertEquals(30_000L, PinPolicy.lockoutAfter(5))
        assertEquals(60_000L, PinPolicy.lockoutAfter(6))
        assertEquals(120_000L, PinPolicy.lockoutAfter(7))
        assertEquals(PinPolicy.MAX_LOCKOUT_MILLIS, PinPolicy.lockoutAfter(11))
        assertEquals(PinPolicy.MAX_LOCKOUT_MILLIS, PinPolicy.lockoutAfter(1_000))
    }

    @Test
    fun `auto lock waits for the chosen time`() {
        assertTrue(AutoLock.shouldLock(backgroundAt = 1_000, now = 1_000, lockAfterMinutes = 0))
        assertFalse(AutoLock.shouldLock(backgroundAt = 0, now = 59_999, lockAfterMinutes = 1))
        assertTrue(AutoLock.shouldLock(backgroundAt = 0, now = 60_000, lockAfterMinutes = 1))
        assertTrue(AutoLock.shouldLock(backgroundAt = 10_000, now = 5_000, lockAfterMinutes = 5))
    }
}
