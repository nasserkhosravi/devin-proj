package com.khosravi.devin.present.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** [isForceUpdate] is set by [UpdateChecker] when the release bumps the major version of the installed app. */
data class ReleaseInfo(val version: AppVersion, val pageUrl: String, val isForceUpdate: Boolean = false)

/**
 * Reads presenter releases from GitHub. The repo also publishes library releases (`write/v…`,
 * `okhttp-write/v…`), so `/releases/latest` is not usable; we list releases and keep only ones
 * whose tag parses as an [AppVersion] and that ship an `.apk` asset.
 */
class GitHubReleaseSource(private val currentVersionName: String) {

    /** Blocking; call off the main thread. Returns null on any non-200 response. */
    fun fetchLatestPresenterRelease(): ReleaseInfo? {
        val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Devin-Presenter/$currentVersionName")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseLatestPresenterRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASES_PAGE_URL = "https://github.com/nasserkhosravi/devin-proj/releases"
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/nasserkhosravi/devin-proj/releases?per_page=30"
        private const val TIMEOUT_MILLIS = 10_000

        fun parseLatestPresenterRelease(json: String): ReleaseInfo? {
            val releases = JSONArray(json)
            return (0 until releases.length())
                .map { releases.getJSONObject(it) }
                .filterNot { it.optBoolean("draft") || it.optBoolean("prerelease") }
                .filter { it.hasApkAsset() }
                .mapNotNull { release ->
                    val version = AppVersion.parse(release.optString("tag_name")) ?: return@mapNotNull null
                    ReleaseInfo(version, release.optString("html_url").ifEmpty { RELEASES_PAGE_URL })
                }
                .maxByOrNull { it.version }
        }

        private fun JSONObject.hasApkAsset(): Boolean {
            val assets = optJSONArray("assets") ?: return false
            return (0 until assets.length()).any {
                assets.getJSONObject(it).optString("name").endsWith(".apk", ignoreCase = true)
            }
        }
    }
}
