package com.khosravi.devin.write

/** No-op counterpart of the `devin-write` module's `TrustedPresenter`, so call sites compile in every build variant. */
class TrustedPresenter(val packageName: String, val sha256CertDigests: List<String>) {

    constructor(packageName: String, vararg sha256CertDigests: String) : this(packageName, sha256CertDigests.toList())
}
