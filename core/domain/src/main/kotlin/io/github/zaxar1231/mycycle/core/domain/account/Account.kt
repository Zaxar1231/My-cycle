package io.github.zaxar1231.mycycle.core.domain.account

import kotlinx.coroutines.flow.StateFlow

/** A signed-in account. Only the email and the server's user id; no health data lives here. */
data class Account(val userId: String, val email: String)

enum class AuthError {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    ALREADY_REGISTERED,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    RATE_LIMITED,
    NETWORK,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface AuthResult {
    data class SignedIn(val account: Account) : AuthResult

    /** Registered; the server sent a confirmation link before the first sign-in. */
    data object ConfirmEmail : AuthResult

    /** A password reset link was sent, if the address has an account. */
    data object ResetSent : AuthResult

    data class Failed(val error: AuthError) : AuthResult
}

/**
 * Accounts are optional and only needed for partner mode and backups. The server is behind
 * this interface, so Supabase can be replaced by a self-hosted backend later.
 */
interface AccountRepository {
    /** False when this build has no server configured; then account features stay hidden. */
    val isAvailable: Boolean
    val account: StateFlow<Account?>

    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun resetPassword(email: String): AuthResult
    suspend fun signOut()

    /** A valid access token for server calls, refreshed when needed; null when signed out. */
    suspend fun accessToken(): String?
}

object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8
    private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun normalizeEmail(email: String) = email.trim().lowercase()

    fun isValidEmail(email: String) = EMAIL.matches(normalizeEmail(email))

    fun isValidPassword(password: String) = password.length >= MIN_PASSWORD_LENGTH
}
