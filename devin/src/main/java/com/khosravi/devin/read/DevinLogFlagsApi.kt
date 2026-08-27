package com.khosravi.devin.read

/**
 * TODO: These flags written for presenter but they are public in writer,
 * we should have a clean separation about what should be public API for read and write side users.
 * See @DevinLogCore , see no-op module
 */
object DevinLogFlagsApi {

    internal const val TAG_UNCAUGHT_EXCEPTION = "uncaught_exception"

    const val TAG_SESSION_START = "SessionStart"

    //finished successfully
    const val FINISHED = 1
    //finished incorrectly
    const val ERROR = 2
    //started in progress
    const val IN_PROGRESS = 3

    const val KEY_LOG_LEVEL = "log_level"
    const val KEY_META_TYPE = "meta_type"
    const val KEY_LOG_THROWABLE = "throwable"

}
