package com.huoyejia.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()

    // 获取导航图的起始路由（应该是"collections"）
    val startDestination = "collections"


    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White.copy(alpha = 0.88f),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.96f), Color(0xFFEAF8FF).copy(alpha = 0.90f))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFB9D9FF).copy(alpha = 0.78f),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                tabs.forEach { tab ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (current != tab.route) {
                                navController.navigate(tab.route) {
                                    launchSingleTop = true
                                    // 弹出到起始页面，确保每次点击都从根状态开始
                                    popUpTo(startDestination) { saveState = false }
                                    restoreState = false
                                }
                            }
                        },
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
        Brush.linearGradient(listOf(Color(0xFF00D7FF), Color(0xFF1769E8)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.86f), Color(0xFFEAF4FF).copy(alpha = 0.72f)))
    }
    Surface(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(12.dp),
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
