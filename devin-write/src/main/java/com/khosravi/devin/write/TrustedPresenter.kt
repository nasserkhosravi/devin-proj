package com.khosravi.devin.write

/**
 * A presenter build this app may send its logs to: the presenter's package name and the SHA-256 digests of the
 * signing certificates it may be signed with. Debug and release builds are usually signed with different keys,
 * so list both to trust both.
 *
 * Digests are accepted in any case, with or without `:` separators, e.g. the `SHA-256` line of
 * `keytool -list -v` or `apksigner verify --print-certs`.
 *
 * See [DevinTool.init].
 */
class TrustedPresenter(val packageName: String, sha256CertDigests: List<String>) {

    constructor(packageName: String, vararg sha256CertDigests: String) : this(packageName, sha256CertDigests.toList())

    /** Normalized: lowercase hex without separators. */
    val sha256CertDigests: List<String> = sha256CertDigests.map { digest ->
        digest.filter { it.isLetterOrDigit() }.lowercase()
    }

    init {
        require(this.sha256CertDigests.isNotEmpty()) { "TrustedPresenter needs at least one certificate digest" }
        require(this.sha256CertDigests.all { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }) {
            "A SHA-256 certificate digest has 64 hex characters"
        }
    }
}
