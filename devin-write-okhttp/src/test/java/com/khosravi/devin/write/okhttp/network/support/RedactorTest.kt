package com.khosravi.devin.write.okhttp.network.support

import com.google.gson.JsonParser
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    private val redactor = Redactor()

    private fun masked(value: String?) = value != null && value.matches(Regex("""\[REDACTED#[0-9a-f]{4}]"""))

    @Test
    fun `credential headers are masked by default and other headers kept`() {
        val headers = redactor.redactHeaders(
            headersOf("Authorization", "Bearer abc", "Set-Cookie", "sid=1", "Content-Type", "application/json")
        )
        assertTrue(masked(headers["Authorization"]))
        assertTrue(masked(headers["Set-Cookie"]))
        assertEquals("application/json", headers["Content-Type"])
    }

    @Test
    fun `headers without sensitive names are returned as is`() {
        val headers = headersOf("Content-Type", "application/json", "Accept", "*/*")
        assertSame(headers, redactor.redactHeaders(headers))
    }

    @Test
    fun `sensitive query parameters are masked and their order kept`() {
        val url = redactor.redactUrl("https://api.x.io/login?access_token=T1&page=2&phone=09121234567".toHttpUrl())
        assertEquals(listOf("access_token", "page", "phone"), url.queryParameterNames.toList())
        assertTrue(masked(url.queryParameter("access_token")))
        assertTrue(masked(url.queryParameter("phone")))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun `a url without sensitive parameters is returned as is`() {
        val url = "https://api.x.io/rides?page=2&sort=asc".toHttpUrl()
        assertSame(url, redactor.redactUrl(url))
    }

    @Test
    fun `json fields are masked by the words in their names`() {
        val json = redactor.redactJson(
            JsonParser.parseString(
                """{"user":{"phoneNumber":"0912","refresh_token":"r","name":"Ali","age":30},"footprint":"keep","otp":1234}"""
            )
        ).asJsonObject
        val user = json.getAsJsonObject("user")
        assertTrue(masked(user["phoneNumber"].asString))
        assertTrue(masked(user["refresh_token"].asString))
        assertEquals("Ali", user["name"].asString)
        assertEquals(30, user["age"].asInt)
        assertTrue(masked(json["otp"].asString))
        // "footprint" contains the letters "otp", but not the word.
        assertEquals("keep", json["footprint"].asString)
    }

    @Test
    fun `null and boolean values of sensitive fields stay visible`() {
        val json = redactor.redactJson(JsonParser.parseString("""{"email_verified":true,"accessToken":null}""")).asJsonObject
        assertTrue(json["email_verified"].asBoolean)
        assertTrue(json["accessToken"].isJsonNull)
    }

    @Test
    fun `a sensitive field holding an object or array is masked whole`() {
        val json = redactor.redactJson(JsonParser.parseString("""{"secret":{"a":1},"tokens":["t1","t2"]}""")).asJsonObject
        assertTrue(masked(json["secret"].asString))
        assertTrue(masked(json["tokens"].asString))
    }

    @Test
    fun `emails, phone numbers and bearer tokens are masked inside any text`() {
        val text = redactor.redactText("mail a.b@example.com, call +14155550123 or 09121234567, Bearer abcdefghijkl, at 1792661154476")
        assertFalse(text.contains("a.b@example.com"))
        assertFalse(text.contains("4155550123"))
        assertFalse(text.contains("09121234567"))
        assertFalse(text.contains("abcdefghijkl"))
        // A timestamp is not a phone number.
        assertTrue(text.contains("1792661154476"))
    }

    @Test
    fun `sensitive pairs in an error message url are masked`() {
        val text = redactor.redactText("SocketTimeoutException: timeout for https://x.io/a?token=T0K3N&id=7")
        assertFalse(text.contains("T0K3N"))
        assertTrue(text.contains("id=7"))
    }

    @Test
    fun `form bodies are masked by field name`() {
        val body = redactor.redactBody("username=ali&password=hunter2&remember=1", "application/x-www-form-urlencoded")!!
        assertEquals("username=ali", body.substringBefore("&"))
        assertFalse(body.contains("hunter2"))
        assertTrue(body.endsWith("&remember=1"))
    }

    @Test
    fun `the same value gets the same fingerprint, another value another one`() {
        assertEquals(Redactor.mask("abc"), Redactor.mask("abc"))
        assertNotEquals(Redactor.mask("abc"), Redactor.mask("abd"))
    }

    @Test
    fun `with defaults off only the app's rules apply`() {
        val custom = Redactor().apply {
            defaultsEnabled = false
            addFields(listOf("national_code"))
        }
        val json = custom.redactJson(JsonParser.parseString("""{"password":"p","nationalCode":"0012345678"}""")).asJsonObject
        assertEquals("p", json["password"].asString)
        assertTrue(masked(json["nationalCode"].asString))
        assertEquals("Bearer abcdefghijkl", custom.redactText("Bearer abcdefghijkl"))
    }

    @Test
    fun `app headers and patterns are added to the defaults`() {
        val custom = Redactor().apply {
            addHeaders(listOf("X-Device-Id"))
            addPatterns(listOf(Regex("""SNP-\d{6}""")))
        }
        assertTrue(masked(custom.redactHeaders(headersOf("x-device-id", "dev-1"))["x-device-id"]))
        assertFalse(custom.redactText("ride SNP-123456 done").contains("SNP-123456"))
        assertTrue(masked(custom.redactHeaders(headersOf("Authorization", "a"))["Authorization"]))
    }

    @Test
    fun `changing the rules after a field was checked takes effect`() {
        val custom = Redactor()
        val before = custom.redactJson(JsonParser.parseString("""{"deviceId":"d1"}""")).asJsonObject
        assertEquals("d1", before["deviceId"].asString)
        custom.addFields(listOf("deviceId"))
        val after = custom.redactJson(JsonParser.parseString("""{"deviceId":"d1"}""")).asJsonObject
        assertTrue(masked(after["deviceId"].asString))
    }
}
