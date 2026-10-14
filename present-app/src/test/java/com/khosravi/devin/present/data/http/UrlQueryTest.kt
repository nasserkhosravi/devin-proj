package com.khosravi.devin.present.data.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.MalformedURLException

class UrlQueryTest {

    @Test
    fun `exposes url parts`() {
        val query = UrlQuery.create("https://api.example.com/v1/users?id=7")
        assertEquals("api.example.com", query.domain)
        assertEquals("https", query.protocol)
        assertEquals("/v1/users", query.path)
        assertEquals("https://api.example.com/v1/users?id=7", query.urlExternalForm)
    }

    @Test
    fun `https is ssl regardless of case`() {
        assertTrue(UrlQuery.create("HTTPS://example.com").isSsl)
    }

    @Test
    fun `http is not ssl`() {
        assertFalse(UrlQuery.create("http://example.com/").isSsl)
    }

    @Test
    fun `empty path for bare host`() {
        assertEquals("", UrlQuery.create("http://example.com").path)
    }

    @Test(expected = MalformedURLException::class)
    fun `invalid url throws`() {
        UrlQuery.create("not a url")
    }
}
