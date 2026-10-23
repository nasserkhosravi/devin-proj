package com.khosravi.devin.present.uikit.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** A rounded, tinted box for monospace content such as JSON or a stack trace. */
@Composable
fun CodeBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CODE_BLOCK_ALPHA))
            .padding(12.dp),
        content = content,
    )
}

/** Monospace text style for [CodeBlock] content. */
val codeTextStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

private const val CODE_BLOCK_ALPHA = 0.5f
