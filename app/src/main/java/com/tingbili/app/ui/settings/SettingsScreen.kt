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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tingbili.app.util.BatteryOptimization

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
    onBack: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenCrashLogs: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val theme by viewModel.themeMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val audioGain by viewModel.audioGain.collectAsState()
    val webdavUrl by viewModel.webdavUrl.collectAsState()
    val webdavUser by viewModel.webdavUser.collectAsState()
    val hasSavedPass by viewModel.hasSavedWebdavPass.collectAsState()
    val cloudDir by viewModel.cloudDir.collectAsState()
    val legacyBackupPass by viewModel.legacyBackupPass.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()
    val immersiveMode by viewModel.immersiveMode.collectAsState()
    val paletteStrength by viewModel.paletteStrength.collectAsState()
    val autoNext by viewModel.autoNextEnabled.collectAsState()
    val autoNextBook by viewModel.autoNextBookEnabled.collectAsState()
    val sleepEndOfTrack by viewModel.sleepEndOfTrack.collectAsState()
    val rememberSpeed by viewModel.rememberSpeedPerAuthor.collectAsState()
    val shakeExtend by viewModel.shakeExtendEnabled.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val coverCacheSize by viewModel.coverCacheSize.collectAsState()
    val playCacheSize by viewModel.playCacheSize.collectAsState()
    val msg by viewModel.msg.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshCacheSizes() }

    var showWebDav by remember { mutableStateOf(false) }
    var showCookie by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    // 后台保活：应用详情页/电池优化白名单的系统状态，进页面读一次，点开时再读一次
    val appCtx = LocalContext.current
    var showKeepAlive by remember { mutableStateOf(false) }
    var showGain by remember { mutableStateOf(false) }
    var ignoringBatteryOpt by remember { mutableStateOf(BatteryOptimization.isIgnoring(appCtx)) }
    val cookieHeader by viewModel.cookieHeader.collectAsState()
    val hasScanBackup by viewModel.hasScanBackup.collectAsState()
    val hasSessdata = cookieHeader.contains("SESSDATA=", ignoreCase = true) ||
        cookieHeader.contains("sessdata=", ignoreCase = true)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                        Text(
                            "0 = 接近极简底色，100 = 完全按封面配色",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                            title = "播完本集自动停止",
                            subtitle = "末尾即停、不续播（每次生效）。与上一项互斥，开它会关掉自动下一集",
                            checked = sleepEndOfTrack,
                            onCheckedChange = viewModel::setSleepEndOfTrack
                        )
                        Divider()
                        SwitchRow(
                            title = "播完本书接听单下一本",
                            subtitle = "连载听到最后一集会自己接下一本；关掉就停在末尾等你点",
                            checked = autoNextBook,
                            onCheckedChange = viewModel::setAutoNextBookEnabled
                        )
                        Divider()
                        SwitchRow(
                            title = "按 UP 主记忆倍速",
                            subtitle = "同一 UP 主下次播放自动用上次倍速",
                            checked = rememberSpeed,
                            onCheckedChange = viewModel::setRememberSpeedPerAuthor
                        )
                        Divider()
                        SwitchRow(
                            title = "摇一摇延长定时",
                            subtitle = "睡眠定时激活时，摇一下手机延长 15 分钟",
                            checked = shakeExtend,
                            onCheckedChange = viewModel::setShakeExtendEnabled
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.VolumeUp,
                            title = "音量增益",
                            subtitle = if (audioGain > 1.005f) {
                                "当前 ×${"%.2f".format(audioGain)}：比原始音轨更响（可能有削波失真）"
                            } else {
                                "原始音量。B 站音轨偏轻时可放大到 300%"
                            },
                            onClick = { showGain = true }
                        )
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Lock,
                            title = "后台保活",
                            subtitle = if (ignoringBatteryOpt) {
                                "已在电池优化白名单，退后台不会被系统掐断"
                            } else {
                                "未设置：息屏或退后台一段时间会被系统暂停播放"
                            },
                            onClick = {
                                ignoringBatteryOpt = BatteryOptimization.isIgnoring(appCtx)
                                showKeepAlive = true
                            }
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
                            icon = Icons.Filled.BugReport,
                            title = "错误日志",
                            subtitle = "上次崩溃堆栈，可复制反馈",
                            onClick = onOpenCrashLogs
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
                            icon = Icons.Filled.PlayCircle,
                            title = "清除播放缓存",
                            subtitle = "当前占用 $playCacheSize（在线收听缓存）",
                            onClick = { viewModel.clearPlayCache() }
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
                            icon = Icons.Filled.QrCode2,
                            title = "扫码登录 B 站",
                            subtitle = "B站 App 扫二维码 · 已有 cookie 的其他字段会保留",
                            onClick = onOpenLogin
                        )
                        if (hasScanBackup) {
                            Divider()
                            NavRow(
                                icon = Icons.Filled.Key,
                                title = "退出扫码登录",
                                subtitle = "恢复扫码前粘贴的 cookie，一个字段不动",
                                onClick = viewModel::undoScanLogin
                            )
                        }
                        Divider()
                        NavRow(
                            icon = Icons.Filled.Cloud,
                            title = if (webdavUrl.isBlank()) "配置 WebDAV 备份" else "修改 WebDAV",
                            subtitle = "云端目录 $cloudDir · 兼容坚果云 / Nextcloud",
                            onClick = { showWebDav = true }
                        )
                        Divider()
                        SwitchRow(
                            title = "自动同步",
                            subtitle = buildString {
                                append("收藏 / 听单 / 进度 / 设置，启动时自动合并")
                                if (lastSyncAt > 0L) append(" · 上次 ${relativeTime(lastSyncAt)}")
                            },
                            checked = autoSyncEnabled,
                            onCheckedChange = viewModel::setAutoSyncEnabled
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
                            subtitle = "v" + com.tingbili.app.BuildConfig.VERSION_NAME + " (build " + com.tingbili.app.BuildConfig.VERSION_CODE + ") · 看更新日志",
                            onClick = { showChangelog = true }
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
            initialCloudDir = cloudDir,
            initialLegacyPass = legacyBackupPass,
            hasSavedPass = hasSavedPass,
            busy = busy,
            onDismiss = { showWebDav = false },
            onSaveUrl = viewModel::setWebdavUrl,
            onSaveUser = viewModel::setWebdavUser,
            onSavePass = viewModel::setWebdavPass,
            onSaveCloudDir = viewModel::setCloudDir,
            onSaveLegacyPass = viewModel::setLegacyBackupPass,
            onBackup = viewModel::webdavBackup,
            onRestore = viewModel::webdavRestore,
            onSync = viewModel::syncNow,
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

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = {
                Text(
                    "更新日志",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif)
                )
            },
            text = {
                Text(
                    CHANGELOG_TEXT.trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) { Text("关闭") }
            }
        )
    }

    if (showGain) {
        AlertDialog(
            onDismissRequest = { showGain = false },
            title = { Text("音量增益") },
            text = {
                Column {
                    Text(
                        if (audioGain > 1.005f) "×${"%.2f".format(audioGain)}（+${((audioGain - 1f) * 100).toInt()}%）"
                        else "×1.00（原始音量）"
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = audioGain,
                        onValueChange = { viewModel.setAudioGain(it) },
                        valueRange = 1.0f..3.0f
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "拖动即时生效。放大的是音频样本本身，超过原始电平会有削波（破音）风险，" +
                            "建议只调到刚好听得清。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showGain = false }) { Text("好") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setAudioGain(1.0f)
                    showGain = false
                }) { Text("恢复原始") }
            }
        )
    }

    if (showKeepAlive) {
        AlertDialog(
            onDismissRequest = { showKeepAlive = false },
            title = { Text("后台保活") },
            text = {
                Text(
                    if (ignoringBatteryOpt) {
                        "已加入电池优化白名单。\n\n" +
                            "如果退后台几分钟后仍会自动停，多半是厂商的省电策略还在拦——" +
                            "在系统设置里给 BiliTing 打开「自启动 / 后台运行 / 允许后台活动」，" +
                            "并把省电策略设为「无限制」。"
                    } else {
                        "系统为了省电，会在息屏或退后台一段时间后冻结 App，连前台播放服务也保不住。\n\n" +
                            "① 点下面按钮把 BiliTing 加入电池优化白名单（系统会弹确认框，选「允许」）\n" +
                            "② 国产 ROM 还差一步：在应用详情里打开「自启动 / 后台运行」，" +
                            "省电策略选「无限制」"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showKeepAlive = false
                    if (!ignoringBatteryOpt) BatteryOptimization.requestIgnore(appCtx)
                    else BatteryOptimization.openAppDetails(appCtx)
                }) {
                    Text(if (ignoringBatteryOpt) "打开应用详情" else "忽略电池优化")
                }
            },
            dismissButton = {
                if (!ignoringBatteryOpt) {
                    TextButton(onClick = {
                        showKeepAlive = false
                        BatteryOptimization.openAppDetails(appCtx)
                    }) { Text("去应用详情") }
                } else {
                    TextButton(onClick = { showKeepAlive = false }) { Text("知道了") }
                }
            }
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
    initialCloudDir: String,
    initialLegacyPass: String = "",
    hasSavedPass: Boolean = false,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onSaveUser: (String) -> Unit,
    onSavePass: (String) -> Unit,
    onSaveCloudDir: (String) -> Unit,
    onSaveLegacyPass: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit,
    onPing: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var cloudDir by remember { mutableStateOf(initialCloudDir) }
    var legacyPass by remember { mutableStateOf(initialLegacyPass) }
    var davPass by remember { mutableStateOf("") }
    var davPassVisible by remember { mutableStateOf(false) }

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
                    value = davPass,
                    onValueChange = {
                        davPass = it
                        // 留空表示"沿用已保存的密码"——不要把空串写回去把旧密码清掉
                        if (it.isNotBlank()) onSavePass(it)
                    },
                    label = { Text(if (hasSavedPass) "WebDAV 密码（已保存，留空则沿用）" else "WebDAV 密码（坚果云应用密码）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (davPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { davPassVisible = !davPassVisible }) {
                            Icon(
                                imageVector = if (davPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (davPassVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cloudDir,
                    onValueChange = { cloudDir = it; onSaveCloudDir(it) },
                    label = { Text("数据备份目录（云端子目录）") },
                    placeholder = { Text("BiliTing") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = legacyPass,
                    onValueChange = { legacyPass = it; onSaveLegacyPass(it) },
                    label = { Text("历史备份密码（可选）") },
                    placeholder = { Text("升级前那份备份用的密码") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    TextButton(onClick = { if (!busy) onBackup() }, enabled = !busy) { Text("备份到云端") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { if (!busy) onRestore() }, enabled = !busy) { Text("从云端恢复") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "WebDAV 密码填坚果云后台→账户→安全→第三方应用管理生成的应用密码（不是登录密码）。" +
                        "云端文件用这个密码加密，所以只需要记这一个密码。" +
                        "配置会自动存一份到手机「下载/BiliTing/」下，重装 App 后不用再输。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            // 主操作 = 立即同步：把云端和本地合并（不覆盖本地独有内容）
            Button(
                onClick = { if (!busy) onSync() },
                enabled = !busy
            ) { Text(if (busy) "处理中..." else "立即同步") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onPing, enabled = !busy) { Text("测试连接") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/** 「上次同步」的人话时间：1 分钟内算刚刚，超过 7 天就说"很久以前" */
private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> "很久以前"
    }
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
