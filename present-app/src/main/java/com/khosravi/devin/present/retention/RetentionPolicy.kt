package com.khosravi.devin.present.retention

/**
 * How long logs are kept in the shared Devin DB. A log is deleted when it is older than
 * [maxAgeDays] **or** falls outside the newest [maxCountPerClient] logs of its client.
 */
data class RetentionPolicy(
    val isEnabled: Boolean,
    val maxAgeDays: Int,
    val maxCountPerClient: Int,
) {
    companion object {
        val DEFAULT = RetentionPolicy(isEnabled = true, maxAgeDays = 7, maxCountPerClient = 10_000)

        /** Values offered by the Settings screen pickers. */
        val AGE_OPTIONS_DAYS = listOf(1, 3, 7, 14, 30)
        val COUNT_OPTIONS = listOf(1_000, 5_000, 10_000, 50_000)
    }
}
