package com.khosravi.devin.read

import android.net.Uri
import com.khosravi.devin.write.DevinContentProvider.Companion.URI_ALL_LOG
import com.khosravi.devin.write.DevinContentProvider.Companion.URI_ROOT_CLIENT
import com.khosravi.devin.write.room.LogTable

object DevinUriHelper {
    private val mUriOfAllLog: Uri by lazy { Uri.parse(URI_ALL_LOG) }

    fun getLogListUri(
        clientId: String,
        tag: String? = null,
        msl1: OpStringParam? = null,
        pageIndex: Int? = null,
        itemCount: Int? = null,
        isRawQuery: Boolean = false
    ): Uri {
        val builder = Uri.parse(URI_ALL_LOG.plus("?${LogTable.COLUMN_CLIENT_ID}=$clientId")).buildUpon()
        builder.appendQueryParameter(KEY_IS_RAW_QUERY, isRawQuery.toString())
        if (!tag.isNullOrEmpty()) {
            builder.appendQueryParameter(LogTable.COLUMN_TAG, tag)
        }
        msl1?.let {
            builder.appendQueryParameter(KEY_LOG_MSL1_NAME, msl1.fName)
                .appendQueryParameter(KEY_LOG_MSL1_VALUE, msl1.op.value)
                .appendQueryParameter(KEY_LOG_MSL1_OP, msl1.op.opId())
        }
        pageIndex?.let {
            builder.appendQueryParameter(KEY_PAGE_INDEX, it.toString())
        }
        itemCount?.let {
            builder.appendQueryParameter(KEY_ITEM_COUNT, it.toString())
        }

        return builder.build()
    }

    fun getClientListUri(): Uri = Uri.parse(URI_ROOT_CLIENT)

    fun getLogListUri(): Uri = mUriOfAllLog

    fun getLogUri(id: Long): Uri = Uri.parse(URI_ALL_LOG.plus("?${LogTable.COLUMN_ID}=$id"))

    internal fun Uri.getMsl1(): OpStringParam? {
        val name = getQueryParameter(KEY_LOG_MSL1_NAME)
        val value = getQueryParameter(KEY_LOG_MSL1_VALUE)
        val opId = getQueryParameter(KEY_LOG_MSL1_OP)
        if (!name.isNullOrEmpty() && !value.isNullOrEmpty() && !opId.isNullOrEmpty()) {
            return OpStringParam(name, OpStringValue.Contain(value))
        }
        return null
    }

    sealed interface OpStringValue {
        val value: String
        fun opId(): String

        class EqualTo(override val value: String) : OpStringValue {
            companion object {
                const val OP_ID = "EqualTo"
            }

            override fun opId(): String = OP_ID
        }

        class Contain(override val value: String) : OpStringValue {
            companion object {
                const val OP_ID = "Contain"
            }

            override fun opId(): String = OP_ID
        }

        class StartWith(override val value: String) : OpStringValue {
            companion object {
                const val OP_ID = "StartWith"
            }

            override fun opId(): String = OP_ID
        }
    }

    class OpStringParam(
        val fName: String,
        val op: OpStringValue
    )


    private const val KEY_LOG_MSL1_NAME = "metaSearchFieldName"
    private const val KEY_LOG_MSL1_VALUE = "metaSearchFieldValue"
    private const val KEY_LOG_MSL1_OP = "metaSearchFieldOperation"

    internal const val KEY_PAGE_INDEX = "pageIndex"
    internal const val KEY_ITEM_COUNT = "itemCount"
    internal const val KEY_IS_RAW_QUERY = "isRawQuery"

}