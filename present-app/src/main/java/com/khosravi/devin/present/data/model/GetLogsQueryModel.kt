package com.khosravi.devin.present.data.model

import com.khosravi.devin.read.DevinUriHelper

data class GetLogsQueryModel(
    val typeId: String? = null,
    val tag: DevinUriHelper.OpStringValue?,
    val value: DevinUriHelper.OpStringValue?,
    val metaParam: DevinUriHelper.OpStringParam?,
    val page: PageInfo? = null,
)