package com.huoyejia.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.hypot

fun Modifier.pressMicroInteraction(
    enabled: Boolean = true,
    shape: Shape = RectangleShape
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val rippleProgress = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clip(shape)
        .drawWithContent {
            drawContent()

            if (rippleAlpha.value > 0.01f) {
                val radius = hypot(size.width, size.height) * rippleProgress.value
                drawCircle(
                    color = Color(0xFFBEEBFF).copy(alpha = rippleAlpha.value),
                    radius = radius,
                    center = pressOffset
                )
            }

            if (pressed || scale.value < 0.995f) {
                drawRoundRect(
                    color = Color(0xFF0B3D91).copy(alpha = 0.13f),
                    size = Size(size.width, size.height),
                    style = Stroke(width = 2.2f)
                )
                drawRoundRect(
                    color = Color(0xFF00A7D8).copy(alpha = 0.06f),
                    size = Size(size.width, size.height)
                )
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressOffset = down.position
                pressed = true

                scope.launch {
                    scale.animateTo(0.965f, tween(durationMillis = 70, easing = FastOutSlowInEasing))
                }
                scope.launch {
                    rippleProgress.snapTo(0f)
                    rippleAlpha.snapTo(0.22f)
                    launch {
                        rippleProgress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
                    }
                    rippleAlpha.animateTo(0f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
                }

                val up = waitForUpOrCancellation()
                pressed = false
                scope.launch {
                    scale.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                if (up == null) {
                    scope.launch { rippleAlpha.animateTo(0f, tween(durationMillis = 90)) }
                }
            }
        }
}
