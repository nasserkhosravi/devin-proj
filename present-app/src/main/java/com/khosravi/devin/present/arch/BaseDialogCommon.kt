package com.khosravi.devin.present.arch

import androidx.fragment.app.DialogFragment
import com.khosravi.devin.present.R

interface BaseDialogCommon {

    fun DialogFragment.setStyle() {
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.DialogTheme);
    }
}