package com.khosravi.devin.present.uikit.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.uikit.theme.DevinTheme

/** Widest a screen's content gets; a wider window shows the page background on both sides. */
val MAX_CONTENT_WIDTH: Dp = 600.dp

/**
 * Keeps the phone layout on wide windows (tablets, unfolded foldables, desktop windows): [content] is centered
 * and at most [MAX_CONTENT_WIDTH] wide. A narrower window (a phone, a narrow split-screen) is unaffected.
 * Call it inside [DevinTheme], or use [DevinScreen].
 */
@Composable
fun DevinWindow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxHeight()) {
            content()
        }
    }
}

/** The theme plus [DevinWindow]: what each activity's `setContent` uses. */
@Composable
fun DevinScreen(content: @Composable () -> Unit) {
    DevinTheme { DevinWindow(content) }
}
