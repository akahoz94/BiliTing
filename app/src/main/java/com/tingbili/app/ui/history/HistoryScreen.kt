package com.tingbili.app.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.ui.common.ListItemRow
import java.time.LocalDate
import java.time.ZoneId

private enum class DateBucket(val label: String) {
    Today("今天"),
    Yesterday("昨天"),
    ThisWeek("本周"),
    Earlier("更早")
}

/** 按本地日期把记录分桶；按 lastPlayedAt 倒序（已在 ViewModel 排序） */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenPlayer: (BookRecord) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
) {
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val buckets = remember(history) { bucketize(history) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("历史") },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) { Text("清空") }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有收听记录", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(8.dp)) {
                buckets.forEachIndexed { idx, (bucket, list) ->
                    item("bucket-${bucket.name}-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                bucket.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${list.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    items(list, key = { "${bucket.name}-${it.id}" }) { record ->
                        ListItemRow(record = record, onClick = { onOpenPlayer(record) })
                    }
                    if (idx != buckets.lastIndex) {
                        item("bucket-${bucket.name}-divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
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
}