package com.khosravi.devin.write

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which frame [TraceLogger.callerFuncInfo] lands on depends on the runtime: ART puts an extra
 * `VMStack` frame on top of `Thread.getStackTrace()` that the JVM running these tests doesn't.
 * So these tests only check runtime-independent behavior.
 */
class TraceLoggerTest {

    @Test
    fun `parent name is left out when not requested`() {
        val result = TraceLogger.callerFuncInfo(0, enableParentName = false).getOrThrow()

        assertNull(result.parenName)
        assertTrue(result.methodName.isNotEmpty())
        assertTrue(!result.isClassName)
    }

    @Test
    fun `parent name is the full class name when requested`() {
        val result = TraceLogger.callerFuncInfo(0, enableParentName = true).getOrThrow()

        assertTrue(result.isClassName)
        assertTrue(result.parenName!!.contains("."))
    }

    @Test
    fun `a frame beyond the stack is a failure`() {
        val result = TraceLogger.callerFuncInfo(10_000, enableParentName = true)

        assertTrue(result.exceptionOrNull() is TraceLogger.StacktraceNotFound)
    }
}
