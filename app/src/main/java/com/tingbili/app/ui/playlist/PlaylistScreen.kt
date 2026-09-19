package com.tingbili.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tingbili.app.data.local.tagSet
import com.tingbili.app.ui.components.CoverImage
import com.tingbili.app.ui.components.EmptyState
import com.tingbili.app.ui.theme.AppTokens

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun PlaylistScreen(
    onOpenPlayer: (BookRecord) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.Factory)
) {
    val favorites by viewModel.favorites.collectAsState()
    val allFavorites by viewModel.allFavorites.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()

    /**
     * 长按管理面板的状态。**只存 id** 而不是 BookRecord 快照：标签是边点边写库的，
     * 存快照的话点完 chip 面板里的"已选"不刷新。
     *
     * [sheetOnTags] 表示同一个抽屉切到了"标签编辑"这一页 —— 关键设计：菜单和标签编辑
     * **共用同一个 ModalBottomSheet**，绝不"关掉一个抽屉再弹一个对话框"。
     * 两个浮层同时存在时，正在退场的那个仍持有全屏 window，会把返回键和触摸一起吃掉，
     * 表现出来就是"点了标签之后退不出去、页面卡在空白处"。
     */
    var sheetTargetId by remember { mutableStateOf<String?>(null) }
    var sheetOnTags by remember { mutableStateOf(false) }

    val sheetRecord = sheetTargetId?.let { id -> allFavorites.firstOrNull { it.id == id } }

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                title = if (selectedTag.isNotBlank()) "「$selectedTag」下还没有" else "听单还是空的",
                                subtitle = if (selectedTag.isNotBlank())
                                    "这个筛选下暂时没有内容，点下面的按钮回到全部听单"
                                else
                                    "搜索结果里长按或点击收藏按钮，听过的书都会自动加进来"
                            )
                            // 一定要留一条"逃出去"的路：筛选后为空时若只有一句空提示，
                            // 用户会以为页面卡死在空白页（顶部 chip 行在小屏 + 多标签时还可能被挤没）
                            if (selectedTag.isNotBlank()) {
                                Spacer(Modifier.height(AppTokens.Spacing2))
                                TextButton(onClick = { viewModel.selectTag("") }) {
                                    Text("显示全部听单")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2)
                    ) {
                        itemsIndexed(favorites, key = { _, r -> r.id }) { _, record ->
                            Spacer(Modifier.height(AppTokens.Spacing3))
                            PlaylistCard(
                                record = record,
                                onClick = { onOpenPlayer(record) },
                                onLongClick = {
                                    sheetTargetId = record.id
                                    sheetOnTags = false
                                }
                            )
                        }
                        item { Spacer(Modifier.height(140.dp)) }
                    }
                }
            }
        }
    }

    if (sheetRecord != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val closeSheet = { sheetTargetId = null; sheetOnTags = false }

        ModalBottomSheet(
            onDismissRequest = closeSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            if (sheetOnTags) {
                TagEditor(
                    record = sheetRecord,
                    knownTags = tags,
                    onCommit = { viewModel.setTags(sheetRecord.id, it) },
                    onBack = { sheetOnTags = false },
                    onDone = closeSheet
                )
            } else {
                PlaylistMenu(
                    record = sheetRecord,
                    index = favorites.indexOfFirst { it.id == sheetRecord.id },
                    total = favorites.size,
                    onMoveUp = { viewModel.moveUp(sheetRecord.id); closeSheet() },
                    onMoveDown = { viewModel.moveDown(sheetRecord.id); closeSheet() },
                    onEditTags = { sheetOnTags = true },
                    onArchive = { viewModel.setFinished(sheetRecord.id, true); closeSheet() },
                    onUnfavorite = { viewModel.unfavorite(sheetRecord.id); closeSheet() },
                    onDelete = { viewModel.delete(sheetRecord.id); closeSheet() }
                )
            }
        }
    }
}

/** 长按后的操作菜单（抽屉第一页） */
@Composable
private fun PlaylistMenu(
    record: BookRecord,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditTags: () -> Unit,
    onArchive: () -> Unit,
    onUnfavorite: () -> Unit,
    onDelete: () -> Unit
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
        SheetItem(Icons.Filled.ArrowUpward, "上移", enabled = index > 0, onClick = onMoveUp)
        SheetItem(
            Icons.Filled.ArrowDownward,
            "下移",
            enabled = index in 0 until total - 1,
            onClick = onMoveDown
        )
        SheetItem(Icons.Filled.Label, "设置标签", onClick = onEditTags)
        SheetItem(Icons.Filled.Archive, "标为听完归档", onClick = onArchive)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = AppTokens.Spacing1)
        )
        SheetItem(Icons.Filled.BookmarkRemove, "从听单移除", onClick = onUnfavorite)
        SheetItem(Icons.Filled.DeleteForever, "彻底删除", danger = true, onClick = onDelete)
    }
}

/**
 * 标签编辑（抽屉第二页，多选、点一下即存）。
 *
 * 之所以塞进抽屉而不是开 AlertDialog：软键盘一弹，AlertDialog 会被整体顶上去，
 * 底部按钮正好被键盘盖住，用户看到的就是"没有返回按键、点都点不动"。
 * 抽屉自带 IME 避让，这一页顶部有"返回菜单"、底部有"完成"，键盘弹着也一定够得着。
 *
 * ⚠️ 勾选状态是**本页自己的 local state**，不是直接读 `record.tagSet()`：
 * 读外部流的话，写库 → Room 失效通知 → 重新查询 → stateIn 发射 这条链有延迟，
 * 用户连点两下（或第一下还没回流就点第二下）时，第二下会基于**旧值**重算，
 * 把第一下顶掉 —— 表现就是"打两个标签，另一个自动消失 / 只能打一个"。
 * 现在以本地选中集为准（点一下立刻高亮、立刻落库），外部数据只用于初始化。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    record: BookRecord,
    knownTags: List<String>,
    onCommit: (List<String>) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    // 本地选中集：以 record.id 为 key，只在换书时重新初始化
    var selected by remember(record.id) { mutableStateOf(record.tagSet()) }
    var custom by remember(record.id) { mutableStateOf("") }
    val commit: (List<String>) -> Unit = { next ->
        selected = next
        onCommit(next)
    }
    // 预设 + 听单里已经出现过的标签 + 本页刚加的自定义标签，去重后一起摆出来
    val palette = (listOf("通勤", "睡前", "学习", "工作") + knownTags + selected).distinct()

    Column(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = AppTokens.Spacing3)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppTokens.Spacing2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回菜单")
            }
            Text(
                text = "设置标签",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Text(
            text = record.title.ifBlank { "未命名" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = AppTokens.Spacing4)
        )
        Spacer(Modifier.height(AppTokens.Spacing2))
        Text(
            text = if (selected.isEmpty()) "可以多选，一本书能挂多个标签（点一下即保存）"
                   else "已选：${selected.joinToString("、")}",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = AppTokens.Spacing4)
        )
        Spacer(Modifier.height(AppTokens.Spacing3))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing4),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            palette.forEach { tag ->
                // key(tag)：chip 数量变化（新增标签）时整行会重排，带 key 才能保证
                // Compose 复用同一个节点 —— 否则手指按下的那一瞬间节点被替换，点击会被取消
                key(tag) {
                    FilterChip(
                        selected = tag in selected,
                        onClick = {
                            commit(if (tag in selected) selected - tag else selected + tag)
                        },
                        label = { Text(tag) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
        }
        Spacer(Modifier.height(AppTokens.Spacing4))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("新标签名") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(AppTokens.Spacing2))
            IconButton(
                onClick = {
                    val t = custom.trim()
                    if (t.isNotBlank()) {
                        commit(if (t in selected) selected else selected + t)
                        custom = ""
                    }
                },
                enabled = custom.isNotBlank()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加标签")
            }
        }

        Spacer(Modifier.height(AppTokens.Spacing4))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(AppTokens.Spacing3))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing4),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing3)
        ) {
            if (selected.isNotEmpty()) {
                TextButton(onClick = { commit(emptyList()) }, modifier = Modifier.weight(1f)) {
                    Text("清空标签", color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun PlaylistCard(record: BookRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    val progress = if (record.durationMs > 0L) {
        (record.progressMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val hasProgress = record.durationMs > 0L && record.progressMs > 0L
    val recordTags = record.tagSet()

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
                recordTags.take(3).forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (recordTags.size > 3) {
                    Text(
                        text = "+${recordTags.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
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
