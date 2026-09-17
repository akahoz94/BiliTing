package com.tingbili.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingbili.app.data.local.SettingsStore

/**
 * v0.9 播放设置抽屉：从播放页右下角 ⚙ 弹出。
 * 接管原本在设置页里的：默认倍速 / 播完停止 / 进度保留 / 睡眠定时。
 *
 * 这些都是"边听边调"的临时偏好，写回 SettingsStore 后下次播放生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    settings: SettingsStore,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val speedDefault by settings.playbackSpeed.collectAsState(initial = 1.0f)
    val sleepMin by settings.sleepMinutes.collectAsState(initial = 30)
    val sleepEnd by settings.sleepEndOfTrack.collectAsState(initial = false)
    val autoNext by settings.autoNextEnabled.collectAsState(initial = false)
    val rememberSpeed by settings.rememberSpeedPerAuthor.collectAsState(initial = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "播放设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            // ============= 倍速 =============
            SheetSection(label = "播放倍速", icon = Icons.Filled.Save) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                        val isOn = kotlin.math.abs(currentSpeed - s) < 0.01f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isOn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { onSpeedChange(s) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${s}x",
                                color = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = kotlin.math.abs(speedDefault - currentSpeed) < 0.01f,
                        onCheckedChange = { on -> if (on) scope.launch { settings.setPlaybackSpeed(currentSpeed) } }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "把当前 ${currentSpeed}x 设为默认倍速",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // ============= 播完本集行为 =============
            SheetSection(label = "播完本集", icon = Icons.Filled.SkipNext) {
                Column {
                    ToggleRow(
                        title = "播完本集自动下一集",
                        subtitle = "末尾自动翻下一P（等同点 ⏭）",
                        checked = autoNext,
                        onCheckedChange = { scope.launch { settings.setAutoNextEnabled(it) } }
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = "播完本集自动停止",
                        subtitle = "末尾即停，配合睡眠定时用",
                        checked = sleepEnd,
                        onCheckedChange = { scope.launch { settings.setSleepEndOfTrack(it) } }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // ============= 睡眠定时 =============
            SheetSection(label = "睡眠定时", icon = Icons.Filled.Bedtime) {
                Column {
                    Text(
                        "倒计时 ${sleepMin} 分钟后自动暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = sleepMin.toFloat(),
                        onValueChange = { scope.launch { settings.setSleepMinutes(it.toInt()) } },
                        valueRange = 5f..180f,
                        steps = 34
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // ============= UP 主粒度记忆 =============
            SheetSection(label = "记忆倍速", icon = Icons.Filled.AccessTime) {
                ToggleRow(
                    title = "按 UP 主记忆倍速",
                    subtitle = "同一 UP 主下次播放用上次倍速",
                    checked = rememberSpeed,
                    onCheckedChange = { scope.launch { settings.setRememberSpeedPerAuthor(it) } }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetSection(
    label: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}