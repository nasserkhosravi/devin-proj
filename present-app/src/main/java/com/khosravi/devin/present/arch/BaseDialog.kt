package com.khosravi.devin.present.arch

import android.os.Bundle
import androidx.fragment.app.DialogFragment

abstract class BaseDialog : DialogFragment(), BaseDialogCommon {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle()
    }

}

