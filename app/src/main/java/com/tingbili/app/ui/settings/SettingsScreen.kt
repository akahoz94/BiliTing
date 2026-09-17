package com.tingbili.app.ui.settings

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.local.Defaults
import com.tingbili.app.ui.theme.ThemePalettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenStats: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val theme by viewModel.themeMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val keywords by viewModel.keywords.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val sleepMin by viewModel.sleepMinutes.collectAsState()
    val sleepEnd by viewModel.sleepEndOfTrack.collectAsState()
    val webdavUrl by viewModel.webdavUrl.collectAsState()
    val webdavUser by viewModel.webdavUser.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val msg by viewModel.msg.collectAsState()

    var newKw by remember { mutableStateOf("") }
    var showWebDav by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 主题
            item {
                SectionLabel("主题")
                SegmentedRow(
                    options = listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"),
                    selected = theme,
                    onSelect = viewModel::setTheme
                )
                Spacer(Modifier.height(20.dp))
            }

            // 主题色
            item {
                SectionLabel("主题色")
                ColorSwatches(selected = themeColor, onSelect = viewModel::setThemeColor)
                Spacer(Modifier.height(20.dp))
            }

            // 音频模式开关
            item {
                SectionLabel("播放器")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("仅音频模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "关闭视频解码，省流量省电（不影响音质）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = audioOnly, onCheckedChange = viewModel::setAudioOnly)
                }
                Spacer(Modifier.height(20.dp))
            }

            // 睡眠定时器
            item {
                SectionLabel("睡眠定时")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("听完本集自动停止", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "开启后当正在播放的内容播完即自动暂停",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = sleepEnd, onCheckedChange = viewModel::setSleepEndOfTrack)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "默认倒计时：${sleepMin} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(value = sleepMin.toFloat(), onValueChange = { viewModel.setSleepMinutes(it.toInt()) }, valueRange = 5f..180f, steps = 34)
                Spacer(Modifier.height(20.dp))
            }

            // 关键词管理
            item {
                SectionLabel("听书关键词 · 轻点 ✕ 可删除")
                Text(
                    "搜索时只显示标题或描述里命中这些词的结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                ChipFlow(
                    items = keywords.toList(),
                    onRemove = viewModel::removeKeyword
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newKw,
                        onValueChange = { newKw = it },
                        placeholder = { Text("新增关键词") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newKw.isNotBlank()) {
                                viewModel.addKeyword(newKw.trim()); newKw = ""
                            }
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (newKw.isNotBlank()) {
                            viewModel.addKeyword(newKw.trim()); newKw = ""
                        }
                    }) { Text("添加") }
                }
                Spacer(Modifier.height(24.dp))
            }

            // 默认倍速
            item {
                SectionLabel("默认倍速")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "新内容播放时使用此倍速",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${speed}x",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(value = speed, onValueChange = viewModel::setSpeed, valueRange = 0.5f..2.0f, steps = 5)
                Spacer(Modifier.height(20.dp))
            }

            // 统计入口
            item {
                SectionLabel("数据")
                OutlinedButton(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
                    Text("查看统计（累计时长 / 本月新增）")
                }
                Spacer(Modifier.height(16.dp))
            }

            // WebDAV 备份
            item {
                SectionLabel("WebDAV 备份与恢复")
                Text(
                    "支持任意 WebDAV 服务（坚果云 / Nextcloud / 自建），本地 AES-GCM 加密",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showWebDav = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (webdavUrl.isBlank()) "配置 WebDAV" else "修改 WebDAV")
                }
                Spacer(Modifier.height(24.dp))
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
            onRestore = viewModel::webdavRestore
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
    onRestore: (String) -> Unit
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

@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val purple = Defaults.COLOR_PURPLE to ThemePalettes.Purple
    val blue = Defaults.COLOR_BLUE to ThemePalettes.Blue
    val green = Defaults.COLOR_GREEN to ThemePalettes.Green
    val orange = Defaults.COLOR_ORANGE to ThemePalettes.Orange
    val pink = Defaults.COLOR_PINK to ThemePalettes.Pink
    val red = Defaults.COLOR_RED to ThemePalettes.Red
    val swatches = listOf(purple, blue, green, orange, pink, red)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        swatches.forEach { entry ->
            val id: Int = entry.first
            val color: Color = entry.second
            val isOn = id == selected
            Box(
                Modifier
                    .size(if (isOn) 52.dp else 40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isOn) 3.dp else 1.dp,
                        color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onSelect(id) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
    )
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
private fun ChipFlow(items: List<String>, onRemove: (String) -> Unit) {
    val rows = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    var widthEstimate = 0
    items.forEach { kw ->
        val chipWidth = (kw.length * 18 + 56)
        if (widthEstimate + chipWidth > 320 && current.isNotEmpty()) {
            rows.add(current); current = mutableListOf(); widthEstimate = 0
        }
        current.add(kw); widthEstimate += chipWidth
    }
    if (current.isNotEmpty()) rows.add(current)
    if (items.isEmpty()) {
        Text(
            "还没有关键词，添加后会过滤搜索结果",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { kw -> KeywordChip(kw, onRemove) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun KeywordChip(label: String, onRemove: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .clickable { onRemove(label) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除 $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}