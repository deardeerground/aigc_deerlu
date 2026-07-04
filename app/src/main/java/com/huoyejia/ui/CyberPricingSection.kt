package com.huoyejia.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun CyberPricingSection() {
    val tiers = remember {
        listOf(
            PricingTier(
                name = "LOCAL",
                price = "¥0",
                signal = "LOCAL CORE",
                description = "Room 本地知识库、离线复习、基础卡片整理。",
                features = listOf("本地收藏夹", "OCR 文本入库", "离线复习卡")
            ),
            PricingTier(
                name = "NEURAL",
                price = "¥19",
                signal = "CLOUD SYNC",
                description = "云端 AI 生成、提示词隔离、网页正文抽取。",
                features = listOf("后端代理 API", "AI 摘要/标签", "上下文隔离")
            ),
            PricingTier(
                name = "ORBIT",
                price = "¥49",
                signal = "AIGC OUTPUT",
                description = "多模态素材、PPT/视频生成与团队协作预留。",
                features = listOf("图片向量检索", "PPT/视频生成", "团队空间预留")
            )
        )
    }
    var selectedIndex by remember { mutableIntStateOf(1) }
    val sectionShape = RoundedCornerShape(34.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0D6DFF), Color(0xFF123E8A), Color(0xFF071936)),
                    radius = 980f
                )
            )
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFF8FE7FF).copy(alpha = 0.42f),
                    cornerRadius = CornerRadius(34.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF4FC3FF).copy(alpha = 0.16f),
                    cornerRadius = CornerRadius(34.dp.toPx()),
                    style = Stroke(width = 8.dp.toPx())
                )
            }
    ) {
        CyberGridBackdrop()
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "NEURAL GLASS TIERS",
                        color = Color(0xFFBEEBFF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = 2.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "知汇卡能力中枢",
                        color = Color(0xFFFFFFFF),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFBEEBFF).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFBEEBFF).copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "AIGC",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color(0xFFBEEBFF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "为答辩展示准备的端云协同能力区：本地数据留在 App，云端负责 AI 生成、网页抓取和提示词隔离。",
                color = Color(0xFFD9F3FF),
                fontFamily = FontFamily.Monospace,
                lineHeight = 19.sp,
                fontSize = 12.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiers.forEachIndexed { index, tier ->
                    CyberPricingCard(
                        tier = tier,
                        active = selectedIndex == index,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    }
}

@Composable
private fun CyberGridBackdrop() {
    val transition = rememberInfiniteTransition(label = "cyber-grid")
    val scanOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan-offset"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "grid-drift"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val grid = 34.dp.toPx()
        var x = -grid + drift
        while (x < size.width + grid) {
            drawLine(
                color = Color(0xFF8FE7FF).copy(alpha = 0.12f),
                start = Offset(x, 0f),
                end = Offset(x + size.height * 0.16f, size.height),
                strokeWidth = 1f
            )
            x += grid
        }
        var y = -grid + drift
        while (y < size.height + grid) {
            drawLine(
                color = Color(0xFF8FE7FF).copy(alpha = 0.10f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += grid
        }

        val scanY = size.height * scanOffset
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFFBEEBFF).copy(alpha = 0.18f), Color.Transparent),
                startY = max(0f, scanY - 42.dp.toPx()),
                endY = scanY + 42.dp.toPx()
            ),
            topLeft = Offset(0f, max(0f, scanY - 42.dp.toPx())),
            size = Size(size.width, 84.dp.toPx())
        )
    }
}

@Composable
private fun CyberPricingCard(
    tier: PricingTier,
    active: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val lifted = active || pressed
    val scale by animateFloatAsState(
        targetValue = if (lifted) 1.018f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "pricing-scale"
    )
    val glow by animateFloatAsState(
        targetValue = if (lifted) 1f else 0.34f,
        animationSpec = tween(durationMillis = 180),
        label = "pricing-glow"
    )
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (lifted) -5f else 0f
            }
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFFBEEBFF).copy(alpha = 0.22f * glow),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 12.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF5B6CFF).copy(alpha = 0.32f * glow),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
            .pressMicroInteraction(shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = Color(0xFF092A5E).copy(alpha = if (active) 0.76f else 0.58f),
        border = BorderStroke(
            width = 1.dp,
            color = if (active) Color(0xFFBEEBFF).copy(alpha = 0.88f) else Color(0xFF7FD8FF).copy(alpha = 0.34f)
        )
    ) {
        Box {
            CornerBrackets(active = active)
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tier.name,
                            color = if (active) Color(0xFFBEEBFF) else Color(0xFFD9F3FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.8.sp
                        )
                        Text(
                            text = tier.description,
                            color = Color(0xFFD9F3FF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = tier.price,
                            color = Color(0xFFFFFFFF),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "/mo",
                            color = Color(0xFFBEEBFF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tier.features.forEach { feature ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0E3A7A).copy(alpha = 0.76f),
                            border = BorderStroke(1.dp, Color(0xFFBEEBFF).copy(alpha = 0.20f))
                        ) {
                            Text(
                                text = feature,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                                color = Color(0xFFEAF8FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) Color(0xFFBEEBFF).copy(alpha = 0.18f) else Color(0xFFFFFFFF).copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color(0xFFBEEBFF).copy(alpha = if (active) 0.52f else 0.18f))
                ) {
                    Text(
                        text = tier.signal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (active) Color(0xFFFFFFFF) else Color(0xFFCFEFFF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerBrackets(active: Boolean) {
    val color = if (active) Color(0xFFBEEBFF).copy(alpha = 0.84f) else Color(0xFF7FD8FF).copy(alpha = 0.32f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = 10.dp.toPx()
        val length = 18.dp.toPx()
        val stroke = Stroke(width = 1.4.dp.toPx())
        val path = Path().apply {
            moveTo(inset, inset + length)
            lineTo(inset, inset)
            lineTo(inset + length, inset)
            moveTo(size.width - inset - length, inset)
            lineTo(size.width - inset, inset)
            lineTo(size.width - inset, inset + length)
            moveTo(inset, size.height - inset - length)
            lineTo(inset, size.height - inset)
            lineTo(inset + length, size.height - inset)
            moveTo(size.width - inset - length, size.height - inset)
            lineTo(size.width - inset, size.height - inset)
            lineTo(size.width - inset, size.height - inset - length)
        }
        drawPath(path = path, color = color, style = stroke)
    }
}

private data class PricingTier(
    val name: String,
    val price: String,
    val signal: String,
    val description: String,
    val features: List<String>
)


