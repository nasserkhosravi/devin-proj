package com.khosravi.devin.write.exception

import com.khosravi.devin.write.DevinExceptionLogger
import java.lang.Thread.UncaughtExceptionHandler

internal class TwinUncaughtExceptionHandler(
    private val logger: DevinExceptionLogger,
    val defaultUncaughtExceptionHandler: UncaughtExceptionHandler?
) : UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        logger.exceptionHappened(e)
        defaultUncaughtExceptionHandler?.uncaughtException(t, e)
    }

}