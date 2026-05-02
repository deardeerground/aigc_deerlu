package com.huoyejia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F2F33),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0EC),
    onPrimaryContainer = Color(0xFF072123),
    secondary = Color(0xFFC14824),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8C8),
    onSecondaryContainer = Color(0xFF451205),
    tertiary = Color(0xFF9A6A00),
    tertiaryContainer = Color(0xFFFFE3A3),
    background = Color(0xFFF7F1E7),
    surface = Color(0xFFFFFCF6),
    surfaceVariant = Color(0xFFE9DED0),
    onSurface = Color(0xFF1C1B18),
    onSurfaceVariant = Color(0xFF554D44),
    outline = Color(0xFF8B7D6E)
)

@Composable
fun HuoyejiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
