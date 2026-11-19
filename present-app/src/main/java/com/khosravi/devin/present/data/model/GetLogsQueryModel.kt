package com.khosravi.devin.present.data.model

import com.khosravi.devin.read.DevinUriHelper

data class GetLogsQueryModel(
    val typeId: String? = null,
    val tag: DevinUriHelper.OpStringValue?,
    val value: DevinUriHelper.OpStringValue?,
    val metaParam: DevinUriHelper.OpStringParam?,
    val timeLessThan: Long?,
    val page: PageInfo? = null,
    /** Exact tags, matched with `tag IN (...)` next to [tag]; null or empty means no tag-set constraint. */
    val tags: List<String>? = null,
    /** Only logs with an id above this; with a null [page] it returns all of them (used to fetch what is new). */
    val idGreaterThan: Long? = null,
)