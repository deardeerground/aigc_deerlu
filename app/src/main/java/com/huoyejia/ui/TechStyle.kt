package com.huoyejia.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val TechPrimaryGradient = Brush.horizontalGradient(
    listOf(Color(0xFF3DBBFF), Color(0xFF1976FF), Color(0xFF5B6CFF))
)

val TechPanelGradient = Brush.linearGradient(
    listOf(Color(0xF2FFFFFF), Color(0xDDF2F8FF), Color(0xCCE6F3FF))
)

val TechDeepPanelGradient = Brush.linearGradient(
    listOf(Color(0xFFEAF5FF), Color(0xFFD8ECFF), Color(0xFFE7E9FF))
)

@Composable
fun TechBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "glass-bg")
    val drift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass-bg-drift"
    )
    val softDrift = if (animated) drift.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6FBFF), Color(0xFFEAF5FF), Color(0xFFF8FCFF))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color(0xFF1976FF).copy(alpha = 0.055f)
            val scanColor = Color(0xFF00A7D8).copy(alpha = 0.060f)
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
                x += 34.dp.toPx()
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += 34.dp.toPx()
            }
            var scanY = -size.height * 0.2f
            while (scanY <= size.height * 1.2f) {
                val path = Path().apply {
                    moveTo(-size.width * 0.1f, scanY)
                    cubicTo(size.width * 0.22f, scanY + 30.dp.toPx(), size.width * 0.46f, scanY - 18.dp.toPx(), size.width * 1.1f, scanY + 14.dp.toPx())
                }
                drawPath(path, scanColor, style = Stroke(width = 1.2.dp.toPx()))
                scanY += 76.dp.toPx()
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-46).dp + softDrift, y = (-28).dp)
                .size(260.dp)
                .blur(34.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF7FD8FF).copy(alpha = 0.58f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 96.dp, y = softDrift)
                .size(320.dp)
                .blur(42.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF6D7BFF).copy(alpha = 0.32f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-70).dp, y = 70.dp - softDrift)
                .size(300.dp)
                .blur(48.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF9BE7FF).copy(alpha = 0.46f), Color.Transparent)
                    )
                )
        )
        content()
    }
}

@Composable
fun techCardColors() = CardDefaults.cardColors(
    containerColor = Color(0xF2FFFFFF).copy(alpha = 0.78f)
)

fun techPanelBorder(alpha: Float = 0.82f) = BorderStroke(
    width = 1.dp,
    color = Color(0xFF9DCAFF).copy(alpha = alpha)
)
