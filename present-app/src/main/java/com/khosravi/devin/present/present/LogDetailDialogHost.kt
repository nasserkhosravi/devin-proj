package com.khosravi.devin.present.present

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.DevinTheme

class LogDetailDialogHost(context: Context, root: ViewGroup) {

    private var current by mutableStateOf<TextLogItemData?>(null)

    init {
        val composeView = ComposeView(context).apply {
            setContent {
                DevinTheme {
                    current?.let { data ->
                        LogDetailDialog(data) { current = null }
                    }
                }
            }
        }
        root.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun show(data: TextLogItemData) {
        current = data
    }
}
