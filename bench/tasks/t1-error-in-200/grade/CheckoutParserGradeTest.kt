package com.khosravi.devin.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutParserGradeTest {

    @Test
    fun `an error status in a 200 response is a failure`() {
        val body = """{"status":"error","code":"COUPON_EXPIRED","message":"Coupon SUMMER expired on 2026-09-30"}"""
        val result = CheckoutParser.parse(200, body)
        assertTrue("expected Failure, got $result", result is CheckoutResult.Failure)
        assertEquals("COUPON_EXPIRED", (result as CheckoutResult.Failure).code)
    }

    @Test
    fun `an ok status is still a success`() {
        assertEquals(CheckoutResult.Success("ORD-1"), CheckoutParser.parse(200, """{"status":"ok","orderId":"ORD-1"}"""))
    }
}
