package com.tingbili.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.ui.components.CoverImage
import com.tingbili.app.ui.components.EmptyState
import com.tingbili.app.ui.theme.AppTokens

/**
 * 听单页 —— 第一版设计语言（统一全局）
 *
 * 视觉规范（与搜索/历史/设置 Tab 共享）：
 *   - 字体：Noto Serif SC 衬线（与小说听书调性契合）
 *   - 卡片：surface 底色 + AppTokens.RadiusHero(24dp) 圆角 + AppTokens.Spacing4(16dp) 内边距
 *   - 封面：AppTokens.RadiusCover(16dp) 圆角，96dp 大封面
 *   - 进度条：AppTokens.ProgressTrackHero(6dp) 高，primary 主色 + outlineVariant 半透明轨道
 *   - 迷你播放器：surfaceContainerHigh 底色，圆形 primary 播放按钮
 *   - 标题字重：Bold（700）/ Semibold（600）；AppBar 标题 28sp display 风格
 *   - 0 emoji，所有图标用 Material Icons
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    onOpenPlayer: (BookRecord) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.Factory)
) {
    val favorites by viewModel.favorites.collectAsState()
    var pendingAction by remember { mutableStateOf<BookRecord?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "听单",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { /* 由 NavHost 路由到搜索 */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { /* 三点菜单：清空听单 / 排序 */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (favorites.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(AppTokens.Spacing5),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "听单还是空的",
                        subtitle = "搜索结果里长按或点击收藏按钮，听过的书都会自动加进来"
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2)
                ) {
                    itemsIndexed(favorites, key = { _, r -> r.id }) { index, record ->
                        Spacer(Modifier.height(AppTokens.Spacing3))
                        PlaylistCard(
                            record = record,
                            onClick = { onOpenPlayer(record) },
                            onLongClick = { pendingAction = record }
                        )
                    }
                    item { Spacer(Modifier.height(140.dp)) }
                }
            }
        }
    }

    pendingAction?.let { record ->
        val idx = favorites.indexOfFirst { it.id == record.id }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("管理《${record.title.ifBlank { "未命名" }}》") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.moveUp(idx); pendingAction = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("上移", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { viewModel.moveDown(idx); pendingAction = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("下移", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { viewModel.setFinished(record.id, true); pendingAction = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("标为听完归档", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = {
                        viewModel.unfavorite(record.id)
                        pendingAction = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("从听单移除", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = {
                        viewModel.delete(record.id)
                        pendingAction = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("彻底删除", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 听单卡片 — 第一版设计语言：
 *   - AppTokens.RadiusHero(24dp) 圆角 surface 卡片
 *   - 96dp 封面（AppTokens.RadiusCover 16dp 圆角）
 *   - 双行：标题（衬线 Semibold）+ 副标题（onSurfaceVariant）
 *   - 进度条（仅在有进度时显示）：AppTokens.ProgressTrackHero(6dp) + primary 色 + 右侧百分比
 */
@Composable
private fun PlaylistCard(record: BookRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    val progress = if (record.durationMs > 0L) {
        (record.progressMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val hasProgress = record.durationMs > 0L && record.progressMs > 0L

    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppTokens.ShapeHero)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(AppTokens.Spacing4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(record.cover, size = 96.dp)
        Spacer(Modifier.width(AppTokens.Spacing4))
        Column(Modifier.weight(1f)) {
            Text(
                text = record.title.ifBlank { "未命名" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AppTokens.Spacing1))
            Text(
                text = buildSubtitle(record),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasProgress) {
                Spacer(Modifier.height(AppTokens.Spacing2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f)
                            .height(AppTokens.ProgressTrackHero)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(AppTokens.Spacing2))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildSubtitle(record: BookRecord): String {
    val owner = record.owner.ifBlank { "未知作者" }
    return when {
        record.totalParts > 1 -> "$owner · 第${record.currentPart}集 / 共${record.totalParts}集"
        record.totalParts == 1 -> "$owner · 单集"
        else -> owner
    }
}