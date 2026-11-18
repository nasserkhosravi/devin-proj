package com.khosravi.devin.present.launcher

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject

/**
 * Reads a client's deeplinks from its compiled `AndroidManifest.xml`. Needs no permission: clients are visible
 * through the presenter's `<queries>` entry. Links a client routes in code are invisible to it.
 */
class DeeplinkScanner @Inject constructor(appContext: Context) {

    private val packageManager = appContext.applicationContext.packageManager

    /** The client's deeplink patterns (see [deeplinkPatterns]); empty when the manifest can't be read. */
    fun scan(packageId: String): List<String> = runCatching {
        val resources = packageManager.getResourcesForApplication(packageId)
        resources.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
            deeplinkPatterns(readIntentFilters(parser, resources))
        }
    }.getOrDefault(emptyList())

    private fun readIntentFilters(parser: XmlResourceParser, resources: Resources): List<ManifestIntentFilter> {
        val filters = ArrayList<ManifestIntentFilter>()
        var componentExported = false
        var inComponent = false
        var actions: MutableSet<String>? = null
        var categories: MutableSet<String>? = null
        var data: MutableList<ManifestData>? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "activity", "activity-alias" -> {
                        inComponent = true
                        // A filter used to make an activity exported by default; apps targeting 31+ must say so.
                        componentExported = parser.getAttributeBooleanValue(ANDROID_NS, "exported", true)
                    }
                    "intent-filter" -> if (inComponent) {
                        actions = HashSet(); categories = HashSet(); data = ArrayList()
                    }
                    "action" -> parser.attr(resources, "name")?.let { actions?.add(it) }
                    "category" -> parser.attr(resources, "name")?.let { categories?.add(it) }
                    "data" -> data?.add(
                        ManifestData(
                            scheme = parser.attr(resources, "scheme"),
                            host = parser.attr(resources, "host"),
                            port = parser.attr(resources, "port"),
                            path = parser.attr(resources, "path"),
                            pathPrefix = parser.attr(resources, "pathPrefix"),
                            pathPattern = parser.attr(resources, "pathPattern") ?: parser.attr(resources, "pathAdvancedPattern"),
                        )
                    )
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "intent-filter" -> {
                        val a = actions; val c = categories; val d = data
                        if (a != null && c != null && d != null) filters += ManifestIntentFilter(componentExported, a, c, d)
                        actions = null; categories = null; data = null
                    }
                    "activity", "activity-alias" -> inComponent = false
                }
            }
            event = parser.next()
        }
        return filters
    }

    /** An `android:` attribute, with a resource reference (`@string/...`) resolved to its text. */
    private fun XmlResourceParser.attr(resources: Resources, name: String): String? {
        val resId = getAttributeResourceValue(ANDROID_NS, name, 0)
        if (resId != 0) return runCatching { resources.getString(resId) }.getOrNull()
        return getAttributeValue(ANDROID_NS, name)?.ifBlank { null }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
