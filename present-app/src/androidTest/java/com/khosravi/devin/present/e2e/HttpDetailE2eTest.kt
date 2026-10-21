package com.khosravi.devin.present.e2e

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.IntentStubs.chooserTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HttpDetailE2eTest : E2eTest() {

    private val spec = TestData.Http(
        url = "https://api.example.com/v1/users?page=2",
        method = "POST",
        status = 201,
        statusText = "Created",
        requestHeaders = listOf("Content-Type" to "application/json", "X-Trace" to "abc"),
        requestBody = """{"name":"neo","role":"admin"}""",
        responseBody = """{"id":7,"name":"neo","tags":["a","b"],"nick":"neo"}""",
    )

    private fun openHttpDetail(http: TestData.Http = spec) {
        openLogList { TestData.http(http) }
        Ui.clickText("${http.method} ${java.net.URL(http.url).path}")
        Ui.waitForText("Overview")
    }

    private fun openTab(name: String) {
        Ui.clickText(name)
        // Pager pages are all composed; wait for the tab's own content to be on screen.
        Waits.require("tab $name visible") { Ui.exists(Ui.textOrDesc("Search in body")) || Ui.exists(Ui.text("(body is empty)")) }
    }

    /** ROW-10, HTTP-01, HTTP-03: tabs + overflow; Overview lists the fields in order with the URL/method/status values. */
    @Test
    fun overview_fieldsInOrder() {
        openHttpDetail()
        Ui.waitForText("Request")
        Ui.waitForText("Response")
        Ui.waitForDesc("More options")

        val labels = listOf("URL", "Method", "Protocol", "Status", "Response", "SSL", "Request time", "Response time",
            "Duration", "Request size", "Response size", "Total size")
        // "Response" is also a tab title; take the occurrence below the tab row.
        val tabBottom = Ui.bounds(Ui.waitForText("Overview")).bottom
        val tops = labels.map { label ->
            Ui.findAll(predicate = Ui.text(label)).first { Ui.bounds(it).top > tabBottom }.let { Ui.bounds(it).top }
        }
        assertEquals("overview order", tops.sorted(), tops)
        Ui.waitForText(spec.url)
        Ui.waitForText("POST")
        Ui.waitForText("HTTP/1.1")
        Ui.waitForText("Completed")
        Ui.waitForText("201 Created")
        Ui.waitForText("Yes")
        Ui.waitForText("42 ms")
    }

    /** HTTP-05, HTTP-07, HTTP-08: Request tab shows headers, the copy row and the body; "Copy body" copies it. */
    @Test
    fun requestTab_headersBodyAndCopyBody() {
        openHttpDetail()
        openTab("Request")
        Ui.waitFor("header line", predicate = Ui.textContains("X-Trace: abc"))
        Ui.waitForText("Copy body")
        Ui.waitForText("Copy cURL")
        Ui.waitFor("body key", predicate = Ui.textContains("\"role\""))

        Ui.clickText("Copy body")
        Toasts.waitFor("Copied")
        Waits.require("clipboard holds the body") { Clipboard.text()?.contains("\"role\"") == true }

        Ui.clickText("Copy cURL")
        Waits.require("clipboard holds a curl command") { Clipboard.text()?.let { it.contains("curl") && it.contains(spec.url) } == true }
    }

    /** HTTP-06: no body → "(body is empty)", no search field, no copy row. */
    @Test
    fun emptyBody_showsPlaceholder() {
        openHttpDetail(TestData.Http(method = "GET", url = "https://api.example.com/ping", requestBody = null, requestHeaders = emptyList()))
        openTab("Request")
        Ui.waitForText("(body is empty)")
        Ui.waitForText("(header is empty)")
        Ui.assertNotShown("search field", Ui.text("Search in body"))
        Ui.assertNotShown("copy row", Ui.text("Copy body"))
    }

    /** HTTP-09, HTTP-10, HTTP-11: literal search with an "i / n" counter that wraps; `{"` and ".." don't crash. */
    @Test
    fun bodySearch_counterAndLiteralMatching() {
        openHttpDetail()
        openTab("Response")
        val field = Ui.field("Search in body")

        Ui.setText(field, "neo")
        Ui.waitForDesc("Clear search")
        Ui.waitForText("1 / 2")
        Ui.clickDesc("Next match")
        Ui.waitForText("2 / 2")
        Ui.clickDesc("Next match")
        Ui.waitForText("1 / 2")
        Ui.clickDesc("Previous match")
        Ui.waitForText("2 / 2")

        // Literal matching: regex metacharacters are plain text. "{\"" and "((" used to be compiled as a regex
        // (".." matched everything, "(" crashed); the pretty-printed body contains none of them.
        listOf("{\"", "((", "..").forEach { query ->
            Ui.setText(searchField(), query)
            Ui.waitGone("counter bar for $query", predicate = Ui.textMatches(Regex("""\d+ / \d+""")))
        }
        // A literal "\": " occurs once per JSON key.
        Ui.setText(searchField(), "\": ")
        Ui.waitForText("1 / 4")
        Ui.clickDesc("Clear search")
        Ui.waitForText("Search in body")
    }

    /** HTTP-14: the overflow lists the seven actions in order. */
    @Test
    fun overflowMenu_sevenActionsInOrder() {
        openHttpDetail()
        Ui.clickDesc("More options")
        val items = listOf("Share HAR (whole log) as json", "Share CURL command", "Copy HAR (whole log)", "Copy CURL command",
            "Copy response content", "Copy request content", "Save HAR as json")
        val tops = items.map { Ui.bounds(Ui.waitForText(it)).top }
        assertEquals(tops.sorted(), tops)
        Ui.back()
    }

    /** HTTP-17, HTTP-18, HTTP-19: copy HAR / cURL / response content from the menu. */
    @Test
    fun menuCopies() {
        openHttpDetail()
        menu("Copy HAR (whole log)")
        Toasts.waitFor("Copied")
        Waits.require("HAR json on clipboard") { Clipboard.text()?.let { it.contains("\"HAR\"") && it.contains(spec.url) } == true }

        menu("Copy CURL command")
        Waits.require("curl on clipboard") { Clipboard.text()?.let { it.startsWith("curl") && it.contains(spec.url) } == true }

        menu("Copy response content")
        Waits.require("response body on clipboard") { Clipboard.text() == spec.responseBody }

        menu("Copy request content")
        Waits.require("request body on clipboard") { Clipboard.text() == spec.requestBody }
    }

    /** HTTP-20: a request without body → "Response is null" toast. */
    @Test
    fun copyRequestContent_withoutBody_toasts() {
        openHttpDetail(TestData.Http(method = "GET", url = "https://api.example.com/ping", requestBody = null))
        menu("Copy request content")
        Toasts.waitFor("Response is null")
    }

    /** HTTP-15, HTTP-16: share HAR → "Share file through" application/json; share cURL → text/plain. */
    @Test
    fun shareHarAndCurl() {
        IntentStubs.stubSystemUi()
        openHttpDetail()

        menu("Share HAR (whole log) as json")
        val har = IntentStubs.waitForStarted("HAR share") { it.action == Intent.ACTION_CHOOSER && it.chooserTarget()?.type == "application/json" }
        assertEquals("Share file through", har.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())

        menu("Share CURL command")
        val curl = IntentStubs.waitForStarted("cURL share") { it.action == Intent.ACTION_CHOOSER && it.chooserTarget()?.type == "text/plain" }
        assertEquals("Share CURL command", curl.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        assertTrue(curl.chooserTarget()!!.getStringExtra(Intent.EXTRA_TEXT)!!.contains(spec.url))
    }

    /** HTTP-21: Save HAR opens a .json create-document picker; the saved file holds the HAR entry. */
    @Test
    fun saveHar_writesFile() {
        val out = File(AppState.e2eDir(), "har.json")
        IntentStubs.stub(
            { it.action == Intent.ACTION_CHOOSER && it.chooserTarget()?.action == Intent.ACTION_CREATE_DOCUMENT },
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(out)),
        )
        openHttpDetail()

        menu("Save HAR as json")

        val chooser = IntentStubs.waitForStarted("save picker") { it.action == Intent.ACTION_CHOOSER }
        assertEquals("Save HAR as json", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        assertTrue(chooser.chooserTarget()!!.getStringExtra(Intent.EXTRA_TITLE)!!.matches(Regex("""Devin_.+\.json""")))
        Toasts.waitFor("Save done")
        Waits.require("file written") { out.length() > 0 }
        val json = JSONObject(out.readText())
        assertTrue(json.getJSONObject("HAR").getJSONObject("request").getString("url") == spec.url)
    }

    private fun searchField() = Ui.waitFor("visible search field", predicate = Ui.editable)

    private fun menu(item: String) {
        Ui.clickDesc("More options")
        Ui.clickText(item)
        Ui.waitGone("menu", predicate = Ui.text(item))
    }
}
