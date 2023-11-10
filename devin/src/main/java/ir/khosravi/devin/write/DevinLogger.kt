package ir.khosravi.devin.write


interface DevinLogger {

    fun log(message: String)
    fun log(tag: String, message: String = "")
    fun logCallerFunc()
}

