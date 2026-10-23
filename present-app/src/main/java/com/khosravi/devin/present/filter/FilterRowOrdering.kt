package com.khosravi.devin.present.filter

/**
 * Orders the filter chips: pinned first, then the same order inside pinned and unpinned chips:
 * 1. chips the user has picked, by [scores] (highest first);
 * 2. custom filters never picked, in their given order;
 * 3. tags that logged recently, by [lastLogAt] (newest first);
 * 4. every other tag, alphabetically.
 */
object FilterRowOrdering {

    fun order(
        filters: List<FilterItem>,
        scores: Map<String, Double>,
        lastLogAt: Map<String, Long>,
    ): List<FilterItem> {
        fun group(filter: FilterItem): Int = when {
            (scores[filter.id] ?: 0.0) > 0.0 -> GROUP_PICKED
            filter is CustomFilterItem -> GROUP_CUSTOM
            lastLogAt.containsKey(filter.id) -> GROUP_RECENT
            else -> GROUP_OTHER
        }

        val comparator = compareByDescending<FilterItem> { it.ui.isPinned }
            .thenBy { group(it) }
            .thenComparator { a, b ->
                // a and b are in the same group here.
                when (group(a)) {
                    GROUP_PICKED -> compareValues(scores[b.id], scores[a.id])
                    GROUP_RECENT -> compareValues(lastLogAt[b.id], lastLogAt[a.id])
                    GROUP_OTHER -> a.ui.title.value.compareTo(b.ui.title.value, ignoreCase = true)
                    else -> 0
                }
            }
        // sortedWith is stable, so never-picked custom filters keep their given order.
        return filters.sortedWith(comparator)
    }

    private const val GROUP_PICKED = 0
    private const val GROUP_CUSTOM = 1
    private const val GROUP_RECENT = 2
    private const val GROUP_OTHER = 3
}
