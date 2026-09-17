package com.tingbili.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.util.CoverDownloader
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.PaletteExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 沉浸式播放页背景 — 学习小宇宙 App 的"封面取色"做法。
 *
 * 设计要点（为什么之前看着"糊成一片"）：
 *   ❌ 全屏 blur(16~32dp) → 封面色彩信息被磨平，背景变成均匀灰
 *   ❌ 双层叠加（dominant α=0.35 + 黑 α=0.45）→ 暗色下直接变死黑
 *   ✅ 封面原图 + 极轻 blur(4dp) → 色彩和明暗层次都保留
 *   ✅ 主色径向光斑 → 氛围感来自封面本身的颜色
 *   ✅ 顶部窄条额外模糊 + 微蒙版 → 状态栏附近有"毛玻璃"质感但不糊
 *   ✅ 底部暗脚（不到一半高度）→ 让控件区有足够对比度，但不吞掉封面
 *
 * 沉浸模式：
 *   - 0 封面取色：本文件实现的"小宇宙风"取色
 *   - 1 主题色：ColorScheme.primary 径向光斑
 *   - 2 极简：仅 surface 底色
 */
@Composable
fun CoverColorBackground(
    record: BookRecord?,
    settings: SettingsStore,
    content: @Composable (palette: PaletteExtractor.CoverPalette?, contentColor: Color) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val immersiveMode by settings.immersiveMode.collectAsState(initial = 0)
    val isDark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme

    var palette by remember { mutableStateOf<PaletteExtractor.CoverPalette?>(null) }

    LaunchedEffect(record?.id, record?.cover) {
        if (record == null) {
            palette = null
            return@LaunchedEffect
        }
        val cached = CoverColorBackgroundHolder.peek(record.id)
        if (cached != null) {
            palette = cached
            return@LaunchedEffect
        }
        val coverUrl = CoverUtil.normalize(record.cover)
        if (coverUrl.isBlank()) return@LaunchedEffect
        scope.launch {
            val f = CoverDownloader.ensureLocalFile(context, coverUrl) ?: return@launch
            val p = PaletteExtractor.extract(context, f, coverUrl) ?: return@launch
            CoverColorBackgroundHolder.put(record.id, p)
            palette = p
        }
    }

    // 文字色：封面取色模式下，按 palette dominant luminance 自适应 onDominant；
    // 其余模式直接用 MaterialTheme 的语义色（M3 已保证对比度）。
    val contentColor: Color = when {
        immersiveMode == 0 && palette != null -> {
            val dom = Color(palette!!.dominant)
            if (dom.luminance() < 0.5f) Color.White else Color(0xFF1C1B1F)
        }
        else -> cs.onSurface
    }

    Box(modifier = Modifier.fillMaxSize().background(cs.surface)) {
        when (immersiveMode) {
            0 -> XiaoyuzhouStyleLayer(record, palette, cs, isDark)
            1 -> PrimaryRadialLayer(cs.primary, isDark)
            else -> { /* 极简：仅 surface 底色，由外层 Box 提供 */ }
        }
        content(palette, contentColor)
    }
}

/**
 * 小宇宙式封面取色背景。
 *
 * 分层结构（自下而上）：
 *   L0 surface                —— MaterialTheme.colorScheme.surface 兜底
 *   L1 封面原图（大模糊）      —— 占顶部 60% 高度，原图 + 极轻 blur(4dp)，保留色彩和明暗层次
 *   L2 主色径向                —— palette dominant 0.55→0 alpha 径向光斑
 *   L3 顶部窄条毛玻璃          —— 顶部 28% 额外 blur(14dp) + 0.30 黑蒙版，营造毛玻璃质感
 *   L4 底部暗脚                —— 底部 32% 0→0.50 alpha 黑色垂直渐变，确保控件对比度
 */
@Composable
private fun XiaoyuzhouStyleLayer(
    record: BookRecord?,
    palette: PaletteExtractor.CoverPalette?,
    cs: androidx.compose.material3.ColorScheme,
    isDark: Boolean
) {
    val context = LocalContext.current
    val coverUrl = CoverUtil.normalize(record?.cover.orEmpty())

    if (coverUrl.isNotBlank()) {
        // L1：封面原图，只占顶部 60% 高度 + 极轻模糊
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(4.dp)
            )
        }
    }

    // L2：主色径向光斑
    val dom = palette?.dominant?.let { Color(it) } ?: cs.primary
    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.radialGradient(
                colors = listOf(
                    dom.copy(alpha = if (isDark) 0.55f else 0.45f),
                    dom.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                radius = 1100f
            )
        )
    )

    if (coverUrl.isNotBlank()) {
        // L3：顶部窄条毛玻璃 —— 给"50 人正在听"那一片以额外磨砂感
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.28f)
                .background(
                    if (isDark)
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent))
                    else
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(14.dp)
            )
        }
    }

    // L4：底部暗脚 —— 确保进度条 / 控件对比度，不吞掉封面
    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = if (isDark) 0.55f else 0.18f)
                )
            )
        )
    )
}

/**
 * mode=1：主题色沉浸。
 * 与"封面取色"差异：没有封面图，主色径向覆盖整页，底部稍深保证控件可读。
 */
@Composable
private fun PrimaryRadialLayer(primary: Color, isDark: Boolean) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.55f),
                    primary.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                radius = 1400f
            )
        )
    )
    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = if (isDark) 0.50f else 0.15f)
                )
            )
        )
    )
}

/** 全局缓存：recordId -> CoverPalette。避免每帧重新解码封面。 */
object CoverColorBackgroundHolder {
    private val cache = MutableStateFlow<Map<String, PaletteExtractor.CoverPalette>>(emptyMap())
    fun peek(id: String): PaletteExtractor.CoverPalette? = cache.value[id]
    fun put(id: String, p: PaletteExtractor.CoverPalette) {
        cache.value = cache.value + (id to p)
    }
    fun state(): StateFlow<Map<String, PaletteExtractor.CoverPalette>> = cache
}