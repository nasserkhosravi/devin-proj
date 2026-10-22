package com.khosravi.devin.write

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Checks that the app holding Devin's provider authority is one of the [trusted] presenter builds, by package
 * name and signing certificate. A package name can be copied by another app; a signing key can't.
 *
 * An empty [trusted] list accepts any presenter (the default, and the behavior before this check existed).
 */
internal class PresenterVerifier(
    private val context: Context,
    private val trusted: List<TrustedPresenter>,
    /** Whether a package is signed with a certificate whose SHA-256 is the given bytes. Replaceable in tests. */
    private val isSignedWith: (PackageManager, packageName: String, sha256: ByteArray) -> Boolean = ::platformIsSignedWith,
) {

    @Volatile
    private var warned = false

    /**
     * `true` when sending logs is allowed: no restriction is set, no presenter is installed (sending fails
     * on its own), or the installed presenter is trusted.
     */
    fun isAllowed(): Boolean {
        if (trusted.isEmpty()) return true
        val pm = context.packageManager
        val presenterPackage = pm.resolveContentProvider(AUTHORITY, 0)?.packageName ?: return true
        val entry = trusted.firstOrNull { it.packageName == presenterPackage }
        val allowed = entry != null && entry.sha256CertDigests.any { isSignedWith(pm, presenterPackage, hexToBytes(it)) }
        if (!allowed && !warned) {
            warned = true
            Log.w(TAG, "Devin logs are not sent: '$presenterPackage' holds the Devin provider but isn't a trusted presenter " +
                    "(package name or signing certificate doesn't match DevinTool.init's trustedPresenters).")
        }
        return allowed
    }

    private fun hexToBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        private const val TAG = "DevinTool"
        private val AUTHORITY: String = android.net.Uri.parse(DevinContentProvider.URI_ALL_LOG).authority!!
    }
}

/** Real signature check: the platform's on API 28+ (handles key rotation), SHA-256 of `GET_SIGNATURES` below. */
private fun platformIsSignedWith(pm: PackageManager, packageName: String, digest: ByteArray): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Handles key rotation (APK signature scheme v3).
        pm.hasSigningCertificate(packageName, digest, PackageManager.CERT_INPUT_SHA256)
    } else {
        @Suppress("DEPRECATION")
        val signatures = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        signatures.any { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).contentEquals(digest) }
    }
} catch (e: PackageManager.NameNotFoundException) {
    false
}
