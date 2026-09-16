package com.tingbili.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tingbili.app.ui.history.HistoryScreen
import com.tingbili.app.ui.player.PlayerScreen
import com.tingbili.app.ui.search.SearchScreen
import com.tingbili.app.ui.settings.SettingsScreen
import com.tingbili.app.ui.shelf.ShelfScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Shelf("shelf", "书架", Icons.Filled.Bookmarks),
    Search("search", "搜索", Icons.Filled.Search),
    History("history", "历史", Icons.Filled.History),
    Settings("settings", "设置", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            // 仅 4 个 tab 页面显示底部栏；全屏播放页 "player" 不显示
            if (Tab.entries.any { it.route == currentRoute }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Shelf.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Shelf.route) {
                ShelfScreen(onOpenPlayer = { navController.navigate("player") })
            }
            composable(Tab.Search.route) {
                SearchScreen(onOpenPlayer = { navController.navigate("player") })
            }
            composable(Tab.History.route) {
                HistoryScreen(onOpenPlayer = { navController.navigate("player") })
            }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable("player") { PlayerScreen() }
        }
    }
}
