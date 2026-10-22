package com.khosravi.devin.write

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.write.testing.DevinTestFixture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PresenterVerifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val digest = "9be88a48039809b66845d6fd375fedb45f51960331eb182018ca1b39593c00b8"
    private val otherDigest = "1".repeat(64)

    /** Records each signature check and answers with [signedWith]. */
    private class FakeSignatures(val signedWith: Set<String>) : (PackageManager, String, ByteArray) -> Boolean {
        val checks = mutableListOf<Pair<String, String>>()
        override fun invoke(pm: PackageManager, packageName: String, sha256: ByteArray): Boolean {
            val hex = sha256.joinToString("") { "%02x".format(it) }
            checks += packageName to hex
            return hex in signedWith
        }
    }

    private fun installPresenter(packageName: String) {
        shadowOf(context.packageManager).addOrUpdateProvider(ProviderInfo().apply {
            authority = DevinTestFixture.AUTHORITY
            this.packageName = packageName
            name = DevinContentProvider::class.java.name
        })
    }

    @Test
    fun `an empty allowlist accepts any presenter without checking signatures`() {
        installPresenter("com.unknown.presenter")
        val signatures = FakeSignatures(emptySet())
        assertTrue(PresenterVerifier(context, emptyList(), signatures).isAllowed())
        assertTrue(signatures.checks.isEmpty())
    }

    @Test
    fun `no installed presenter is allowed, so sending fails on its own`() {
        val verifier = PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest)), FakeSignatures(emptySet()))
        assertTrue(verifier.isAllowed())
    }

    @Test
    fun `a trusted package signed with a trusted certificate is allowed`() {
        installPresenter("com.example.presenter")
        val signatures = FakeSignatures(setOf(digest))
        val verifier = PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest)), signatures)
        assertTrue(verifier.isAllowed())
        assertEquals(listOf("com.example.presenter" to digest), signatures.checks)
    }

    @Test
    fun `any of several digests is enough, e g debug and release keys`() {
        installPresenter("com.example.presenter")
        val verifier = PresenterVerifier(
            context,
            listOf(TrustedPresenter("com.example.presenter", otherDigest, digest)),
            FakeSignatures(setOf(digest)),
        )
        assertTrue(verifier.isAllowed())
    }

    @Test
    fun `a trusted package signed with another certificate is rejected`() {
        installPresenter("com.example.presenter")
        val verifier = PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest)), FakeSignatures(setOf(otherDigest)))
        assertFalse(verifier.isAllowed())
    }

    @Test
    fun `another package is rejected without checking its signature`() {
        installPresenter("com.impostor.presenter")
        val signatures = FakeSignatures(setOf(digest))
        val verifier = PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest)), signatures)
        assertFalse(verifier.isAllowed())
        assertTrue(signatures.checks.isEmpty())
    }

    @Test
    fun `digests reach the signature check as raw SHA-256 bytes`() {
        installPresenter("com.example.presenter")
        var received: ByteArray? = null
        PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest.uppercase().chunked(2).joinToString(":")))) { _, _, sha256 ->
            received = sha256
            true
        }.isAllowed()
        assertArrayEquals(ByteArray(32) { digest.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, received)
    }
}
