package com.khosravi.devin.present.filter

data class CustomFilterCriteria(
    /** Exact tags; at least one, de-duplicated, in pick order. */
    val tags: List<String>,
    /** Narrows the tags' logs to values containing it; null or blank means no text constraint. */
    val searchText: String?
)
