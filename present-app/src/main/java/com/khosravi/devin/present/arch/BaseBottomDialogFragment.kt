package com.khosravi.devin.present.arch

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.khosravi.devin.present.R

abstract class BaseBottomDialogFragment : BottomSheetDialogFragment(), BaseDialogCommon {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.BottomSheetDialogTheme)
    }
}