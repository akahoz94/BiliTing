package com.tingbili.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.local.Defaults
import com.tingbili.app.ui.theme.ThemePalettes

/**
 * v0.9 重构后的设置页：
 *   - "外观"分组：主题色 / 深浅模式 / 沉浸式策略 / 取色强度（按用户勾掉的 A5 删掉背景淡出时长）
 *   - "播放"分组只剩全局开关：仅音频 / 自动下一集 / 按 UP 主记忆倍速
 *     （默认倍速、播完停止、进度保留已搬到播放页 ⚙ 抽屉）
 *   - "数据"分组：搜索 / 统计 / WebDAV
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenStats: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val theme by viewModel.themeMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val webdavUrl by viewModel.webdavUrl.collectAsState()
    val webdavUser by viewModel.webdavUser.collectAsState()
    val immersiveMode by viewModel.immersiveMode.collectAsState()
    val paletteStrength by viewModel.paletteStrength.collectAsState()
    val autoNext by viewModel.autoNextEnabled.collectAsState()
    val rememberSpeed by viewModel.rememberSpeedPerAuthor.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val msg by viewModel.msg.collectAsState()

    var showWebDav by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ============= 外观 =============
            item {
                GroupHeader("外观", Icons.Filled.Palette)
                Card {
                    Column {
                        SettingRow(
                            title = "主题色",
                            subtitle = themeColorName(themeColor) + " · 影响按钮 / 胶囊 / Tab"
                        ) {
                            // 紧凑 trailing：6 个小圆点排成一行，文字在上方独占完整宽度
                            ColorSwatches(selected = themeColor, onSelect = viewModel::setThemeColor)
                        }
                        Divider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "深浅模式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            SegmentedRow(
                                options = listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"),
                                selected = theme,
                                onSelect = viewModel::setTheme
                            )
                        }
                        Divider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "播放页沉浸式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            SegmentedRow(
                                options = listOf(0 to "封面取色", 1 to "主题色", 2 to "极简"),
                                selected = immersiveMode,
                                onSelect = viewModel::setImmersiveMode
                            )
                            if (immersiveMode == 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "取色强度 ${paletteStrength}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = paletteStrength.toFloat(),
                                    onValueChange = { viewModel.setPaletteStrength(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 9
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ============= 播放 =============
            item {
                GroupHeader("播放", Icons.Filled.PlayCircle)
                Card {
                    Column {
                        SwitchRow(
                            title = "仅音频模式",
                            subtitle = "关闭视频解码，省流量省电（不影响音质）",
                            checked = audioOnly,
                            onCheckedChange = viewModel::setAudioOnly
                        )
                        Divider()
                        SwitchRow(
                            title = "播完本集自动下一集",
                            subtitle = "开启后到末尾自动翻下一P，无需手动点 ⏭",
                            checked = autoNext,
                            onCheckedChange = viewModel::setAutoNextEnabled
                        )
                        Divider()
                        SwitchRow(
                            title = "按 UP 主记忆倍速",
                            subtitle = "同一 UP 主下次播放自动用上次倍速",
                            checked = rememberSpeed,
                            onCheckedChange = viewModel::setRememberSpeedPerAuthor
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ============= 数据 =============
            item {
                GroupHeader("数据", Icons.Filled.Storage)
                Card {
                    Column {
                        NavRow(
                            icon = Icons.Filled.Tune,
                            title = "听书统计",
                            subtitle = "累计时长 / 本月新增 / TOP 3",
                            onClick = onOpenStats
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Download,
                            title = "下载管理",
                            subtitle = "离线缓存的分集，可播放 / 删除",
                            onClick = onOpenDownloads
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.NightsStay,
                            title = if (webdavUrl.isBlank()) "配置 WebDAV 备份" else "修改 WebDAV",
                            subtitle = "本地 AES-GCM 加密 · 兼容坚果云 / Nextcloud / 自建",
                            onClick = { showWebDav = true }
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showWebDav) {
        WebDavDialog(
            initialUrl = webdavUrl,
            initialUser = webdavUser,
            busy = busy,
            onDismiss = { showWebDav = false },
            onSaveUrl = viewModel::setWebdavUrl,
            onSaveUser = viewModel::setWebdavUser,
            onSavePass = viewModel::setWebdavPass,
            onBackup = viewModel::webdavBackup,
            onRestore = viewModel::webdavRestore,
            onPing = viewModel::webdavPing
        )
    }

    msg?.let { m ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMsg,
            confirmButton = { TextButton(onClick = viewModel::consumeMsg) { Text("好") } },
            title = { Text("提示") },
            text = { Text(m) }
        )
    }
}

// ============== 通用小组件 ==============

@Composable
private fun GroupHeader(label: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) { Column { content() } }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    if (trailing == null) {
        // 无 trailing：保持单 Row 紧凑样式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        // 有 trailing：标题独占第一行，副标题和 trailing 共享第二行（trailing 在右）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun themeColorName(idx: Int) = when (idx) {
    Defaults.COLOR_BLUE -> "蓝"
    Defaults.COLOR_GREEN -> "绿"
    Defaults.COLOR_ORANGE -> "橙"
    Defaults.COLOR_PINK -> "粉"
    Defaults.COLOR_RED -> "红"
    else -> "紫"
}

@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val swatches = listOf(
        Defaults.COLOR_PURPLE to "紫",
        Defaults.COLOR_BLUE to "蓝",
        Defaults.COLOR_GREEN to "绿",
        Defaults.COLOR_ORANGE to "橙",
        Defaults.COLOR_PINK to "粉",
        Defaults.COLOR_RED to "红"
    )
    // 紧凑横向排列：每个圆点 28/32dp + 8dp 间距。6 个 ≈ 6*32 + 5*8 ≈ 232dp，
    // 留给副标题文字 ≈ 80dp（够 4 个字 + 省略号）。不再 fillMaxWidth+SpaceBetween。
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        swatches.forEach { entry ->
            val id: Int = entry.first
            val previewColor = ThemePalettes.schemeOf(id, false).primary
            val isOn = id == selected
            Box(
                Modifier
                    .size(if (isOn) 32.dp else 26.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(
                        width = if (isOn) 2.dp else 1.dp,
                        color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onSelect(id) }
            )
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { (id, label) ->
                val isOn = id == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isOn) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun WebDavDialog(
    initialUrl: String,
    initialUser: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onSaveUser: (String) -> Unit,
    onSavePass: (String) -> Unit,
    onBackup: (String) -> Unit,
    onRestore: (String) -> Unit,
    onPing: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 备份") },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it; onSaveUrl(it) },
                    label = { Text("WebDAV 地址") },
                    placeholder = { Text("https://dav.example.com/BiliTing") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it; onSaveUser(it) },
                    label = { Text("用户名") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it; onSavePass(it) },
                    label = { Text("备份密码（加密用）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "说明：URL 与用户名立即保存到本机；密码以密文保存。备份/恢复时用密码派生 AES-256 密钥，云端只看到密文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!busy) onBackup(pass) },
                enabled = !busy && pass.isNotBlank()
            ) { Text(if (busy) "处理中..." else "备份") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onPing, enabled = !busy) { Text("测试连接") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { if (!busy) onRestore(pass) },
                    enabled = !busy && pass.isNotBlank()
                ) { Text("恢复") }
            }
        }
    )
}