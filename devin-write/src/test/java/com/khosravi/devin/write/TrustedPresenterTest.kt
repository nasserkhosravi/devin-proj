package com.khosravi.devin.write

import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedPresenterTest {

    private val digest = "9be88a48039809b66845d6fd375fedb45f51960331eb182018ca1b39593c00b8"

    @Test
    fun `digests are normalized from keytool and apksigner formats`() {
        val colons = digest.uppercase().chunked(2).joinToString(":")
        val presenter = TrustedPresenter("com.example.presenter", colons, digest.uppercase(), digest)
        assertEquals(listOf(digest, digest, digest), presenter.sha256CertDigests)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `at least one digest is required`() {
        TrustedPresenter("com.example.presenter", emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a digest must have 64 hex characters`() {
        TrustedPresenter("com.example.presenter", "AB:CD")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a digest must be hex`() {
        TrustedPresenter("com.example.presenter", "z".repeat(64))
    }
}
