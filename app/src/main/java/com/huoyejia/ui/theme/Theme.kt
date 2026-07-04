package com.huoyejia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF062A63),
    secondary = Color(0xFF00A7D8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7F5FF),
    onSecondaryContainer = Color(0xFF003344),
    tertiary = Color(0xFF5B6CFF),
    tertiaryContainer = Color(0xFFE3E6FF),
    onTertiaryContainer = Color(0xFF10195D),
    background = Color(0xFFF4F9FF),
    surface = Color(0xF2FFFFFF),
    surfaceVariant = Color(0xFFE7F1FF),
    onSurface = Color(0xFF10233F),
    onSurfaceVariant = Color(0xFF5B6D86),
    outline = Color(0xFFB8D5F8),
    error = Color(0xFFD64545),
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF641212),
    inverseSurface = Color(0xFF10233F),
    inverseOnSurface = Color(0xFFF4F9FF)
)

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)

@Composable
fun HuoyejiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = GlassShapes,
        typography = MaterialTheme.typography,
        content = content
    )
}
