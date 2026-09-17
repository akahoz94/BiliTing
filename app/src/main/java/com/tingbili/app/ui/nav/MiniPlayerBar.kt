package com.tingbili.app.ui.nav

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.player.PartItem
import com.tingbili.app.player.PlayerHolder
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.FormatUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 迷你播放条：常驻底部导航上方，显示当前播放内容 + 播放/暂停 + ⏭。
 * 点击整条 → 回播放页；长按整条 → 弹出选集 sheet（仅多集时）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBar(
    holder: PlayerHolder,
    onOpenPlayer: () -> Unit
) {
    val record by holder.record.collectAsState()
    val currentRecord = record ?: return  // 无播放内容时不显示
    val scope = rememberCoroutineScope()
    val partsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showParts by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(holder.player.isPlaying) }
    var queueIdx by remember { mutableStateOf(holder.currentQueueIndex()) }
    var queue by remember { mutableStateOf(holder.currentQueue()) }

    // 用 ExoPlayer.Listener 替代每 500ms 忙等轮询：播放状态/队列变化时由播放器回调
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                queueIdx = holder.currentQueueIndex()
                queue = holder.currentQueue()
            }
        }
        holder.player.addListener(listener)
        onDispose { holder.player.removeListener(listener) }
    }

    val launcher = BiliTingApplication.get()?.container?.playerLauncher

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenPlayer,
                onLongClick = { if (queue.isNotEmpty()) showParts = true }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val cover = currentRecord.cover
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = CoverUtil.normalize(cover),
                        contentDescription = currentRecord.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Text(
                        currentRecord.title.take(1).ifBlank { "·" },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    currentRecord.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentRecord.totalParts > 0) {
                    Text(
                        "第 ${currentRecord.currentPart} 集 / ${currentRecord.totalParts}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            // 上一集 | 播放/暂停 | 下一集 —— 居中播放，规范排布
            IconButton(
                onClick = { scope.launch { launcher?.prevPart() } },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一集", tint = MaterialTheme.colorScheme.onSurface)
            }
            FilledIconButton(
                onClick = { holder.togglePlay() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(
                onClick = { scope.launch { launcher?.nextPart() } },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一集", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    if (showParts && launcher != null) {
        ModalBottomSheet(
            onDismissRequest = { showParts = false },
            sheetState = partsSheetState
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    "选集 · 共 ${queue.size} 集",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    itemsIndexed(queue, key = { _, p -> "${p.bvid}:${p.cid}" }) { index, part ->
                        val selected = index == queueIdx
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        holder.moveQueueTo(index)
                                        launcher.playRecord(currentRecord)
                                    }
                                    showParts = false
                                }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (part.part.isBlank()) "第 ${index + 1} 集" else part.part,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (part.duration > 0) {
                                    Text(
                                        FormatUtil.progress(part.duration * 1000L),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (selected) {
                                Text(
                                    "播放中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}