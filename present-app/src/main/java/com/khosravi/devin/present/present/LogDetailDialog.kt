package com.khosravi.devin.present.present

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.khosravi.devin.present.KEY_DATA
import com.khosravi.devin.present.R
import com.khosravi.devin.present.applyBundle
import com.khosravi.devin.present.databinding.DialogLogDetailBinding
import com.khosravi.devin.present.getSerializableSupport
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.toSafeJSONObject
import com.khosravi.devin.present.tool.BaseDialog

class LogDetailDialog : BaseDialog() {

    private var _binding: DialogLogDetailBinding? = null
    private val binding: DialogLogDetailBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = DialogLogDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val data = requireArguments().getSerializableSupport(KEY_DATA, TextLogItemData::class.java)!!
        binding.tvTag.text = data.tag
        binding.tvMessage.text = data.text
        binding.tvMeta.text = data.meta?.toSafeJSONObject()?.toString(3)?.replace("\\/", "/") ?: ""
    }


    companion object {

        const val TAG = "LogDetailDialog"

        fun newInstance(data: TextLogItemData) = LogDetailDialog()
            .applyBundle(KEY_DATA to data)
    }
}