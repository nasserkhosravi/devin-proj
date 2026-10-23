package com.khosravi.devin.present.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.pow

/**
 * Per-client usage data for the tag row: how often each filter chip is picked (a score that decays with
 * [HALF_LIFE_MS]) and when each tag chip was last selected, which clears its error dot.
 */
class FilterUsageRepository @Inject constructor(appContext: Context) {

    private val usagePref = appContext.applicationContext.getSharedPreferences(PREF_NAME_USAGE, Context.MODE_PRIVATE)
    private val seenPref = appContext.applicationContext.getSharedPreferences(PREF_NAME_SEEN, Context.MODE_PRIVATE)

    /** Adds 1 to [filterId]'s decayed score. */
    fun recordPick(clientId: String, filterId: String, now: Long = System.currentTimeMillis()) {
        val key = key(clientId, filterId)
        val score = usagePref.getString(key, null)?.let { decayedScore(it, now) } ?: 0.0
        val json = JSONObject().put(KEY_SCORE, score + 1).put(KEY_UPDATED_AT, now)
        usagePref.edit { putString(key, json.toString()) }
    }

    /** Decayed scores of [clientId]'s filters, keyed by filter id. Filters never picked are absent. */
    fun getScores(clientId: String, now: Long = System.currentTimeMillis()): Map<String, Double> {
        val prefix = prefix(clientId)
        return usagePref.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is String) return@mapNotNull null
            val score = decayedScore(value, now) ?: return@mapNotNull null
            key.removePrefix(prefix) to score
        }.toMap()
    }

    fun removeUsage(clientId: String, filterId: String) {
        usagePref.edit { remove(key(clientId, filterId)) }
    }

    fun markTagSeen(clientId: String, tag: String, now: Long = System.currentTimeMillis()) {
        seenPref.edit { putLong(key(clientId, tag), now) }
    }

    /** When the user last selected [clientId]'s tag chips, keyed by tag. Tags never selected are absent. */
    fun getTagSeenTimes(clientId: String): Map<String, Long> {
        val prefix = prefix(clientId)
        return seenPref.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is Long) return@mapNotNull null
            key.removePrefix(prefix) to value
        }.toMap()
    }

    private fun decayedScore(jsonString: String, now: Long): Double? {
        return try {
            val json = JSONObject(jsonString)
            val elapsed = (now - json.getLong(KEY_UPDATED_AT)).coerceAtLeast(0)
            json.getDouble(KEY_SCORE) * 0.5.pow(elapsed.toDouble() / HALF_LIFE_MS)
        } catch (e: Exception) {
            null
        }
    }

    // Client ids are package names, which can't contain SEPARATOR, so the first SEPARATOR ends the client id.
    private fun prefix(clientId: String) = "$clientId$SEPARATOR"

    private fun key(clientId: String, id: String) = prefix(clientId) + id

    companion object {
        private const val PREF_NAME_USAGE = "filter_usage"
        private const val PREF_NAME_SEEN = "tag_seen"
        private const val KEY_SCORE = "score"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val SEPARATOR = "|"
        private const val HALF_LIFE_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
