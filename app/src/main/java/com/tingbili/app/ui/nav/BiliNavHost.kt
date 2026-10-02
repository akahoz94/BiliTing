package com.tingbili.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.api.LinkParser
import com.tingbili.app.data.api.ShortLinkWebViewResolver
import com.tingbili.app.data.api.LinkResult
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.player.PlayerLauncher
import com.tingbili.app.ui.author.AuthorScreen
import com.tingbili.app.ui.discover.DiscoverScreen
import com.tingbili.app.ui.downloads.DownloadScreen
import com.tingbili.app.ui.history.HistoryScreen
import com.tingbili.app.ui.player.PlayerScreen
import com.tingbili.app.ui.search.SearchScreen
import com.tingbili.app.ui.settings.SettingsScreen
import com.tingbili.app.ui.login.LoginScreen
import com.tingbili.app.ui.playlist.PlaylistScreen
import com.tingbili.app.util.ErrorBus
import com.tingbili.app.ui.stats.StatsScreen
import kotlinx.coroutines.launch

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "发现", Icons.Filled.Explore),
    Playlist("playlist", "听单", Icons.Filled.Headphones),
    Search("search", "搜索", Icons.Filled.Search),
    History("history", "历史", Icons.Filled.History),
    Settings("settings", "设置", Icons.Filled.Settings)
}

private const val SETTINGS_ROUTE = "settings"
private val MINI_PLAYER_ROUTES = setOf(
    Tab.Discover.route, Tab.Playlist.route, Tab.Search.route, Tab.History.route, SETTINGS_ROUTE
)

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

    // 外链分享导入：collect Flow，覆盖冷启动 + 前台运行中再分享两种场景。
    LaunchedEffect(Unit) {
        BiliTingApplication.importLinkFlow.collect { raw ->
            if (raw != null) {
                consumePendingImport(container, launcher, raw)
                BiliTingApplication.importLinkFlow.value = null
            }
        }
    }

    val showNavBar = Tab.entries.any { it.route == currentRoute }
    val showMiniPlayer = currentRoute in MINI_PLAYER_ROUTES

    /**
     * 列表里点一下 = 直接开始播，**不跳播放页**。
     * 全屏播放页改成从底部迷你条进入（点一下或上滑展开），对齐 QQ 音乐 / 网易云的手感。
     */
    fun playInline(item: SearchItem) {
        scope.launch {
            // 解析播放地址是网络请求，异常不能让它把进程带走
            runCatching { launcher.playSearchItem(item) }
                .onFailure { ErrorBus.post(message = "播放失败：${it.message ?: "网络异常"}") }
        }
    }

    fun playRecordInline(record: BookRecord) {
        scope.launch {
            runCatching { launcher.playRecord(record) }
                .onFailure { ErrorBus.post(message = "播放失败：${it.message ?: "网络异常"}") }
        }
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
            // 点了 + 必须给反馈，否则用户不知道有没有收进去
            ErrorBus.post(message = "已加入听单")
        }
    }

    fun openAuthor(mid: Long, name: String, avatar: String, bvid: String = "") {
        val safeName = name.ifBlank { "未知UP" }
        val safeAvatar = avatar.ifBlank { "none" }
        if (mid > 0L) {
            navController.navigate("author?mid=$mid&name=${Uri.encode(safeName)}&avatar=${Uri.encode(safeAvatar)}")
            return
        }
        if (bvid.isNotBlank()) {
            scope.launch {
                val resolved = runCatching { container.biliService.view(bvid) }.getOrNull()
                val o = resolved?.data?.owner
                val realMid = o?.mid ?: 0L
                if (realMid > 0L) {
                    val realName = o?.name.orEmpty().ifBlank { safeName }
                    val realAvatar = o?.face.orEmpty().ifBlank { safeAvatar }
                    navController.navigate("author?mid=$realMid&name=${Uri.encode(realName)}&avatar=${Uri.encode(realAvatar)}")
                } else {
                    com.tingbili.app.util.ErrorBus.post(message = "未获取到UP主信息")
                }
            }
        } else {
            com.tingbili.app.util.ErrorBus.post(message = "未获取到UP主信息")
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showMiniPlayer) {
                Column {
                    MiniPlayerBar(
                        holder = container.playerHolder,
                        onOpenPlayer = { navController.navigate("player") },
                        onEmptyClick = {
                            navController.navigate(Tab.Playlist.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    if (showNavBar) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Discover.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Tab.Discover.route, enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) }, popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) }) {
                DiscoverScreen(
                    onOpenPlayer = ::playInline,
                    onOpenAuthor = { mid, name, avatar, bvid -> openAuthor(mid, name, avatar, bvid) },
                    onFavorite = ::favoriteSearchItem,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.Playlist.route, enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) }, popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) }) {
                PlaylistScreen(
                    onOpenPlayer = ::playRecordInline,
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
            composable(Tab.Search.route, enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) }, popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) }) {
                SearchScreen(
                    onOpenPlayer = ::playInline,
                    onOpenAuthor = { mid, name, avatar, bvid -> openAuthor(mid, name, avatar, bvid) },
                    onFavorite = ::favoriteSearchItem,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(Tab.History.route, enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) }, popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) }) {
                HistoryScreen(
                    onOpenPlayer = ::playRecordInline,
                    modifier = Modifier.padding(padding)
                )
            }
            composable(SETTINGS_ROUTE, enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) }, popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) }) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenCrashLogs = { navController.navigate("crash_logs") },
                    onOpenLogin = { navController.navigate("login") },
                    modifier = Modifier.padding(padding)
                )
            }

            composable("login", enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) }, popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) }) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(padding)
                )
            }

            composable("player", enterTransition = { slideInVertically(tween(350)) { it } }, exitTransition = { slideOutVertically(tween(350)) { it } }) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAuthor = { mid, name, avatar, bvid -> openAuthor(mid, name, avatar, bvid) }
                )
            }
            composable("author?mid={mid}&name={name}&avatar={avatar}") { entry ->
                // 用 arguments["mid"] 取原值再 toString：Bundle.getString() 对非 String（Long/Int）
                // 直接返回 null，那样 mid 会静默变成 0，UP 主主页就永远空
                val mid = entry.arguments?.get("mid")?.toString()?.toLongOrNull() ?: 0L
                val name = entry.arguments?.get("name")?.toString().orEmpty()
                val avatar = entry.arguments?.get("avatar")?.toString().orEmpty()
                AuthorScreen(
                    mid = mid,
                    name = name,
                    avatar = avatar,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = ::playInline,
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
            composable("crash_logs") {
                com.tingbili.app.ui.debug.CrashLogsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * 消费外链分享导入：解析 bvid/auid → 收藏进听单（isFavorite）→ 直接播放（已有在播也切过去，不询问）。
 * 标题拉取失败不阻塞导入；非 B 站链接静默提示。
 */
private suspend fun consumePendingImport(
    container: com.tingbili.app.AppContainer,
    launcher: PlayerLauncher,
    raw: String
) {
    var text = raw
    // b23.tv 短链本身不含 bvid/auid，先跟一次重定向拿落地 URL
    if (raw.contains("b23.tv", ignoreCase = true)) {
        LinkParser.resolveShortLink(raw, container.cookieProvider)?.let { text = it }
        // OkHttp 被 412 风控时落地 URL 仍是 b23.tv → 换 WebView 兜底通道再试一次
        if (LinkParser.parse(text) == null) {
            runCatching {
                ShortLinkWebViewResolver.resolve(container.app, raw)?.let { text = it }
            }
        }
    }
    when (val r = LinkParser.parse(text)) {
        is LinkResult.Bvid -> {
            val id = "video:${r.bvid}"
            val v = runCatching { container.biliService.view(r.bvid)?.data }.getOrNull()
            val title = v?.title.orEmpty()
            val cover = v?.cover.orEmpty()
            val ownerName = v?.owner?.name.orEmpty()
            val ownerMid = v?.owner?.mid ?: 0L
            val rec = BookRecord(
                id = id, title = title, owner = ownerName, type = "video", cover = cover,
                bvid = r.bvid, ownerMid = ownerMid,
                isFavorite = true, favoriteAt = System.currentTimeMillis()
            )
            container.libraryRepo.recordPlayed(rec)
            container.libraryRepo.toggleFavorite(id, true)
            runCatching { launcher.playRecord(rec) }
                .onFailure { ErrorBus.post(message = "导入播放失败：${it.message ?: "网络异常"}") }
            ErrorBus.post(message = "已导入：${title.ifBlank { r.bvid }}")
        }
        is LinkResult.Auid -> {
            val id = "audio:${r.auid}"
            val title = runCatching { container.biliService.audioInfo(r.auid)?.data?.title }.getOrNull().orEmpty()
            val rec = BookRecord(
                id = id, title = title, owner = "", type = "audio",
                auid = r.auid, isFavorite = true, favoriteAt = System.currentTimeMillis()
            )
            container.libraryRepo.recordPlayed(rec)
            container.libraryRepo.toggleFavorite(id, true)
            runCatching { launcher.playRecord(rec) }
                .onFailure { ErrorBus.post(message = "导入播放失败：${it.message ?: "网络异常"}") }
            ErrorBus.post(message = "已导入：${title.ifBlank { "au${r.auid}" }}")
        }
        null -> ErrorBus.post(message = "仅支持 B 站视频/音频链接（BVxxx / audio / b23.tv）")
    }
}
