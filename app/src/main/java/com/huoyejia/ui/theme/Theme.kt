package com.huoyejia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF106FEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3FF),
    onPrimaryContainer = Color(0xFF06275C),
    secondary = Color(0xFF072B47),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F7FF),
    onSecondaryContainer = Color(0xFF05233A),
    tertiary = Color(0xFF00A7B7),
    tertiaryContainer = Color(0xFFDFFBF4),
    onTertiaryContainer = Color(0xFF003D43),
    background = Color(0xFFF6FCFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8F6FF),
    onSurface = Color(0xFF10223D),
    onSurfaceVariant = Color(0xFF536781),
    outline = Color(0xFFB8D3F4),
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
