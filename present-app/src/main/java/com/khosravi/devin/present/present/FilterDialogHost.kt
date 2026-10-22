package com.khosravi.devin.present.present

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.uikit.theme.DevinTheme

class FilterDialogHost(context: Context, root: ViewGroup) {

    private var visible by mutableStateOf(false)
    private var onConfirm: ((CustomFilterItem) -> Unit)? = null

    init {
        val composeView = ComposeView(context).apply {
            setContent {
                DevinTheme {
                    if (visible) {
                        FilterDialog(
                            onDismiss = { visible = false },
                            onConfirm = { item ->
                                onConfirm?.invoke(item)
                                visible = false
                            }
                        )
                    }
                }
            }
        }
        root.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun show(onConfirm: (CustomFilterItem) -> Unit) {
        this.onConfirm = onConfirm
        visible = true
    }
}
