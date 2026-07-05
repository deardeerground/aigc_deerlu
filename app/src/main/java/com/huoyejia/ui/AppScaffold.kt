package com.huoyejia.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class Tab(val route: String, val label: String, val symbol: String)

private val tabs = listOf(
    Tab("collections", "回流箱", "↺"),
    Tab("capture", "采集", "+"),
    Tab("review", "复习", "✓"),
    Tab("dashboard", "指数", "#"),
    Tab("settings", "设置", "⚙")
)

@Composable
fun HuoyejiaScaffold(
    navController: NavHostController,
    isBusy: Boolean,
    tabNavDirection: MutableState<Float>,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()

    val startDestination = "collections"
    fun navigateToMainTab(route: String) {
        if (current != route) {
            val currentIndex = tabs.indexOfFirst { it.route == current }
            val targetIndex = tabs.indexOfFirst { it.route == route }
            tabNavDirection.value = if (targetIndex > currentIndex) 1f else -1f
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(startDestination) { saveState = false }
                restoreState = false
            }
        }
    }


    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xF2FFFFFF).copy(alpha = 0.78f),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF).copy(alpha = 0.88f), Color(0xFFE9F5FF).copy(alpha = 0.78f))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF9DCAFF).copy(alpha = 0.82f),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                tabs.forEach { tab ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToMainTab(tab.route) },
                        icon = { NavSymbol(symbol = tab.symbol, selected = selected) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    )
                }
            }
        }
    ) { padding ->

        TechBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(current) {
                    var horizontalDrag = 0f
                    var verticalDrag = 0f
                    detectDragGestures(
                        onDragStart = {
                            horizontalDrag = 0f
                            verticalDrag = 0f
                        },
                        onDrag = { change, amount ->
                            horizontalDrag += amount.x
                            verticalDrag += amount.y
                            if (kotlin.math.abs(horizontalDrag) > kotlin.math.abs(verticalDrag) * 1.25f) {
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            val currentIndex = tabs.indexOfFirst { it.route == current }
                            val isHorizontalIntent = kotlin.math.abs(horizontalDrag) > kotlin.math.abs(verticalDrag) * 1.35f
                            if (currentIndex >= 0 && isHorizontalIntent && kotlin.math.abs(horizontalDrag) > 64f) {
                                val nextIndex = if (horizontalDrag < 0) currentIndex + 1 else currentIndex - 1
                                tabs.getOrNull(nextIndex)?.let { navigateToMainTab(it.route) }
                            }
                        }
                    )
                }
        ) {
            content(PaddingValues(0.dp))
            if (isBusy) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "正在保存，AI 会在后台继续整理",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TechPrimaryGradient)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun NavSymbol(symbol: String, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        label = "nav-symbol-scale"
    )
    val background = if (selected) {
        Brush.linearGradient(listOf(Color(0xFF4FC3FF), Color(0xFF1976FF)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFFFFF).copy(alpha = 0.88f), Color(0xFFE6F3FF).copy(alpha = 0.72f)))
    }
    val navShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .size(30.dp)
            .pressMicroInteraction(shape = navShape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = navShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
