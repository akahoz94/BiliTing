package com.tingbili.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.util.FormatUtil

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

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(record.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (record.totalParts > 0) {
                    Text("第 ${record.currentPart} 集 / 共 ${record.totalParts} 集",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            val fav = s.record?.isFavorite == true
            IconButton(onClick = viewModel::toggleFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    imageVector = if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (fav) "取消收藏" else "收藏",
                    tint = if (fav) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = s.positionMs.coerceIn(0, s.durationMs.coerceAtLeast(1)).toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..s.durationMs.coerceAtLeast(1).toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(FormatUtil.progress(s.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(FormatUtil.progress(s.durationMs), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showSpeed = true }) { Text("${s.speed}x") }
                IconButton(onClick = viewModel::prevPart) { Text("⏮", style = MaterialTheme.typography.titleLarge) }
                FilledIconButton(onClick = viewModel::toggle, modifier = Modifier.size(72.dp)) {
                    Text(if (s.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = viewModel::nextPart) { Text("⏭", style = MaterialTheme.typography.titleLarge) }
                TextButton(onClick = { showSleep = true }) {
                    Text(if (s.sleepRemainSec > 0) "${(s.sleepRemainSec / 60) + 1}min" else "定时")
                }
            }
        }
    }

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
}
