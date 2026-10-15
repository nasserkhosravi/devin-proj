package com.khosravi.devin.present.retention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    @Test
    fun `default is enabled, 7 days, 10000 per client`() {
        assertEquals(RetentionPolicy(isEnabled = true, maxAgeDays = 7, maxCountPerClient = 10_000), RetentionPolicy.DEFAULT)
    }

    @Test
    fun `defaults are selectable in the settings pickers`() {
        assertTrue(RetentionPolicy.DEFAULT.maxAgeDays in RetentionPolicy.AGE_OPTIONS_DAYS)
        assertTrue(RetentionPolicy.DEFAULT.maxCountPerClient in RetentionPolicy.COUNT_OPTIONS)
    }

    @Test
    fun `picker options are positive and ascending`() {
        listOf(RetentionPolicy.AGE_OPTIONS_DAYS, RetentionPolicy.COUNT_OPTIONS).forEach { options ->
            assertTrue(options.all { it > 0 })
            assertEquals(options.sorted(), options)
            assertEquals(options.distinct(), options)
        }
    }
}
