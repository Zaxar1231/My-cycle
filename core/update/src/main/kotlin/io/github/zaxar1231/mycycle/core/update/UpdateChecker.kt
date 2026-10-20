package io.github.zaxar1231.mycycle.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface CheckResult {
    data object UpToDate : CheckResult
    data object NoReleases : CheckResult
    data class Available(val release: ReleaseInfo) : CheckResult
}

/** Looks for a newer version among the GitHub Releases of this repository. */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"

    suspend fun check(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "TwilightCycle/$currentVersion")
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val release = ReleaseInfo.fromGitHubJson(conn.inputStream.bufferedReader().use { it.readText() })
                    if (Version.isNewer(release.version, currentVersion)) CheckResult.Available(release)
                    else CheckResult.UpToDate
                }
                HttpURLConnection.HTTP_NOT_FOUND -> CheckResult.NoReleases
                else -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
