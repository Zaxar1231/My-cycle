package io.github.zaxar1231.mycycle.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionTest {
    @Test
    fun comparesNumericParts() {
        assertTrue(Version.isNewer("0.1.10", "0.1.9"))
        assertTrue(Version.isNewer("1.0.0", "0.99.99"))
        assertFalse(Version.isNewer("0.1.0", "0.1.0"))
        assertFalse(Version.isNewer("0.1.0", "0.2.0"))
    }

    @Test
    fun ignoresPrefixAndSuffix() {
        assertEquals(0, Version.compare("v1.2.3", "1.2.3"))
        assertEquals(0, Version.compare("1.2.3-beta", "1.2.3"))
        assertTrue(Version.isNewer("v1.2", "1.1.9"))
    }

    @Test
    fun treatsMissingPartsAsZero() {
        assertEquals(0, Version.compare("1.2", "1.2.0"))
        assertEquals(listOf(1, 0, 0), Version.parse("v1.x.0"))
    }
}
