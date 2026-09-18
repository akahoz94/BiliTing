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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.tingbili.app.ui.components.ModeCard
import com.tingbili.app.ui.theme.AppTokens
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.local.Defaults
import com.tingbili.app.ui.theme.ThemePalettes

/**
 * 设置页 —— 第一版设计语言（与听单/搜索/历史统一）：
 *   - 衬线字（Noto Serif SC）
 *   - 28sp Serif Bold AppBar 标题
 *   - 卡片：16dp 圆角 surfaceContainer 卡 + 1dp outlineVariant 边框
 *   - 列表项：16dp 内边距 + 双行（标题 Semibold + 副标题 onSurfaceVariant）
 *   - 主题色：6 个圆点 + onSurface 边框指示激活态
 *   - 段控：12dp 圆角分段，primary 高亮
 *   - 0 emoji
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
    val coverCacheSize by viewModel.coverCacheSize.collectAsState()
    val downloadCacheSize by viewModel.downloadCacheSize.collectAsState()
    val playCacheSize by viewModel.playCacheSize.collectAsState()
    val msg by viewModel.msg.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshCacheSizes() }

    var showWebDav by remember { mutableStateOf(false) }
    var showCookie by remember { mutableStateOf(false) }
    val cookieHeader by viewModel.cookieHeader.collectAsState()
    val hasSessdata = cookieHeader.contains("SESSDATA=", ignoreCase = true) ||
        cookieHeader.contains("sessdata=", ignoreCase = true)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ============ 外观 ============
            item {
                SectionHeader("外观", Icons.Filled.Palette)
                ModeCard(
                    title = "主题色",
                    subtitle = "${themeColorName(themeColor)} · 影响按钮 / 胶囊 / Tab"
                ) {
                    ColorSwatches(selected = themeColor, onSelect = viewModel::setThemeColor)
                }
                Spacer(Modifier.height(AppTokens.Spacing3))
                ModeCard(title = "深浅模式") {
                    SegmentedRow(
                        options = listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"),
                        selected = theme,
                        onSelect = viewModel::setTheme
                    )
                }
                Spacer(Modifier.height(AppTokens.Spacing3))
                ModeCard(title = "播放页沉浸式") {
                    SegmentedRow(
                        options = listOf(0 to "封面取色", 1 to "主题色", 2 to "极简"),
                        selected = immersiveMode,
                        onSelect = viewModel::setImmersiveMode
                    )
                    if (immersiveMode == 0) {
                        Spacer(Modifier.height(AppTokens.Spacing3))
                        Text(
                            "取色强度 ${paletteStrength}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Serif
                            ),
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
                Spacer(Modifier.height(AppTokens.Spacing5))
            }

            // ============ 播放 ============
            item {
                SectionHeader("播放", Icons.Filled.PlayCircle)
                SettingsCard {
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
                            subtitle = "开启后到末尾自动翻下一P，无需手动点",
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

            // ============ 数据 ============
            item {
                SectionHeader("数据", Icons.Filled.Storage)
                SettingsCard {
                    Column {
                        NavRow(
                            icon = Icons.Filled.GraphicEq,
                            title = "听书统计",
                            subtitle = "累计时长 / 本月新增 / 收藏概览",
                            onClick = onOpenStats
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Image,
                            title = "下载管理",
                            subtitle = "离线缓存的分集，可播放 / 删除",
                            onClick = onOpenDownloads
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Brush,
                            title = "清除封面缓存",
                            subtitle = "当前占用 $coverCacheSize",
                            onClick = { viewModel.clearCoverCache() }
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.FolderDelete,
                            title = "清除下载音频",
                            subtitle = "当前占用 $downloadCacheSize",
                            onClick = { viewModel.clearDownloadCache() }
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Key,
                            title = if (hasSessdata) "修改 B 站登录 cookie" else "粘贴 B 站登录 cookie",
                            subtitle = if (hasSessdata)
                                "已登录 · SESSDATA 已保存"
                            else
                                "未登录 · 必填才能稳定下载 / 播放高画质",
                            onClick = { showCookie = true }
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Cloud,
                            title = if (webdavUrl.isBlank()) "配置 WebDAV 备份" else "修改 WebDAV",
                            subtitle = "本地 AES-GCM 加密 · 兼容坚果云 / Nextcloud",
                            onClick = { showWebDav = true }
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
            item {
                SectionHeader("关于", Icons.Filled.Info)
                SettingsCard {
                    Column {
                        val ctx = LocalContext.current
                        NavRow(
                            icon = Icons.Filled.Info,
                            title = "BiliTing",
                            subtitle = "v" + com.tingbili.app.BuildConfig.VERSION_NAME + " (build " + com.tingbili.app.BuildConfig.VERSION_CODE + ")",
                            onClick = {}
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Folder,
                            title = "GitHub 仓库",
                            subtitle = "https://github.com/akahoz94/BiliTing",
                            onClick = {
                                runCatching {
                                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("https://github.com/akahoz94/BiliTing")
                                    })
                                }
                            }
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Folder,
                            title = "检查更新 / Releases",
                            subtitle = "查看最新版本和下载 APK",
                            onClick = {
                                runCatching {
                                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("https://github.com/akahoz94/BiliTing/releases")
                                    })
                                }
                            }
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

    if (showCookie) {
        CookieDialog(
            initial = if (cookieHeader.isBlank()) viewModel.anonymousCookie else cookieHeader,
            busy = busy,
            onDismiss = { showCookie = false },
            onSave = viewModel::setCookie,
            onClear = viewModel::clearCookie
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

// ============== 通用小组件（设计语言统一） ==============

@Composable
private fun SectionHeader(label: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) { Column { content() } }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                ),
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
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEach { entry ->
            val id: Int = entry.first
            val previewColor = ThemePalettes.schemeOf(id, false).primary
            val isOn = id == selected
            Box(
                Modifier
                    .size(if (isOn) 36.dp else 30.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(
                        width = if (isOn) 2.5.dp else 1.dp,
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif
                        ),
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
    var davPass by remember { mutableStateOf("") }
    var encPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "WebDAV 设置",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it; onSaveUrl(it) },
                    label = { Text("WebDAV 地址") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it; onSaveUser(it) },
                    label = { Text("用户名（邮箱）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = davPass, onValueChange = { davPass = it; onSavePass(it) },
                    label = { Text("WebDAV 密码（坚果云应用密码）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = encPass, onValueChange = { encPass = it },
                    label = { Text("备份加密密码（自己设一个）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "WebDAV 密码填坚果云后台→账户→安全→第三方应用管理生成的应用密码（不是登录密码）。备份加密密码自己设一个，用于加密云端数据。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!busy) onBackup(encPass) },
                enabled = !busy && encPass.isNotBlank() && davPass.isNotBlank()
            ) { Text(if (busy) "处理中..." else "备份") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onPing, enabled = !busy) { Text("测试连接") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { if (!busy) onRestore(encPass) },
                    enabled = !busy && encPass.isNotBlank() && davPass.isNotBlank()
                ) { Text("恢复") }
            }
        }
    )
}

/**
 * 粘贴 B 站登录 cookie 的对话框：
 *  - 多行输入框，直接粘整段 cookie
 *  - 提供"清空"按钮退回匿名
 *  - 仅做"是否含 SESSDATA"的轻校验，避免误存空白
 */
@Composable
private fun CookieDialog(
    initial: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val hasSessdata = text.contains("SESSDATA=", ignoreCase = true) ||
        text.contains("sessdata=", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "B 站登录 cookie",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "从浏览器打开 bilibili.com → F12 → Network → 任一请求 → Cookie，复制整段粘贴到下面。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("cookie 字符串") },
                    placeholder = { Text("SESSDATA=xxxx; bili_jct=xxxx; ...") },
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasSessdata) "已识别到 SESSDATA" else "未检测到 SESSDATA，保存后下载仍可能受限",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = if (hasSessdata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!busy) onSave(text) },
                enabled = !busy && text.isNotBlank()
            ) { Text(if (busy) "保存中..." else "保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear, enabled = !busy) { Text("清空") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}
