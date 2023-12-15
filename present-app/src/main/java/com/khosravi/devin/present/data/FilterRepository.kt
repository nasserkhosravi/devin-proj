package com.khosravi.devin.present.data

import android.content.Context
import com.khosravi.devin.present.creataNotEmpty
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterUiData
import org.json.JSONObject
import javax.inject.Inject

class FilterRepository @Inject constructor(appContext: Context) {

    private val pref = appContext.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getFilterList(): List<FilterItem> {
        return pref.all.map {
            val jsonString = it.value as String
            filterItem(JSONObject(jsonString))
        }
    }

    fun saveFilter(data: FilterItem): Boolean {
        if (data.id.isEmpty()) {
            return false
        }

        return pref.edit().putString(data.id, data.toJson().toString()).commit()
    }

    private fun FilterItem.toJson(): JSONObject {
        val criteriaJson = criteria?.let {
            JSONObject().put(KEY_CRITERIA_TYPE, it.type)
                .put(KEY_CRITERIA_SEARCH_TEXT, it.searchText)
        }
        val uiJson = ui.let {
            JSONObject().put(KEY_UI_TITLE, it.title.value)
        }
        return JSONObject()
            .put(KEY_ID, id)
            .put(KEY_CRITERIA, criteriaJson)
            .put(KEY_UI, uiJson)

    }

    fun clearSync(): Boolean {
        return pref.edit().clear().commit()
    }

    private fun filterItem(json: JSONObject): FilterItem {
        val id = json.getString(KEY_ID)
        val criteria = json.optJSONObject(KEY_CRITERIA)?.let {
            FilterCriteria(
                it.optString(KEY_CRITERIA_TYPE), it.optString(KEY_CRITERIA_SEARCH_TEXT)
            )
        }

        val present = FilterUiData(
            id, json.getJSONObject(KEY_UI).getString(KEY_UI_TITLE).creataNotEmpty()
        )
        return DefaultFilterItem(present, criteria)
    }

    companion object {
        private const val KEY_ID = "_id"

        private const val KEY_CRITERIA = "_CRITERIA"
        private const val KEY_CRITERIA_TYPE = "_TYPE"
        private const val KEY_CRITERIA_SEARCH_TEXT = "_SEARCH_TEXT"

        private const val KEY_UI = "_UI"
        private const val KEY_UI_TITLE = "_UI_TITLE"

        private const val PREF_NAME = "filter"
    }

}