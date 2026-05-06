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
    listOf(Color(0xFF00D7FF), Color(0xFF1769E8), Color(0xFF09224D))
)

val TechPanelGradient = Brush.linearGradient(
    listOf(Color(0xFFF8FCFF), Color(0xFFEAF5FF), Color(0xFFF1FBFF))
)

val TechDeepPanelGradient = Brush.linearGradient(
    listOf(Color(0xFFE5F8FF), Color(0xFFEAF1FF), Color(0xFFE8FFF7))
)

@Composable
fun TechBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "tech-bg")
    val drift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tech-bg-drift"
    )
    val softDrift = if (animated) drift.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6FCFF), Color(0xFFFFFFFF), Color(0xFFEAF8FF))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color(0xFF6CB9FF).copy(alpha = 0.08f)
            val step = 42.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            val path = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.22f)
                lineTo(size.width * 0.28f, size.height * 0.18f)
                lineTo(size.width * 0.46f, size.height * 0.30f)
                lineTo(size.width * 0.74f, size.height * 0.24f)
            }
            drawPath(path, Color(0xFF00BFEF).copy(alpha = 0.13f), style = Stroke(width = 2.dp.toPx()))
            listOf(
                size.width * 0.08f to size.height * 0.22f,
                size.width * 0.28f to size.height * 0.18f,
                size.width * 0.46f to size.height * 0.30f,
                size.width * 0.74f to size.height * 0.24f
            ).forEach { (nodeX, nodeY) ->
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(nodeX, nodeY))
                drawCircle(Color(0xFF00BFEF).copy(alpha = 0.26f), radius = 10.dp.toPx(), center = androidx.compose.ui.geometry.Offset(nodeX, nodeY), style = Stroke(width = 1.dp.toPx()))
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
                        listOf(Color(0xFF73FFF4).copy(alpha = 0.58f), Color.Transparent)
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
                        listOf(Color(0xFFB7D9FF).copy(alpha = 0.66f), Color.Transparent)
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
                        listOf(Color(0xFFC8FFE8).copy(alpha = 0.62f), Color.Transparent)
                    )
                )
        )
        content()
    }
}

@Composable
fun techCardColors() = CardDefaults.cardColors(
    containerColor = Color.White.copy(alpha = 0.88f)
)

fun techPanelBorder(alpha: Float = 0.82f) = BorderStroke(
    width = 1.dp,
    color = Color(0xFFB9D9FF).copy(alpha = alpha)
)
