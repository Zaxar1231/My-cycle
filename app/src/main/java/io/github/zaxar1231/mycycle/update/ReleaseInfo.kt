package io.github.zaxar1231.mycycle.update

import org.json.JSONObject

data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
) {
    companion object {
        /** Parses a GitHub `releases/latest` API response. */
        fun fromGitHubJson(json: String): ReleaseInfo {
            val obj = JSONObject(json)
            val assets = obj.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size")
                        break
                    }
                }
            }
            val tag = obj.getString("tag_name")
            return ReleaseInfo(
                version = tag.removePrefix("v"),
                title = obj.optString("name").ifBlank { tag },
                notes = obj.optString("body"),
                pageUrl = obj.optString("html_url"),
                apkUrl = apkUrl,
                apkSize = apkSize,
            )
        }
    }
}
