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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.download.DownloadManager
import com.tingbili.app.player.PartItem
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.FormatUtil
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit = {},
    onOpenAuthor: (Long, String, String) -> Unit = { _, _, _ -> },
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
) {
    LaunchedEffect(Unit) { viewModel.bind() }
    DisposableEffect(Unit) { onDispose { viewModel.saveProgress() } }
    val s by viewModel.state.collectAsState()
    val record = s.record
    if (record == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = (context.applicationContext as com.tingbili.app.BiliTingApplication).settingsStore
    val downloadManager = (context.applicationContext as BiliTingApplication).container.downloadManager
    val scope = rememberCoroutineScope()
    val dlTasks by downloadManager.tasks.collectAsState()
    val dlKey = when {
        record.auid != null && record.auid != 0L -> downloadManager.keyOf(record.id, null, null, record.auid)
        else -> downloadManager.keyOf(record.id, record.bvid, record.currentCid, null)
    }
    val dlDone = downloadManager.isDownloaded(dlKey)
    val dlState = dlTasks[dlKey]
    val dlRunningPct = (dlState as? DownloadManager.TaskState.Running)?.let {
        if ((it.total ?: 0) > 0) (it.bytes * 100 / it.total!!) else null
    }

    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showParts by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val partsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fav = s.record?.isFavorite == true

    CoverColorBackground(
        record = record,
        settings = settingsStore
    ) { palette, contentColor ->
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = contentColor
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) MaterialTheme.colorScheme.primary
                               else contentColor
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "播放设置",
                        tint = contentColor
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 36.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(260.dp)
                ) {
                    val coverUrl = CoverUtil.normalize(record.cover)
                    var coverFailed by remember { mutableStateOf(false) }
                    if (coverUrl.isNotBlank() && !coverFailed) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = record.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                if (state is coil.compose.AsyncImagePainter.State.Error) coverFailed = true
                            }
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
                                )
                        ) {
                            Text(
                                record.title.ifBlank { "听书" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                val totalTxt = if (record.totalParts > 0) "第 ${record.currentPart}/${record.totalParts} 集" else null
                val ownerTxt = record.owner
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ownerTxt.isNotBlank()) {
                        Text(
                            ownerTxt,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (record.ownerMid > 0) MaterialTheme.colorScheme.primary
                                    else contentColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (record.ownerMid > 0) Modifier.clickable {
                                onOpenAuthor(record.ownerMid, ownerTxt, record.ownerAvatar)
                            } else Modifier
                        )
                    }
                    if (totalTxt != null) {
                        if (ownerTxt.isNotBlank()) Text(
                            " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.75f)
                        )
                        Text(
                            totalTxt,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            var draggingValue by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = draggingValue ?: s.positionMs.coerceIn(0, s.durationMs.coerceAtLeast(1)).toFloat(),
                onValueChange = { draggingValue = it },
                onValueChangeFinished = {
                    draggingValue?.let { viewModel.seekTo(it.toLong()) }
                    draggingValue = null
                },
                valueRange = 0f..s.durationMs.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(FormatUtil.progress(s.positionMs), style = MaterialTheme.typography.bodySmall, color = contentColor)
                Text(FormatUtil.progress(s.durationMs), style = MaterialTheme.typography.bodySmall, color = contentColor)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::prevPart) {
                    Text("⏮", style = MaterialTheme.typography.titleLarge, color = contentColor)
                }
                Spacer(Modifier.width(24.dp))
                FilledIconButton(onClick = viewModel::toggle, modifier = Modifier.size(72.dp)) {
                    Text(
                        if (s.isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Spacer(Modifier.width(24.dp))
                IconButton(onClick = viewModel::nextPart) {
                    Text("⏭", style = MaterialTheme.typography.titleLarge, color = contentColor)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubAction(icon = "${s.speed}x", label = "倍速", tint = contentColor) { showSpeed = true }
                SubAction(
                    icon = if (s.sleepRemainSec > 0) "${(s.sleepRemainSec / 60) + 1}min" else "定时",
                    label = if (s.sleepRemainSec > 0) "剩余" else "定时",
                    tint = contentColor
                ) { showSleep = true }
                SubAction(
                    icon = when {
                        dlDone -> "✓"
                        dlRunningPct != null -> "$dlRunningPct%"
                        dlState is DownloadManager.TaskState.Failed -> "重试"
                        else -> "下载"
                    },
                    label = when {
                        dlDone -> "已缓存"
                        dlRunningPct != null -> "下载中"
                        dlState is DownloadManager.TaskState.Failed -> "失败"
                        else -> "离线"
                    },
                    highlight = dlDone || dlRunningPct != null,
                    tint = contentColor
                ) {
                    if (!dlDone && dlRunningPct == null) {
                        val partTitle = s.queue.getOrNull(s.queueIndex)?.part ?: ""
                        scope.launch {
                            downloadManager.download(
                                recordId = record.id,
                                bookTitle = record.title,
                                cover = record.cover,
                                bvid = record.bvid,
                                cid = record.currentCid,
                                auid = record.auid,
                                partTitle = partTitle
                            )
                        }
                    }
                }
                SubAction(
                    icon = if (s.queue.isNotEmpty()) "${s.queue.size}集" else "单集",
                    label = "选集",
                    highlight = s.queue.isNotEmpty(),
                    tint = contentColor
                ) { showParts = true }
            }
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(
            settings = settingsStore,
            currentSpeed = s.speed,
            onSpeedChange = { viewModel.setSpeed(it) },
            onDismiss = { showSettings = false }
        )
    }

    if (showSpeed) {
        var sliderPos by remember(s.speed) { mutableFloatStateOf(s.speed.coerceIn(0.5f, 3f)) }
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("播放倍速") },
            text = {
                Column {
                    Text(
                        "%.2fx".format(sliderPos),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it; viewModel.setSpeed(it) },
                        valueRange = 0.5f..3.0f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    val presets = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presets.forEach { v ->
                            TextButton(onClick = { sliderPos = v; viewModel.setSpeed(v) }) {
                                Text("${v}x")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("关闭") } }
        )
    }

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
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.startSleepEndOfTrack(); showSleep = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "听完本集停止",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (s.sleepRemainSec > 0 || s.sleepEndOfTrack) {
                        TextButton(
                            onClick = { viewModel.stopSleep(); showSleep = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "取消定时",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleep = false }) { Text("关闭") } }
        )
    }

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
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SubAction(icon: String, label: String, highlight: Boolean = false, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Text(
            icon,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else tint
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = tint.copy(alpha = 0.75f)
        )
    }
}