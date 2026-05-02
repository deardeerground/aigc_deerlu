package com.huoyejia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class Tab(val route: String, val label: String, val symbol: String)

private val tabs = listOf(
    Tab("inbox", "回流箱", "↺"),
    Tab("capture", "采集", "+"),
    Tab("review", "复习", "✓"),
    Tab("history", "历史", "⌕"),
    Tab("explain", "讲解", "▶"),
    Tab("dashboard", "指数", "#")
)

@Composable
fun HuoyejiaScaffold(
    navController: NavHostController,
    isBusy: Boolean,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                launchSingleTop = true
                                popUpTo("inbox") { saveState = true }
                                restoreState = true
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
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp)
                )
            }
        }
    }
}
