package io.github.zaxar1231.mycycle.core.sync

import io.github.zaxar1231.mycycle.core.domain.account.Account
import io.github.zaxar1231.mycycle.core.domain.account.AccountRepository
import io.github.zaxar1231.mycycle.core.domain.account.AccountRules
import io.github.zaxar1231.mycycle.core.domain.account.AuthError
import io.github.zaxar1231.mycycle.core.domain.account.AuthResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase Auth over plain HTTPS (no SDK): sign-up, password sign-in, reset link, refresh and
 * sign-out. Only the email and password ever go to the server here; no health data.
 */
@Singleton
class SupabaseAccountRepository @Inject constructor(private val sessions: SessionStore) : AccountRepository {
    private val url = BuildConfig.SUPABASE_URL
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val refreshLock = Mutex()

    // Plain HTTP would expose passwords, so only an https address enables accounts.
    override val isAvailable: Boolean = url.startsWith("https://") && anonKey.isNotEmpty()

    private val _account = MutableStateFlow(sessions.read()?.let { Account(it.userId, it.email) })
    override val account: StateFlow<Account?> = _account.asStateFlow()

    override suspend fun signUp(email: String, password: String): AuthResult = call {
        val response = post("/auth/v1/signup", JSONObject().put("email", AccountRules.normalizeEmail(email)).put("password", password))
        when {
            !response.ok -> AuthResult.Failed(response.error())
            // With "Confirm email" on, the server answers with a user but no session.
            response.json.has("access_token") -> signedIn(response.json)
            else -> AuthResult.ConfirmEmail
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult = call {
        val body = JSONObject().put("email", AccountRules.normalizeEmail(email)).put("password", password)
        val response = post("/auth/v1/token?grant_type=password", body)
        if (response.ok) signedIn(response.json) else AuthResult.Failed(response.error())
    }

    override suspend fun resetPassword(email: String): AuthResult = call {
        val response = post("/auth/v1/recover", JSONObject().put("email", AccountRules.normalizeEmail(email)))
        if (response.ok) AuthResult.ResetSent else AuthResult.Failed(response.error())
    }

    override suspend fun signOut() {
        val session = sessions.read()
        sessions.clear()
        _account.value = null
        // Best effort: the local session is already gone even if the server is unreachable.
        if (session != null && isAvailable) runCatching { post("/auth/v1/logout", JSONObject(), bearer = session.accessToken) }
    }

    override suspend fun accessToken(): String? = refreshLock.withLock {
        val session = sessions.read() ?: return null
        if (System.currentTimeMillis() < session.expiresAt - REFRESH_MARGIN_MILLIS) return session.accessToken
        val response = runCatching {
            post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", session.refreshToken))
        }.getOrNull() ?: return null
        when {
            response.ok -> sessionOf(response.json)?.also { sessions.write(it) }?.accessToken
            // A rejected refresh token means the session is over (revoked or signed out elsewhere).
            response.code in CLIENT_ERRORS -> {
                sessions.clear()
                _account.value = null
                null
            }
            else -> null
        }
    }

    private fun signedIn(json: JSONObject): AuthResult {
        val session = sessionOf(json) ?: return AuthResult.Failed(AuthError.UNKNOWN)
        sessions.write(session)
        val account = Account(session.userId, session.email)
        _account.value = account
        return AuthResult.SignedIn(account)
    }

    private fun sessionOf(json: JSONObject): Session? = runCatching {
        val user = json.getJSONObject("user")
        Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + json.getLong("expires_in") * MILLIS_PER_SECOND,
            userId = user.getString("id"),
            email = user.optString("email"),
        )
    }.getOrNull()

    private suspend fun call(block: suspend () -> AuthResult): AuthResult {
        if (!isAvailable) return AuthResult.Failed(AuthError.NOT_CONFIGURED)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AuthResult.Failed(AuthError.NETWORK)
        } catch (e: Exception) {
            // An unexpected answer or a Keystore failure: reported, never a crash.
            AuthResult.Failed(AuthError.UNKNOWN)
        }
    }

    private class Response(val code: Int, val json: JSONObject) {
        val ok get() = code in SUCCESS
        fun error() = AuthErrors.map(
            code,
            json.optString("error_code").ifEmpty { json.optString("error") }.ifEmpty { null },
            json.optString("msg").ifEmpty { json.optString("error_description") }.ifEmpty { json.optString("message") },
        )
    }

    private suspend fun post(path: String, body: JSONObject, bearer: String? = null): Response = withContext(Dispatchers.IO) {
        val connection = (URL(url + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${bearer ?: anonKey}")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in SUCCESS) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
        const val MILLIS_PER_SECOND = 1_000L
        const val REFRESH_MARGIN_MILLIS = 60_000L
        val SUCCESS = 200..299
        val CLIENT_ERRORS = 400..499
    }
}
