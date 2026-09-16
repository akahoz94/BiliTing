package com.tingbili.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
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

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(record.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (record.totalParts > 0) {
                Text("第 ${record.currentPart} 集 / 共 ${record.totalParts} 集",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
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
                TextButton(onClick = { /* 倍速弹窗（Task 10） */ }) { Text("${s.speed}x") }
                IconButton(onClick = viewModel::prevPart) { Text("⏮", style = MaterialTheme.typography.titleLarge) }
                FilledIconButton(onClick = viewModel::toggle, modifier = Modifier.size(72.dp)) {
                    Text(if (s.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = viewModel::nextPart) { Text("⏭", style = MaterialTheme.typography.titleLarge) }
                TextButton(onClick = { /* 定时弹窗（Task 10） */ }) {
                    Text(if (s.sleepRemainSec > 0) "${(s.sleepRemainSec / 60) + 1}min" else "定时")
                }
            }
        }
    }
}
