package ir.khosravi.devin.write


interface DevinLogger {

    fun debug(value: String)
    fun error(value: String)
    fun info(value: String)
    fun warning(value: String)
    fun custom(type: String, value: String)
}

