package ir.khosravi.devin.present.filter

import ir.khosravi.devin.present.tool.NotEmptyString

interface FilterItem {
    val id: String
    val ui: FilterUiData
    val criteria: FilterCriteria?
}

class FilterCriteria(
    val type: String?,
    val searchText: String?
)

class FilterUiData(
    val id: String,
    val title: NotEmptyString,
    var isChecked: Boolean = false
)

class DefaultFilterItem(
    override val ui: FilterUiData,
    override val criteria: FilterCriteria?
) : FilterItem {
    override val id: String
        get() = ui.title.value
}

class MainFilterItem(
    override val id: String = "Main"
) : FilterItem {

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString(id))

    override val criteria: FilterCriteria? = null
}
