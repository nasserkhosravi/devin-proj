package ir.khosravi.devin.present.filter

interface FilterItem {
    val id: String?
    val type: String
    var isChecked: Boolean
}

class DefaultFilterItem(override val type: String, override var isChecked: Boolean = false) : FilterItem {
    override val id: String
        get() = type
}

class MainFilterItem : FilterItem {
    override val id: String? = null
    override val type: String = "Main"
    override var isChecked: Boolean = true
}
