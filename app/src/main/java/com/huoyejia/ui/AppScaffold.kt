package com.huoyejia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val startDestination = "collections"

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
                tabs.forEach { tab ->
NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            if (current != tab.route) {
                                navController.navigate(tab.route)
                            }
                        },
                        icon = { Text(tab.symbol) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.64f),
                            unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
) { padding ->
        Box(
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
                        "正在保存，AI 整理会在后台继续",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
