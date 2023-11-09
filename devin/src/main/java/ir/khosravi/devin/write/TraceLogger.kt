package ir.khosravi.devin.write

internal object TraceLogger {

    fun callerFuncInfo(wrapperIndex: Int = 0): String {
        val stackTrace = Thread.currentThread().stackTrace
        //3 is a magic number to access caller of [logCaller]
        val stackTraceElement = stackTrace.getOrNull(3 + wrapperIndex) ?: return "DevinError, callerFuncInfo getting strace"
        val methodName = stackTraceElement.methodName
        val parentName = stackTraceElement.className ?: stackTraceElement.fileName
        return "$parentName $methodName"
    }

}