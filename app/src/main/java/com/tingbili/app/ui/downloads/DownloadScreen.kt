package com.tingbili.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.download.DownloadManager
import com.tingbili.app.util.CoverUtil
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as BiliTingApplication
    val mgr = app.container.downloadManager
    val launcher = app.container.playerLauncher
    val scope = rememberCoroutineScope()
    val items by mgr.index.collectAsState()
    val tasks by mgr.tasks.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空全部")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有下载内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("在播放页点\"下载\"，可离线收听", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            val grouped: List<Map.Entry<String, List<DownloadManager.Item>>> = items.groupBy { it.bookId }.entries.toList()
            val totalBytes = items.sumOf { it.sizeBytes }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        "已下载 ${items.size} 集 · ${formatSize(totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                for ((_, list) in grouped) {
                    item {
                        BookHeader(list.first())
                    }
                    item {
                        itemsList(list, mgr, tasks, onPlay = { item ->
                            scope.launch {
                                launcher.playLocalFile(item, mgr.localFile(item))
                                onOpenPlayer()
                            }
                        }, onDelete = { mgr.delete(it.key) })
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空全部下载？") },
                text = { Text("将删除 ${items.size} 集本地文件，回收 ${formatSize(items.sumOf { it.sizeBytes })} 空间") },
                confirmButton = {
                    TextButton(onClick = {
                        mgr.deleteAll()
                        showClearConfirm = false
                    }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun BookHeader(item: DownloadManager.Item) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val coverUrl = CoverUtil.normalize(item.cover)
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            item.bookTitle.ifBlank { "离线内容" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun itemsList(
    list: List<DownloadManager.Item>,
    mgr: DownloadManager,
    tasks: Map<String, DownloadManager.TaskState>,
    onPlay: (DownloadManager.Item) -> Unit,
    onDelete: (DownloadManager.Item) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column {
            list.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPlay(item) },
                            onLongClick = { onDelete(item) }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.partTitle.ifBlank { "第 ${item.key}" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatSize(item.sizeBytes)} · ${timestamp(item.downloadedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

private fun formatSize(b: Long): String {
    if (b <= 0) return "0 B"
    val kb = b / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%.0f KB", kb)
}

private fun timestamp(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return fmt.format(java.util.Date(ms))
}