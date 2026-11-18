package com.khosravi.devin.present.launcher

/** One `<data>` element of an intent filter; every attribute is optional. */
data class ManifestData(
    val scheme: String?,
    val host: String?,
    val port: String?,
    val path: String?,
    val pathPrefix: String?,
    val pathPattern: String?,
)

/** One `<intent-filter>` of an activity (or activity alias), with whether that component is exported. */
data class ManifestIntentFilter(
    val exported: Boolean,
    val actions: Set<String>,
    val categories: Set<String>,
    val data: List<ManifestData>,
)

private const val ACTION_VIEW = "android.intent.action.VIEW"
private const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"

/**
 * The deeplinks a client declares, as patterns to pick from: `scheme://host[:port][path]`, or `scheme:` without a
 * host. All `<data>` elements of a filter combine (every scheme × host × path); `pathPrefix` ends in `*` and
 * `pathPattern` is shown as written. Only exported VIEW + BROWSABLE filters with a scheme count.
 */
fun deeplinkPatterns(filters: List<ManifestIntentFilter>): List<String> = filters
    .filter { it.exported && ACTION_VIEW in it.actions && CATEGORY_BROWSABLE in it.categories }
    .flatMap { filter ->
        val schemes = filter.data.mapNotNull { it.scheme?.ifBlank { null } }.distinct()
        val authorities = filter.data.mapNotNull { d -> d.host?.ifBlank { null }?.let { if (d.port.isNullOrBlank()) it else "$it:${d.port}" } }.distinct()
        val paths = filter.data.flatMap { d -> listOfNotNull(d.path, d.pathPrefix?.let { "$it*" }, d.pathPattern) }.distinct()
        schemes.flatMap { scheme ->
            if (authorities.isEmpty()) listOf("$scheme:")
            else authorities.flatMap { authority ->
                if (paths.isEmpty()) listOf("$scheme://$authority") else paths.map { "$scheme://$authority$it" }
            }
        }
    }
    .distinct()
    .sorted()
