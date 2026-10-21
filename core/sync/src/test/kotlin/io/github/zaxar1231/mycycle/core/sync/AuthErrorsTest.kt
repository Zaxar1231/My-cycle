package io.github.zaxar1231.mycycle.core.sync

import io.github.zaxar1231.mycycle.core.domain.account.AuthError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthErrorsTest {
    @Test
    fun `new and old server responses map to the same errors`() {
        assertEquals(AuthError.INVALID_CREDENTIALS, AuthErrors.map(400, "invalid_credentials", "Invalid login credentials"))
        assertEquals(AuthError.INVALID_CREDENTIALS, AuthErrors.map(400, null, "Invalid login credentials"))
        assertEquals(AuthError.EMAIL_NOT_CONFIRMED, AuthErrors.map(400, "email_not_confirmed", null))
        assertEquals(AuthError.ALREADY_REGISTERED, AuthErrors.map(422, "user_already_exists", null))
        assertEquals(AuthError.ALREADY_REGISTERED, AuthErrors.map(400, null, "User already registered"))
        assertEquals(AuthError.WEAK_PASSWORD, AuthErrors.map(422, "weak_password", null))
        assertEquals(AuthError.INVALID_EMAIL, AuthErrors.map(400, "email_address_invalid", null))
        assertEquals(AuthError.RATE_LIMITED, AuthErrors.map(429, null, null))
        assertEquals(AuthError.RATE_LIMITED, AuthErrors.map(400, "over_email_send_rate_limit", null))
        assertEquals(AuthError.UNKNOWN, AuthErrors.map(500, null, "boom"))
    }
}
