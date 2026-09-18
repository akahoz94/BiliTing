package com.tingbili.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.ui.components.CoverImage
import com.tingbili.app.ui.components.EmptyState
import com.tingbili.app.ui.theme.AppTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    onOpenPlayer: (BookRecord) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.Factory)
) {
    val favorites by viewModel.favorites.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    var pendingAction by remember { mutableStateOf<BookRecord?>(null) }
    var showTagDialog by remember { mutableStateOf<BookRecord?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("清空已归档") },
                            onClick = {
                                menuExpanded = false
                                viewModel.clearArchived()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 横向标签筛选栏
            if (tags.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTag.isBlank(),
                        onClick = { viewModel.selectTag("") },
                        label = { Text("全部") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { viewModel.selectTag(if (selectedTag == tag) "" else tag) },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (favorites.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(AppTokens.Spacing5),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = if (selectedTag.isNotBlank()) "「$selectedTag」下还没有" else "听单还是空的",
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
    }

    // 长按管理菜单：底部抽屉，带图标，危险操作独立置底
    pendingAction?.let { record ->
        val idx = favorites.indexOfFirst { it.id == record.id }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pendingAction = null },
            sheetState = sheetState
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(bottom = AppTokens.Spacing3)
            ) {
                Text(
                    text = record.title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(AppTokens.Spacing1))
                SheetItem(Icons.Filled.ArrowUpward, "上移", enabled = idx > 0) {
                    viewModel.moveUp(idx); pendingAction = null
                }
                SheetItem(Icons.Filled.ArrowDownward, "下移", enabled = idx >= 0 && idx < favorites.size - 1) {
                    viewModel.moveDown(idx); pendingAction = null
                }
                SheetItem(Icons.Filled.Label, "设置标签") {
                    showTagDialog = record; pendingAction = null
                }
                SheetItem(Icons.Filled.Download, "下载缓存") {
                    val dm = (ctx.applicationContext as com.tingbili.app.BiliTingApplication).container.downloadManager
                    scope.launch {
                        dm.download(
                            recordId = record.id,
                            bookTitle = record.title,
                            cover = record.cover,
                            bvid = record.bvid,
                            cid = record.currentCid,
                            auid = record.auid,
                            partTitle = "全集"
                        )
                    }
                    pendingAction = null
                }
                SheetItem(Icons.Filled.Archive, "标为听完归档") {
                    viewModel.setFinished(record.id, true); pendingAction = null
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = AppTokens.Spacing1)
                )
                SheetItem(Icons.Filled.BookmarkRemove, "从听单移除") {
                    viewModel.unfavorite(record.id); pendingAction = null
                }
                SheetItem(Icons.Filled.DeleteForever, "彻底删除", danger = true) {
                    viewModel.delete(record.id); pendingAction = null
                }
            }
        }
    }

    // 设置标签对话框
    showTagDialog?.let { record ->
        var tagText by remember(record.id) { mutableStateOf(record.tag) }
        AlertDialog(
            onDismissRequest = { showTagDialog = null },
            title = { Text("设置标签") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it },
                        label = { Text("标签名（如：通勤/睡前/学习）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("通勤", "睡前", "学习", "工作").forEach { preset ->
                            TextButton(onClick = { tagText = preset }) { Text(preset) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTag(record.id, tagText.trim())
                    showTagDialog = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = null }) { Text("取消") }
            }
        )
    }
}

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.tag.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = record.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = buildSubtitle(record),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

@Composable
private fun SheetItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppTokens.Spacing4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppTokens.Spacing4))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
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
