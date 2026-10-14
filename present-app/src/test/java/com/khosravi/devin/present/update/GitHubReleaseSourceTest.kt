package com.khosravi.devin.present.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseSourceTest {

    private fun release(
        tag: String,
        assets: List<String> = listOf("devin-presenter-app-release.apk"),
        htmlUrl: String? = "https://github.com/x/releases/tag/$tag",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): JSONObject = JSONObject().apply {
        put("tag_name", tag)
        if (htmlUrl != null) put("html_url", htmlUrl)
        put("draft", draft)
        put("prerelease", prerelease)
        put("assets", JSONArray(assets.map { JSONObject().put("name", it) }))
    }

    private fun parse(vararg releases: JSONObject): ReleaseInfo? =
        GitHubReleaseSource.parseLatestPresenterRelease(JSONArray(releases.toList()).toString())

    @Test
    fun `picks the highest presenter version regardless of order`() {
        val result = parse(release("presenter_4.4.0"), release("presenter_4.10.0"), release("presenter_4.5.0"))
        assertEquals(AppVersion(4, 10, 0), result?.version)
        assertEquals("https://github.com/x/releases/tag/presenter_4.10.0", result?.pageUrl)
    }

    @Test
    fun `ignores library releases`() {
        val result = parse(release("write_9.0.0", assets = emptyList()), release("presenter_4.5.0"))
        assertEquals(AppVersion(4, 5, 0), result?.version)
    }

    @Test
    fun `ignores releases without an apk asset`() {
        val result = parse(release("presenter_5.0.0", assets = listOf("notes.txt")), release("presenter_4.5.0"))
        assertEquals(AppVersion(4, 5, 0), result?.version)
    }

    @Test
    fun `apk asset match is case-insensitive`() {
        assertEquals(AppVersion(4, 5, 0), parse(release("presenter_4.5.0", assets = listOf("App.APK")))?.version)
    }

    @Test
    fun `ignores drafts and prereleases`() {
        val result = parse(
            release("presenter_6.0.0", draft = true),
            release("presenter_5.0.0", prerelease = true),
            release("presenter_4.5.0"),
        )
        assertEquals(AppVersion(4, 5, 0), result?.version)
    }

    @Test
    fun `falls back to releases page when html_url is missing`() {
        assertEquals(GitHubReleaseSource.RELEASES_PAGE_URL, parse(release("presenter_4.5.0", htmlUrl = null))?.pageUrl)
    }

    @Test
    fun `release info is not a force update by default`() {
        assertEquals(false, parse(release("presenter_4.5.0"))?.isForceUpdate)
    }

    @Test
    fun `returns null when nothing qualifies`() {
        assertNull(parse())
        assertNull(parse(release("write_4.4.0"), release("presenter_4.5.0", assets = emptyList())))
    }

    @Test(expected = org.json.JSONException::class)
    fun `malformed json throws`() {
        GitHubReleaseSource.parseLatestPresenterRelease("{not an array}")
    }
}
