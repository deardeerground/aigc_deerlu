package com.huoyejia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1769E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F0FF),
    onPrimaryContainer = Color(0xFF08285C),
    secondary = Color(0xFF0B1F3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F1FF),
    onSecondaryContainer = Color(0xFF0B1F3A),
    tertiary = Color(0xFF159DCE),
    tertiaryContainer = Color(0xFFE3F7FF),
    onTertiaryContainer = Color(0xFF053B49),
    background = Color(0xFFF8FBFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF1FF),
    onSurface = Color(0xFF172033),
    onSurfaceVariant = Color(0xFF5D6B85),
    outline = Color(0xFFC8D3E8),
    error = Color(0xFFE5486D),
    errorContainer = Color(0xFFFFE7EE),
    onErrorContainer = Color(0xFF5F1027),
    inverseSurface = Color(0xFF1C2640),
    inverseOnSurface = Color(0xFFF5F8FF)
)

private val TechShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun HuoyejiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = TechShapes,
        typography = MaterialTheme.typography,
        content = content
    )
}
