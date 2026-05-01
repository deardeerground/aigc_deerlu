package com.huoyejia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF183B37),
    onPrimary = Color.White,
    secondary = Color(0xFFB84E2A),
    tertiary = Color(0xFFD59F0F),
    background = Color(0xFFF4F7F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDE7DF),
    onSurface = Color(0xFF13201F)
)

@Composable
fun HuoyejiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
