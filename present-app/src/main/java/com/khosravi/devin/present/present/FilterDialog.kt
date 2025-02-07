package com.khosravi.devin.present.present

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.khosravi.devin.present.ResourceHelper
import com.khosravi.devin.present.R
import com.khosravi.devin.present.applyBundle
import com.khosravi.devin.present.creataNotEmpty
import com.khosravi.devin.present.databinding.DialogFilterBinding
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.tool.BaseDialog

class FilterDialog : BaseDialog() {

    var onConfirm: ((CustomFilterItem) -> Unit)? = null
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
        val tag = binding.edTag.text?.toString()
        val title = binding.edTitle.text?.toString()
        if (title.isNullOrEmpty() && tag.isNullOrEmpty()) {
            binding.edTitle.error = getString(R.string.msg_title_required)
            binding.edTag.error = getString(R.string.msg_title_required)
            return
        }
        val fTitle = if (title.isNullOrEmpty()) tag!!
        else title

        val searchText = binding.edSearchText.text.toString()
        val chipColor = ResourceHelper.getAFilterColor(context!!, requireArguments().getInt(KEY_LAST_INDEX))
        val filterItem = CustomFilterItem(
            ui = FilterUiData(fTitle, fTitle.creataNotEmpty(), chipColor),
            criteria = CustomFilterCriteria(tag, searchText)
        )
        onConfirm?.invoke(filterItem)
    }

    override fun onDestroy() {
        super.onDestroy()
        onConfirm = null
    }

    companion object {
        const val TAG = "FilterDialog"
        private const val KEY_LAST_INDEX = "_last_index"
        fun newInstance(lastIndex: Int) = FilterDialog().applyBundle(KEY_LAST_INDEX to lastIndex)
    }

}