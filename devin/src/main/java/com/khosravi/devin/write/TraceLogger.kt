package com.khosravi.devin.write

/**
 * A place to put logging functionalities by stack trace
 */
internal object TraceLogger {

    /**
     * @return [Result]] of [AnalysedStacktrace] that contain the method name plus class name or file name by stack trace.
     * [wrapperIndex] Set skipping levels of stack trace to reach desired function.
     * [enableParentName] indicate whether class name or file name should be in return.
     */
    fun callerFuncInfo(wrapperIndex: Int = 0, enableParentName: Boolean): Result<AnalysedStacktrace> {
        val stackTrace = Thread.currentThread().stackTrace
        //3 is a magic number to access caller of [callerFuncInfo]
        val stackTraceElement = stackTrace.getOrNull(3 + wrapperIndex)
            ?: return Result.failure(StacktraceNotFound("DevinError, callerFuncInfo getting strace"))
        val methodName = stackTraceElement.methodName
        if (enableParentName) {
            val fullClassName = stackTraceElement.className
            val analysed = if (!fullClassName.isNullOrEmpty()) {
                AnalysedStacktrace(methodName, stackTraceElement.className!!, true)
            } else {
                AnalysedStacktrace(methodName, stackTraceElement.fileName!!, false)
            }
            return Result.success(analysed)
        }
        return Result.success(AnalysedStacktrace(methodName, null, false))
    }

    class StacktraceNotFound(message: String?) : Throwable(message)

    class AnalysedStacktrace(
        val methodName: String,
        val parenName: String?,
        val isClassName: Boolean
    )

}