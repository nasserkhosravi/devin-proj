package com.khosravi.devin.present.data

import android.annotation.SuppressLint
import android.content.Context
import com.khosravi.devin.present.itsNotEmpty
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import androidx.core.content.edit

class FilterRepository @Inject constructor(appContext: Context) {

    private val pref = appContext.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val pinnedFiltersPref =
        appContext.applicationContext.getSharedPreferences(PREF_NAME_PINNED, Context.MODE_PRIVATE)
    private val pinnedMetaPref =
        appContext.applicationContext.getSharedPreferences(PREF_NAME_PINNED_META, Context.MODE_PRIVATE)

    fun getCustomFilterItemList(clientId: String): List<FilterItem> {
        val pinnedList = getPinnedListIds()
        return pref.all.map {
            val jsonString = it.value as String
            JSONObject(jsonString)
        }.filter {
            val savedClientId = it.optString(KEY_CLIENT_ID)
            if (savedClientId.isEmpty()) {
                //consider not saved client id as a global filter item.
                true
            } else savedClientId == clientId
        }.sortedBy { it.getLong(KEY_TIMESTAMP) }
            .mapNotNull { createCustomFilterItem(it, pinnedList) }
    }

    @SuppressLint("UseKtx")
    fun saveFilter(data: CustomFilterItem, clientId: String): Boolean {
        if (data.id.isEmpty()) {
            return false
        }

        return pref.edit().putString(data.id, data.toJson(clientId).toString()).commit()
    }

    private fun CustomFilterItem.toJson(clientId: String): JSONObject {
        val criteriaJson = criteria.let {
            JSONObject().put(KEY_CRITERIA_TAG, it.tag)
                .put(KEY_CRITERIA_SEARCH_TEXT, it.searchText)
        }
        val uiJson = ui.let {
            JSONObject()
                .put(KEY_UI_TITLE, it.title.value)
        }
        return JSONObject()
            .put(KEY_ID, id)
            .put(KEY_CLIENT_ID, clientId)
            .put(KEY_TIMESTAMP, Date().time)
            .put(KEY_CRITERIA, criteriaJson)
            .put(KEY_UI, uiJson)

    }

    @SuppressLint("UseKtx")
    fun clearSync(): Boolean {
        return pref.edit().clear().commit()
    }

    private fun createCustomFilterItem(json: JSONObject, pinnedList: List<String>): FilterItem? {
        val id = json.getString(KEY_ID)
        val criteria = json.optJSONObject(KEY_CRITERIA)?.let {
            CustomFilterCriteria(
                it.optString(KEY_CRITERIA_TAG), it.optString(KEY_CRITERIA_SEARCH_TEXT)
            )
        } ?: return null

        val uiJson = json.getJSONObject(KEY_UI)

        val isPinned = pinnedList.contains(id)
        val present = FilterUiData(
            id, uiJson.getString(KEY_UI_TITLE).itsNotEmpty(),
            isPinned = isPinned
        )
        return CustomFilterItem(present, criteria)
    }

    fun createTagFilterList(tags: Set<String>, userFilterList: List<FilterItem>): HashMap<String, FilterItem> {
        val userFilterListId = userFilterList.map { it.id }
        val result = HashMap<String, FilterItem>()
        val pinnedList = getPinnedListIds()
        tags.filter {
            val key = it
            //first see if the tag exist in user tags
            //second see if the tag already added to [result] for removing duplicate tags
            !userFilterListId.contains(key) && !result.contains(key)
        }.forEach {
            val isPinned = pinnedList.contains(it)
            result[it] = TagFilterItem(it, isPinned)
        }
        return result
    }

    fun getPinnedListIds(): List<String> {
        ensureDefaultPinsSeeded()
        try {
            return pinnedFiltersPref.all.map {
                it.value as String
            }.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun saveAsPinned(filterItem: FilterItem) {
        pinnedFiltersPref.edit { putString(filterItem.id, filterItem.ui.title.value) }
    }

    fun removeAsPinned(filterItem: FilterItem) {
        pinnedFiltersPref.edit { remove(filterItem.id) }
    }

    /** Pinned filter ids (tags and custom filters), sorted, for the Settings screen. */
    fun getPinnedTags(): List<String> {
        ensureDefaultPinsSeeded()
        // Keys, not values: unpinTag() removes by key.
        return pinnedFiltersPref.all.keys.sorted()
    }

    /** Pins [tag] by name, so it can be pinned before any client has logged it. Blank input is ignored. */
    fun pinTag(tag: String) {
        val value = tag.trim()
        if (value.isEmpty()) return
        pinnedFiltersPref.edit { putString(value, value) }
    }

    fun unpinTag(tag: String) {
        pinnedFiltersPref.edit { remove(tag) }
    }

    /** Pins [DEFAULT_PINNED_TAGS] once; a tag the user unpins later stays unpinned. */
    private fun ensureDefaultPinsSeeded() {
        if (pinnedMetaPref.getBoolean(KEY_SEEDED_DEFAULT_PINS, false)) return
        // commit before setting the flag, so a crash in between can't re-seed a tag the user unpinned.
        pinnedFiltersPref.edit(commit = true) { DEFAULT_PINNED_TAGS.forEach { putString(it, it) } }
        pinnedMetaPref.edit { putBoolean(KEY_SEEDED_DEFAULT_PINS, true) }
    }

    fun removeFilter(data: CustomFilterItem) {
        pref.edit { remove(data.id) }
    }


    companion object {
        private const val KEY_ID = "_id"
        private const val KEY_TIMESTAMP = "_timestamp"
        private const val KEY_CLIENT_ID = "_client_id"

        private const val KEY_CRITERIA = "_CRITERIA"
        private const val KEY_CRITERIA_TAG = "_TAG"
        private const val KEY_CRITERIA_SEARCH_TEXT = "_SEARCH_TEXT"

        private const val KEY_UI = "_UI"
        private const val KEY_UI_TITLE = "_TITLE"

        private const val PREF_NAME = "filter"
        private const val PREF_NAME_PINNED = "pinned_filter_ids"
        // Separate file: getPinnedListIds() reads every value in PREF_NAME_PINNED as a String.
        private const val PREF_NAME_PINNED_META = "pinned_filter_meta"
        private const val KEY_SEEDED_DEFAULT_PINS = "seeded_default_pins"
        private val DEFAULT_PINNED_TAGS = listOf(DevinHttpFlagsApi.LOG_TAG)
    }

}