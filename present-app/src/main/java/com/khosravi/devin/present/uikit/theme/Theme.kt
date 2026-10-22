package com.khosravi.devin.present.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** One corner scale, matching the XML `ShapeAppearance.LoggerApp.*` styles (small 12, medium 16, large 24). */
private val DevinShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DevinTypography = Typography()

@Composable
fun DevinTheme(content: @Composable () -> Unit) {
    // Both come from the colour resources, so they follow values-night like the XML theme does.
    val colorScheme = devinColorScheme(darkTheme = isSystemInDarkTheme())
    val devinColors = devinColors()
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDevinColors provides devinColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = DevinShapes,
            typography = DevinTypography,
            content = content,
        )
    }
}
