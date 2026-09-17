package com.tingbili.app.ui.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.time.LocalDate
import java.time.ZoneId

private enum class DateBucket(val label: String) {
    Today("今天"),
    Yesterday("昨天"),
    ThisWeek("本周"),
    Earlier("更早")
}

private fun bucketize(list: List<BookRecord>): List<Pair<DateBucket, List<BookRecord>>> {
    if (list.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val weekStart = today.minusDays(6)
    val order = listOf(DateBucket.Today, DateBucket.Yesterday, DateBucket.ThisWeek, DateBucket.Earlier)
    val buckets = mutableMapOf<DateBucket, MutableList<BookRecord>>()
    list.forEach { rec ->
        val ms = rec.lastPlayedAt.takeIf { it > 0L } ?: rec.favoriteAt.takeIf { it > 0L } ?: 0L
        val date = if (ms > 0L) java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() else today
        val bucket = when {
            date == today -> DateBucket.Today
            date == yesterday -> DateBucket.Yesterday
            !date.isBefore(weekStart) -> DateBucket.ThisWeek
            else -> DateBucket.Earlier
        }
        buckets.getOrPut(bucket) { mutableListOf() }.add(rec)
    }
    return order.mapNotNull { b -> buckets[b]?.let { b to it } }
}

/**
 * 历史页 —— 与听单共用设计语言：
 *   - AppTokens.ShapeHero(24dp) + surface 卡 + 72dp 封面（比听单略小）
 *   - 分组标题：titleSmall 衬线 Semibold + 数量（labelSmall onSurfaceVariant）
 *   - 长按卡片 = 删除单条；右上"清空" = 清空全部
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onOpenPlayer: (BookRecord) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
) {
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BookRecord?>(null) }
    val buckets = remember(history) { bucketize(history) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "历史",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) { Text("清空") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "还没有收听记录",
                    subtitle = "打开任意一本书开始听，过一会儿这里就会出现历史"
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2)
            ) {
                buckets.forEachIndexed { idx, (bucket, list) ->
                    item("bucket-${bucket.name}-header") {
                        Spacer(Modifier.height(if (idx == 0) AppTokens.Spacing2 else AppTokens.Spacing4))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                bucket.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(AppTokens.Spacing2))
                            Text(
                                "${list.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Serif
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(AppTokens.Spacing1))
                    }
                    items(list, key = { "${bucket.name}-${it.id}" }) { record ->
                        HistoryCard(
                            record = record,
                            onClick = { onOpenPlayer(record) },
                            onLongClick = { pendingDelete = record }
                        )
                        Spacer(Modifier.height(AppTokens.Spacing3))
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史") },
            text = { Text("确定要清空全部收听历史吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clear()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条历史") },
            text = { Text("将从历史里移除《${record.title.ifBlank { "未命名" }}》的收听记录；听单收藏不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(record.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 历史卡片 —— 与听单同设计语言
 *   - 72dp 封面（比听单的 96dp 略小，信息密度更高）
 *   - AppTokens.ShapeHero(24dp) + surface 卡
 */
@Composable
private fun HistoryCard(record: BookRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppTokens.ShapeHero)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(AppTokens.Spacing3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(record.cover, size = 72.dp)
        Spacer(Modifier.width(AppTokens.Spacing3))
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
                text = buildHistorySubtitle(record),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildHistorySubtitle(record: BookRecord): String {
    val owner = record.owner.ifBlank { "未知作者" }
    val part = if (record.currentPart > 0) " · 第${record.currentPart}集" else ""
    return "$owner$part"
}