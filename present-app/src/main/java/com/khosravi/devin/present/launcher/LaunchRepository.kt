package com.khosravi.devin.present.launcher

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/** A saved launch of one client. */
data class LaunchEntry(val id: String, val input: LaunchInput, val lastLaunchedAt: Long)

/**
 * Saved launches per client, newest first, in the presenter's own prefs (one JSON array per client package).
 * At most [MAX_ENTRIES] per client; the oldest drop off.
 */
class LaunchRepository @Inject constructor(appContext: Context) {

    private val pref = appContext.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun list(clientId: String): List<LaunchEntry> {
        val array = runCatching { JSONArray(pref.getString(clientId, null) ?: return emptyList()) }.getOrNull()
            ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { array.getJSONObject(it).toEntry() }.getOrNull() }
    }

    /**
     * Saves a successful launch at the top. [editingId] names the entry being edited, which takes the new values;
     * otherwise an entry with the same values is reused. Any other entry left with the same values is dropped.
     */
    fun recordLaunch(clientId: String, input: LaunchInput, editingId: String?, now: Long): LaunchEntry {
        val list = list(clientId).toMutableList()
        val identity = input.identity()
        val target = editingId?.let { id -> list.find { it.id == id } } ?: list.find { it.input.identity() == identity }
        val entry = LaunchEntry(target?.id ?: UUID.randomUUID().toString(), input, now)
        list.removeAll { it.id == entry.id || it.input.identity() == identity }
        list.add(0, entry)
        save(clientId, list)
        return entry
    }

    /** Removes the entry and returns it, so it can be [restore]d. */
    fun delete(clientId: String, id: String): LaunchEntry? {
        val list = list(clientId).toMutableList()
        val removed = list.find { it.id == id } ?: return null
        list.remove(removed)
        save(clientId, list)
        return removed
    }

    /** Puts [entry] back where its launch time places it. */
    fun restore(clientId: String, entry: LaunchEntry) {
        val list = list(clientId).filter { it.id != entry.id } + entry
        save(clientId, list.sortedByDescending { it.lastLaunchedAt })
    }

    /** The extra key of the newest launch that had one, to pre-fill the key field. */
    fun lastExtraKey(clientId: String): String? = list(clientId).firstNotNullOfOrNull { it.input.extraKey }

    private fun save(clientId: String, list: List<LaunchEntry>) {
        val array = JSONArray()
        list.take(MAX_ENTRIES).forEach { array.put(it.toJson()) }
        pref.edit { putString(clientId, array.toString()) }
    }

    private fun LaunchEntry.toJson() = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_DEEPLINK, input.deeplink)
        .put(KEY_EXTRA_KEY, input.extraKey ?: JSONObject.NULL)
        .put(KEY_EXTRA_VALUE, input.extraValue ?: JSONObject.NULL)
        .put(KEY_AT, lastLaunchedAt)

    private fun JSONObject.toEntry() = LaunchEntry(
        id = getString(KEY_ID),
        input = LaunchInput(
            deeplink = getString(KEY_DEEPLINK),
            extraKey = optNullableString(KEY_EXTRA_KEY),
            extraValue = optNullableString(KEY_EXTRA_VALUE),
        ),
        lastLaunchedAt = getLong(KEY_AT),
    )

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    companion object {
        const val MAX_ENTRIES = 50
        private const val PREF_NAME = "launcher_v1"
        private const val KEY_ID = "id"
        private const val KEY_DEEPLINK = "deeplink"
        private const val KEY_EXTRA_KEY = "key"
        private const val KEY_EXTRA_VALUE = "value"
        private const val KEY_AT = "at"
    }
}
