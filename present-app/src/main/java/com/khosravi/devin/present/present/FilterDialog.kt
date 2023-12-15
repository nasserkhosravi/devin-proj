package com.khosravi.devin.present.present

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.creataNotEmpty
import com.khosravi.devin.present.databinding.DialogFilterBinding
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.tool.BaseDialog

class FilterDialog : BaseDialog() {

    var onConfirm: ((DefaultFilterItem) -> Unit)? = null
    private var _binding: DialogFilterBinding? = null
    private val binding: DialogFilterBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogTheme);
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = DialogFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnConfirm.setOnClickListener {
            confirmedRequested()
        }
    }

    private fun confirmedRequested() {
        val title = binding.edTitle.text?.toString()
        if (title.isNullOrEmpty()) {
            binding.edTitle.error = getString(R.string.msg_title_required)
            return
        }
        val searchText = binding.edSearchText.text.toString()
        val filterItem = DefaultFilterItem(
            ui = FilterUiData(title, title.creataNotEmpty()),
            //TODO: in future support filter by type
            criteria = FilterCriteria(null, searchText)
        )
        onConfirm?.invoke(filterItem)
    }

    override fun onDestroy() {
        super.onDestroy()
        onConfirm = null
    }

    companion object {
        const val TAG = "FilterDialog"
        fun newInstance() = FilterDialog()
    }

}