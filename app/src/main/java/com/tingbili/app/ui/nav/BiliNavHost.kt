package com.tingbili.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tingbili.app.ui.downloads.DownloadScreen
import com.tingbili.app.ui.history.HistoryScreen
import com.tingbili.app.ui.player.PlayerScreen
import com.tingbili.app.ui.search.SearchScreen
import com.tingbili.app.ui.settings.SettingsScreen
import com.tingbili.app.ui.playlist.PlaylistScreen
import com.tingbili.app.util.ErrorBus
import com.tingbili.app.ui.stats.StatsScreen
import kotlinx.coroutines.launch

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Playlist("playlist", "听单", Icons.Filled.Headphones),
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
    val snackbarHostState = remember { SnackbarHostState() }
    val launcher = remember {
        container.playerLauncher
    }

    LaunchedEffect(Unit) {
        ErrorBus.errors.collect { err ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = err.message,
                    actionLabel = if (err.retry != null) err.retryLabel else null,
                    withDismissAction = err.retry == null,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    err.retry?.invoke()
                }
            }
        }
    }

    val isTab = Tab.entries.any { it.route == currentRoute }
    val isFullscreen = currentRoute == "player" || currentRoute?.contains("author") == true

    fun playAndNavigate(item: SearchItem) {
        scope.launch { launcher.playSearchItem(item) }
        navController.navigate("player")
    }

    fun resumeAndNavigate(record: BookRecord) {
        scope.launch { launcher.playRecord(record) }
        navController.navigate("player")
    }

    fun favoriteSearchItem(item: SearchItem) {
        scope.launch {
            val cover = if (item.cover.isNotBlank()) item.cover else item.pic
            val id = if (item.type == "audio" || item.bvid.isBlank()) "audio:${item.aid}" else "video:${item.bvid}"
            val record = BookRecord(
                id = id,
                title = com.tingbili.app.data.repo.SearchRepository.stripHtml(item.title),
                owner = item.author,
                type = if (item.type == "audio") "audio" else "video",
                cover = cover,
                bvid = item.bvid.ifBlank { null },
                auid = item.aid,
                ownerMid = item.uid,
                ownerAvatar = item.upic,
                isFavorite = true,
                favoriteAt = System.currentTimeMillis()
            )
            container.libraryRepo.recordPlayed(record)
            container.libraryRepo.toggleFavorite(id, true)
        }
    }

    fun openAuthor(mid: Long, name: String, avatar: String) {
        val safeName = name.ifBlank { "未知UP" }
        val safeAvatar = avatar.ifBlank { "none" }
        navController.navigate("author?mid=$mid&name=${Uri.encode(safeName)}&avatar=${Uri.encode(safeAvatar)}")
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTab) {
                Column {
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
            startDestination = Tab.Playlist.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Tab.Playlist.route) {
                PlaylistScreen(
                    onOpenPlayer = ::resumeAndNavigate,
                    onOpenSearch = {
                        navController.navigate(Tab.Search.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.Search.route) {
                SearchScreen(
                    onOpenPlayer = ::playAndNavigate,
                    onOpenAuthor = ::openAuthor,
                    onFavorite = ::favoriteSearchItem,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.History.route) {
                HistoryScreen(
                    onOpenPlayer = ::resumeAndNavigate,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onOpenStats = { navController.navigate("stats") },
                    onOpenDownloads = { navController.navigate("downloads") },
                    modifier = Modifier.padding(padding)
                )
            }

            composable("player") {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAuthor = { mid, name, avatar -> openAuthor(mid, name, avatar) }
                )
            }
            composable("author?mid={mid}&name={name}&avatar={avatar}") { entry ->
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
            composable("stats") {
                StatsScreen(modifier = Modifier.padding(padding))
            }
            composable("downloads") {
                DownloadScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate("player") }
                )
            }
        }
    }
}