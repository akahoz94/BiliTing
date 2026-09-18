package com.tingbili.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.repo.SearchRepository
import com.tingbili.app.ui.components.CoverImage
import com.tingbili.app.ui.components.SegmentChip
import com.tingbili.app.ui.theme.AppTokens
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.FormatUtil

/**
 * 搜索页 —— 与听单共用第一版设计语言
 *   - 衬线字（Noto Serif SC）
 *   - 搜索栏：OutlinedTextField + 28dp 全圆角
 *   - "听书优先"：ModeCard + 段控件（与 Settings 复用）
 *   - 关键词历史：chip 列表（与 Settings 复用 SegmentChip）
 *   - 结果行：24dp 圆角 surface 卡片 + 64dp 封面 + 双行 + 加号
 *   - 0 emoji
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenPlayer: (SearchItem) -> Unit = {},
    onOpenAuthor: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onFavorite: (SearchItem) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val s by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "搜索",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ===== 搜索栏 =====
            OutlinedTextField(
                value = s.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2),
                placeholder = {
                    Text(
                        "搜索：有声书 / 小说 / 广播剧 / 播客",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif
                        )
                    )
                },
                singleLine = true,
                shape = AppTokens.ShapeHero,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (s.keyword.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onKeywordChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(1) })
            )

            // ===== 搜索历史 chip =====
            if (s.history.isNotEmpty() && s.results.isEmpty() && !s.loading) {
                Column(Modifier.padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing1)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "最近搜索",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearHistory) {
                            Text("清空", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(AppTokens.Spacing1))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing2),
                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing2)
                    ) {
                        s.history.forEach { kw ->
                            SegmentChip(
                                label = kw,
                                selected = false,
                                onClick = { viewModel.applyHistory(kw) }
                            )
                        }
                    }
                }
            }

            // ===== 听书优先开关 =====
            Row(
                Modifier
                    .padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing1)
                    .fillMaxWidth()
                    .clip(AppTokens.ShapeModule)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "听书优先",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "只显示有声小说 / 广播剧 / 有声书 / 有声剧",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(AppTokens.Spacing3))
                Switch(
                    checked = s.listeningOnly,
                    onCheckedChange = viewModel::onListeningOnlyChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }

            if (s.error != null) {
                Text(
                    s.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    modifier = Modifier.padding(AppTokens.Spacing3)
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(AppTokens.Spacing4),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing3)
            ) {
                items(s.results, key = { it.bvid.ifBlank { it.aid.toString() } }) { item ->
                    SearchResultRow(
                        item = item,
                        onClick = { onOpenPlayer(item) },
                        onOpenAuthor = { onOpenAuthor(item.uid, item.author, item.upic, item.bvid) },
                        onFavorite = { onFavorite(item) }
                    )
                }
                if (s.hasMore) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载更多") }
                    }
                }
            }
        }
    }
}

/**
 * 搜索结果行 —— 与听单卡片同设计语言：
 *   - AppTokens.ShapeHero(24dp) 圆角 surface 卡
 *   - 64dp 封面（AppTokens.RadiusCover 16dp 圆角）
 *   - 双行：标题 + UP 主 + 时长/类型
 *   - 右侧 36dp 圆形 primaryContainer 加号按钮
 */
@Composable
fun SearchResultRow(item: SearchItem, onClick: () -> Unit, onOpenAuthor: () -> Unit, onFavorite: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppTokens.ShapeHero)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(AppTokens.Spacing3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(item.pic.orEmpty(), size = 64.dp)
        Spacer(Modifier.width(AppTokens.Spacing3))
        Column(Modifier.weight(1f)) {
            Text(
                SearchRepository.stripHtml(item.title),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AppTokens.Spacing1))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenAuthor)
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val upic = CoverUtil.normalize(item.upic)
                    if (upic.isNotBlank()) {
                        AsyncImage(
                            model = upic,
                            contentDescription = item.author,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            item.author.take(1).ifBlank { "·" },
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.width(AppTokens.Spacing1 + 2.dp))
                Text(
                    item.author,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Spacer(Modifier.width(AppTokens.Spacing1 + 2.dp))
                Text(
                    "${item.durationStr.ifBlank { FormatUtil.duration("") }} · ${if (item.bvid.isNotBlank()) "视频" else "音频"}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(AppTokens.Spacing2))
        // 右侧圆形加号 → 加入听单（收藏）
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "加入听单",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        // 长按弹出操作菜单
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("加入听单") },
                leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                onClick = { onFavorite(); menuExpanded = false }
            )
            DropdownMenuItem(
                text = { Text("查看UP主") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                onClick = { onOpenAuthor(); menuExpanded = false }
            )
        }
    }
}