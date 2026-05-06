package com.huoyejia.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp

val TechPrimaryGradient = Brush.horizontalGradient(
    listOf(Color(0xFF5CB8FF), Color(0xFF1769E8), Color(0xFF0B1F3A))
)

val TechPanelGradient = Brush.linearGradient(
    listOf(Color(0xFFF7FBFF), Color(0xFFF0F4FF), Color(0xFFF8F2FF))
)

val TechDeepPanelGradient = Brush.linearGradient(
    listOf(Color(0xFFE9F2FF), Color(0xFFEFEAFF), Color(0xFFE5FBFF))
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
                    listOf(Color(0xFFF8FCFF), Color(0xFFFFFFFF), Color(0xFFEFF6FF))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-46).dp + softDrift, y = (-28).dp)
                .size(260.dp)
                .blur(34.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFBFF7F6).copy(alpha = 0.62f), Color.Transparent)
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
                        listOf(Color(0xFFD8ECFF).copy(alpha = 0.72f), Color.Transparent)
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
                        listOf(Color(0xFFD4EAFF).copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )
        content()
    }
}

@Composable
fun techCardColors() = CardDefaults.cardColors(
    containerColor = Color.White.copy(alpha = 0.90f)
)

fun techPanelBorder(alpha: Float = 0.82f) = BorderStroke(
    width = 1.dp,
    color = Color(0xFFDDE7FA).copy(alpha = alpha)
)
