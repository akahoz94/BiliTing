package com.tingbili.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tingbili.app.player.PartItem
import com.tingbili.app.util.FormatUtil

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)) {
    LaunchedEffect(Unit) { viewModel.bind() }
    DisposableEffect(Unit) { onDispose { viewModel.saveProgress() } }
    val s by viewModel.state.collectAsState()
    val record = s.record
    if (record == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有播放内容", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showParts by remember { mutableStateOf(false) }
    val partsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 顶部到封面的柔和渐变背景，淡化白屏感
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ===== 顶部：标题 + 收藏 =====
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        record.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (record.totalParts > 0) {
                        Text(
                            "第 ${record.currentPart} 集 / 共 ${record.totalParts} 集 · ${record.owner}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (record.owner.isNotBlank()) {
                        Text(
                            record.owner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val fav = s.record?.isFavorite == true
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 中央：封面 =====
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(240.dp)
                ) {
                    if (record.cover.isNotBlank()) {
                        AsyncImage(
                            model = record.cover,
                            contentDescription = record.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                record.title.take(2).ifBlank { "听" },
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ===== 底部：进度 + 控制 =====
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Slider(
                    value = s.positionMs.coerceIn(0, s.durationMs.coerceAtLeast(1)).toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..s.durationMs.coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(FormatUtil.progress(s.positionMs), style = MaterialTheme.typography.bodySmall)
                    Text(FormatUtil.progress(s.durationMs), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::prevPart) { Text("⏮", style = MaterialTheme.typography.titleLarge) }
                    FilledIconButton(onClick = viewModel::toggle, modifier = Modifier.size(72.dp)) {
                        Text(if (s.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = viewModel::nextPart) { Text("⏭", style = MaterialTheme.typography.titleLarge) }
                }
                Spacer(Modifier.height(4.dp))
                // 倍速 / 定时 / 选集（无评论）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubAction(icon = "${s.speed}x", label = "倍速") { showSpeed = true }
                    SubAction(
                        icon = if (s.sleepRemainSec > 0) "${(s.sleepRemainSec / 60) + 1}min" else "定时",
                        label = if (s.sleepRemainSec > 0) "剩余" else "定时"
                    ) { showSleep = true }
                    SubAction(
                        icon = if (s.queue.isNotEmpty()) "${s.queue.size}集" else "单集",
                        label = "选集",
                        highlight = s.queue.isNotEmpty()
                    ) { showParts = true }
                }
            }
        }
    }

    // 倍速弹窗
    if (showSpeed) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("播放倍速") },
            text = {
                Column {
                    speeds.forEach { v ->
                        val selected = v == s.speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSpeed(v); showSpeed = false }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${v}x",
                                style = if (selected) MaterialTheme.typography.titleMedium
                                        else MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) {
                                Text("当前", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("关闭") } }
        )
    }

    // 定时弹窗
    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("定时关闭") },
            text = {
                Column {
                    listOf(30, 60, 90, 120).forEach { min ->
                        TextButton(
                            onClick = { viewModel.startSleep(min); showSleep = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$min 分钟", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                    if (s.sleepRemainSec > 0) {
                        TextButton(
                            onClick = { viewModel.stopSleep(); showSleep = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消定时", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleep = false }) { Text("关闭") } }
        )
    }

    // 选集弹窗（ModalBottomSheet，显示完整分 P 列表）
    if (showParts) {
        val parts: List<PartItem> = s.queue
        ModalBottomSheet(
            onDismissRequest = { showParts = false },
            sheetState = partsSheetState
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    "选集 · 共 ${parts.size} 集",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                if (parts.isEmpty()) {
                    Text(
                        "该内容为单集，无需选集",
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                        itemsIndexed(parts, key = { _, p -> "${p.bvid}:${p.cid}" }) { index, part ->
                            val selected = index == s.queueIndex
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.jumpToPart(index)
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
}

@Composable
private fun SubAction(icon: String, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Text(
            icon,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}