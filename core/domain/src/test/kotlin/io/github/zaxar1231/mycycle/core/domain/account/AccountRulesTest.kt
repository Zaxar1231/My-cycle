package io.github.zaxar1231.mycycle.core.domain.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountRulesTest {
    @Test
    fun `emails are trimmed, lowercased and checked`() {
        assertEquals("anna@example.org", AccountRules.normalizeEmail("  Anna@Example.ORG "))
        assertTrue(AccountRules.isValidEmail("anna@example.org"))
        assertTrue(AccountRules.isValidEmail(" Anna@Example.org "))
        assertFalse(AccountRules.isValidEmail("anna@example"))
        assertFalse(AccountRules.isValidEmail("anna example@x.org"))
        assertFalse(AccountRules.isValidEmail(""))
    }

    @Test
    fun `passwords need eight characters`() {
        assertFalse(AccountRules.isValidPassword("1234567"))
        assertTrue(AccountRules.isValidPassword("12345678"))
    }
}
