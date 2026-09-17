package com.tingbili.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.player.PlayerLauncher
import com.tingbili.app.ui.author.AuthorScreen
import com.tingbili.app.ui.history.HistoryScreen
import com.tingbili.app.ui.player.PlayerScreen
import com.tingbili.app.ui.search.SearchScreen
import com.tingbili.app.ui.settings.SettingsScreen
import com.tingbili.app.ui.shelf.ShelfScreen
import kotlinx.coroutines.launch

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

    val context = LocalContext.current
    val container = (context.applicationContext as BiliTingApplication).container
    val scope = rememberCoroutineScope()
    val launcher = remember {
        PlayerLauncher(container.playerHolder, container.playRepo, container.libraryRepo)
    }

    // 是否为 tab 主页面（显示底部栏；播放页/作者页为全屏，不显示底部栏也不显示迷你条）
    val isTab = Tab.entries.any { it.route == currentRoute }
    // 隐藏系统底部导航的全屏页（播放页、作者主页）
    val isFullscreen = currentRoute == "player" || currentRoute?.contains("author") == true

    fun playAndNavigate(item: SearchItem) {
        scope.launch { launcher.playSearchItem(item) }
        navController.navigate("player")
    }

    fun resumeAndNavigate(record: BookRecord) {
        scope.launch { launcher.playRecord(record) }
        navController.navigate("player")
    }

    fun openAuthor(mid: Long, name: String, avatar: String) {
        // 名字/头像含 / : ? 等特殊字符，必须 URL 编码后再塞进路径路由
        navController.navigate("author/$mid/${Uri.encode(name)}/${Uri.encode(avatar)}")
    }

    Scaffold(
        bottomBar = {
            if (isTab) {
                Column {
                    // 迷你播放条置于底部导航上方
                    MiniPlayerBar(
                        holder = container.playerHolder,
                        onOpenPlayer = { navController.navigate("player") }
                    )
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Shelf.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Tab.Shelf.route) {
                ShelfScreen(
                    onOpenPlayer = ::resumeAndNavigate,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.Search.route) {
                SearchScreen(
                    onOpenPlayer = ::playAndNavigate,
                    onOpenAuthor = ::openAuthor,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.History.route) {
                HistoryScreen(
                    onOpenPlayer = ::resumeAndNavigate,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.Settings.route) { SettingsScreen(modifier = Modifier.padding(padding)) }

            composable("player") {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAuthor = { mid, name, avatar -> openAuthor(mid, name, avatar) }
                )
            }
            composable("author/{mid}/{name}/{avatar}") { entry ->
                val mid = entry.arguments?.getString("mid")?.toLongOrNull() ?: 0L
                val name = entry.arguments?.getString("name").orEmpty()
                val avatar = entry.arguments?.getString("avatar").orEmpty()
                AuthorScreen(
                    mid = mid,
                    name = name,
                    avatar = avatar,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = ::playAndNavigate,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}