package io.github.zaxar1231.mycycle.core.sync

import io.github.zaxar1231.mycycle.core.domain.account.AuthError

/**
 * Maps Supabase Auth (GoTrue) error responses to [AuthError]. Newer servers send an
 * `error_code`, older ones only `error` and a message, so both are read.
 */
internal object AuthErrors {
    private const val TOO_MANY_REQUESTS = 429

    fun map(httpCode: Int, errorCode: String?, message: String?): AuthError {
        val code = errorCode.orEmpty().lowercase()
        val text = message.orEmpty().lowercase()
        return when {
            httpCode == TOO_MANY_REQUESTS || code.startsWith("over_") -> AuthError.RATE_LIMITED
            code == "invalid_credentials" || "invalid login credentials" in text -> AuthError.INVALID_CREDENTIALS
            code == "email_not_confirmed" || "email not confirmed" in text -> AuthError.EMAIL_NOT_CONFIRMED
            code == "user_already_exists" || code == "email_exists" || "already registered" in text -> AuthError.ALREADY_REGISTERED
            code == "weak_password" || "password should be" in text -> AuthError.WEAK_PASSWORD
            code == "email_address_invalid" || code == "validation_failed" || "invalid format" in text -> AuthError.INVALID_EMAIL
            else -> AuthError.UNKNOWN
        }
    }
}
