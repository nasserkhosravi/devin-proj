package com.khosravi.devin.present.present

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.khosravi.devin.present.uikit.theme.DevinTheme

class ClientLoginDialogHost(private val activity: AppCompatActivity) {

    fun show(
        correctPassword: String,
        onCorrectPassword: (String) -> Unit,
        onWrongPassword: () -> Boolean,
    ) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        lateinit var composeView: ComposeView
        composeView = ComposeView(activity).apply {
            setContent {
                DevinTheme {
                    ClientLoginSheet(
                        correctPassword = correctPassword,
                        onCorrectPassword = onCorrectPassword,
                        onWrongPassword = onWrongPassword,
                        onDismissed = { root.removeView(composeView) }
                    )
                }
            }
        }
        root.addView(composeView)
    }
}
