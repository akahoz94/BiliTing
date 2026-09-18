package com.tingbili.app.ui.nav

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.player.PartItem
import com.tingbili.app.player.PlayerHolder
import com.tingbili.app.player.PlayerLauncher
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.FormatUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 迷你播放条占位态：常驻底部，没有任何播放内容时显示。
 * 整条与右侧按钮都跳听单，避免条一会儿有一会儿没有造成底栏跳动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniPlayerPlaceholder(onClick: () -> Unit) {
    Column {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "还没有在听的内容",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "去听单挑一本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "去听单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 迷你播放条：常驻底部导航上方，显示当前播放内容 + 播放/暂停 + ⏭。
 * 点击整条 → 回播放页；长按整条 → 弹出选集 sheet（仅多集时）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBar(
    holder: PlayerHolder,
    onOpenPlayer: () -> Unit,
    onEmptyClick: () -> Unit = {}
) {
    val record by holder.record.collectAsState()
    val currentRecord = record
    // 常驻：没有播放内容时显示占位态，不再整条消失
    if (currentRecord == null) {
        MiniPlayerPlaceholder(onClick = onEmptyClick)
        return
    }
    val scope = rememberCoroutineScope()
    val partsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showParts by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(holder.player.isPlaying) }
    var queueIdx by remember { mutableStateOf(holder.currentQueueIndex()) }
    var queue by remember { mutableStateOf(holder.currentQueue()) }
    var isLoading by remember { mutableStateOf(false) }

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

    // 冷启动后播放器是空的（只有展示用 record）：先唤醒装载，再执行控制动作
    fun control(action: suspend PlayerLauncher.() -> Unit) {
        scope.launch {
            val l = launcher ?: return@launch
            if (holder.hasMedia()) l.action() else l.resumeCurrent()
        }
    }

    Column {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
        // 上滑展开大播放页的抓手（QQ 音乐 / 网易云同款视觉提示）
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            )
        }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            // 上滑 → 展开成全屏播放页（对齐时只认纵向滑动，不影响内部按钮点击）
            .pointerInput(Unit) {
                var totalDy = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDy = 0f },
                    onVerticalDrag = { _, dy -> totalDy += dy },
                    onDragEnd = { if (totalDy < -40.dp.toPx()) onOpenPlayer() }
                )
            }
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
                onClick = { control { prevPart() } },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一集", tint = MaterialTheme.colorScheme.onSurface)
            }
            FilledIconButton(
                onClick = {
                    if (holder.hasMedia()) holder.togglePlay()
                    else {
                        isLoading = true
                        scope.launch { launcher?.resumeCurrent(); isLoading = false }
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            IconButton(
                onClick = { control { nextPart() } },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一集", tint = MaterialTheme.colorScheme.onSurface)
            }
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