package com.khosravi.devin.read

object DevinLogFlagsApi {

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
