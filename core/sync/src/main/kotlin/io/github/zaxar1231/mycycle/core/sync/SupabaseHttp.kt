package io.github.zaxar1231.mycycle.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** The project address and key from the build, and plain HTTPS calls to it (no SDK). */
@Singleton
class SupabaseHttp @Inject constructor() {
    private val url = BuildConfig.SUPABASE_URL
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    // Plain HTTP would expose passwords and tokens, so only an https address enables the server.
    val isConfigured: Boolean = url.startsWith("https://") && anonKey.isNotEmpty()

    class Response(val code: Int, val body: String) {
        val ok get() = code in SUCCESS
    }

    /** Sends [body] (JSON) to [path]; without [bearer] the request goes as the anonymous role. */
    suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        bearer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response = withContext(Dispatchers.IO) {
        val connection = (URL(url + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${bearer ?: anonKey}")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            doOutput = body != null
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in SUCCESS) connection.inputStream else connection.errorStream
            Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
        val SUCCESS = 200..299
    }
}
