package com.khosravi.devin.present.present.http.items

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.databinding.ItemHttpDetailOverviewBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class HttpDetailOverviewItemView(
    val data: HttpLogDetailData,
    private val calendarProxy: CalendarProxy,
) : FastBindingItem<ItemHttpDetailOverviewBinding>() {

    override val type: Int
        get() = R.id.vh_item_http_detail_overview

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpDetailOverviewBinding {
        return ItemHttpDetailOverviewBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHttpDetailOverviewBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.run {
            url.text = data.urlExternalForm
            method.text = data.harRequest.method
            protocol.text = data.protocol
            status.text = data.statusString()
            response.text = data.responseSummaryText
            sslValue.text = if (data.isSsl) context.getString(R.string.yes) else context.getString(R.string.no)

            if (data.responseTlsVersion != null) {
                tlsVersionValue.text = data.responseTlsVersion
                tlsGroup.visibility = View.VISIBLE
            }else {
                tlsGroup.visibility = View.GONE
            }
            if (data.responseCipherSuite != null) {
                cipherSuiteValue.text = data.responseCipherSuite
                cipherSuiteGroup.visibility = View.VISIBLE
            }else {
                cipherSuiteGroup.visibility = View.GONE
            }
            requestTime.text = data.requestDateString(calendarProxy)
            responseTime.text = data.responseDateString(calendarProxy)
            duration.text = data.durationString
            requestSize.text = data.requestSizeString
            responseSize.text = data.responseSizeString
            totalSize.text = data.totalSizeString
        }
    }

}