package com.tingbili.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.ui.common.ListItemRow

/**
 * 听单页：
 *  - 顶部"继续播放卡"（若 PlayerHolder 中正在播放且非空）
 *  - 最近播放区（取最近 6 条收藏，按 lastPlayedAt 倒序）
 *  - 分组列表（按 mode 切换）：
 *     0 按文件夹（SettingsStore.shelfFoldersFlow）
 *     1 按 UP 主（按 record.owner 聚类，UP 主 ID 写在 record.ownerId）
 *  - 顶部 groupMode 切换 FilterChip
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onOpenPlayer: (BookRecord) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.Factory)
) {
    val favorites by viewModel.favorites.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val groupMode by viewModel.groupMode.collectAsState()
    val continueTarget by viewModel.continueTarget.collectAsState()

    Scaffold(modifier = modifier, topBar = {
        Column {
            TopAppBar(title = { Text("听单") })
            // 分组模式切换
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = groupMode == 0,
                    onClick = { viewModel.setGroupMode(0) },
                    label = { Text("按文件夹") }
                )
                FilterChip(
                    selected = groupMode == 1,
                    onClick = { viewModel.setGroupMode(1) },
                    label = { Text("按 UP 主") }
                )
            }
        }
    }) { padding ->
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("听单还是空的，去搜索添加一本吧", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            val grouped = remember(favorites, folders, groupMode) {
                val sorted = favorites.sortedByDescending { it.favoriteAt }
                val map: Map<String, List<BookRecord>> = if (groupMode == 1) {
                    // 按 UP 主：未标注 ownerId 的归入"未知作者"
                    sorted.groupBy { it.owner.ifBlank { "未知作者" } }
                        .toSortedMap()
                } else {
                    // 按文件夹：未分组的归入"未分组"
                    val foldersMap = folders
                    sorted.groupBy { foldersMap[it.id]?.takeIf { f -> f.isNotBlank() } ?: "未分组" }
                        .toSortedMap(compareByDescending<String> { it == "未分组" }.thenBy { it })
                }
                map
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 继续播放卡
                if (continueTarget != null) {
                    item("continue") {
                        ContinueCard(record = continueTarget!!, onClick = { onOpenPlayer(continueTarget!!) })
                    }
                }

                // 最近播放（最多 6 条）—— 仅按文件夹模式下显示（按 UP 主模式下分组本身已经按时间倒序）
                if (recent.isNotEmpty() && groupMode == 0) {
                    item("recent-header") {
                        SectionHeader("最近播放")
                    }
                    items(recent, key = { "recent-${it.id}" }) { record ->
                        ListItemRow(record = record, onClick = { onOpenPlayer(record) })
                    }
                    item("recent-divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }

                // 分组
                grouped.forEach { (folder, list) ->
                    item("folder-${folder}") {
                        SectionHeader(
                            text = if (groupMode == 1) folder else if (folder == "未分组") "全部收藏" else folder,
                            count = list.size
                        )
                    }
                    items(list, key = { "fav-${it.id}" }) { record ->
                        ListItemRow(record = record, onClick = { onOpenPlayer(record) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ContinueCard(record: BookRecord, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (record.cover.isNotBlank()) {
            AsyncImage(
                model = record.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "继续播放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                record.title.ifBlank { "未命名" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}