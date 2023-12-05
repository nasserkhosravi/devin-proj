package com.khosravi.devin.write

/**
 * A place to put logging functionalities by stack trace
 */
internal object TraceLogger {

    /**
     * Return function name plus class name or file name by stack trace.
     * [wrapperIndex] is for skipping upper usage in the library.
     * [enableParentName] indicate whether class name or file name should be in return.
     */
    fun callerFuncInfo(wrapperIndex: Int = 0, enableParentName: Boolean): String {
        val stackTrace = Thread.currentThread().stackTrace
        //3 is a magic number to access caller of [logCaller]
        val stackTraceElement = stackTrace.getOrNull(3 + wrapperIndex) ?: return "DevinError, callerFuncInfo getting strace"
        val methodName = stackTraceElement.methodName
        if (enableParentName) {
            val parentName = stackTraceElement.className ?: stackTraceElement.fileName
            return "$parentName $methodName"
        }
        return methodName
    }

}