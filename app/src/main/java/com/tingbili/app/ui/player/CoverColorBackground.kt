package com.tingbili.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.util.CoverDownloader
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.PaletteExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 沉浸式播放页背景 — 按用户最新反馈重写：
 *   "按主题色的风格去做，只不过去取封面的颜色"
 *
 * 实现要点（避免之前出现的"封面顶部被截断/丑"问题）：
 *   ✅ 背景层只放"封面取出的纯色调"，不放封面图片本身
 *      → 封面不会被自己模糊版截断，背景永远干净
 *   ✅ 背景 = palette.dominant → 上下渐变到 desaturated 暗色/亮色
 *      → 类似主题色沉浸（mode=1）的渐变，但颜色取自封面而不是 ColorScheme
 *   ✅ 封面图作为前景元素居中悬浮，不被背景覆盖
 *   ✅ 所有 UI 控件文字色 = 按背景 luminance 自适应（白/黑）
 *
 * 沉浸模式：
 *   - 0 封面取色：本文件实现（封面色调背景）
 *   - 1 主题色：ColorScheme.primary 渐变背景（不变）
 *   - 2 极简：纯 surface 底色（不变）
 */
@Composable
fun CoverColorBackground(
    record: BookRecord?,
    settings: SettingsStore,
    content: @Composable (palette: PaletteExtractor.CoverPalette?, contentColor: Color) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    // 文字色：背景是封面取色时，按背景 luminance 自适应（亮底 → 黑字，暗底 → 白字）
    val contentColor: Color = when {
        immersiveMode == 0 && palette != null -> {
            val dom = Color(palette!!.dominant)
            // 暗色模式下倾向"暗底白字"，亮色模式下倾向"亮底黑字"
            // 用 dom 的 luminance 决定基础，再按系统主题微调
            val bgL = if (isDark) dom.luminance() * 0.5f else dom.luminance()
            if (bgL < 0.5f) Color.White else Color(0xFF1C1B1F)
        }
        else -> cs.onSurface
    }

    Box(modifier = Modifier.fillMaxSize().background(cs.surface)) {
        when (immersiveMode) {
            0 -> CoverToneLayer(palette, isDark)
            1 -> PrimaryToneLayer(cs.primary, isDark)
            else -> { /* 极简：仅 surface 底色，由外层 Box 提供 */ }
        }
        content(palette, contentColor)
    }
}

/**
 * 封面取色调背景 — 单层纯色调（不放封面图）。
 *
 * 设计：
 *   1. 取 palette.dominant 作为主色
 *   2. 衍生两个端点色：top = dominant → desaturated 更亮 30%（亮模式）/ 更暗 20%（暗模式）
 *                    bottom = dominant → 更暗 50%（亮模式）/ 更暗 60%（暗模式）
 *   3. 上下渐变，呈现"封面色调的氛围"，不出现封面图本身
 *
 * 视觉对比 mode=1（主题色沉浸）：
 *   mode=1: primary 色阶
 *   mode=0: 封面 dominant 色阶 —— 两者结构一致，颜色不同
 */
@Composable
private fun CoverToneLayer(palette: PaletteExtractor.CoverPalette?, isDark: Boolean) {
    val cs = MaterialTheme.colorScheme
    val dom = palette?.dominant?.let { Color(it) } ?: cs.primary
    val top: Color
    val bottom: Color
    if (isDark) {
        // 暗模式：背景压暗让控件文字读得清
        top = dom.copy(alpha = 1f).darken(0.25f)
        bottom = dom.copy(alpha = 1f).darken(0.75f)
    } else {
        // 亮模式：背景保持淡色调，封面取色看起来像"主题色"
        top = dom.copy(alpha = 1f).lighten(0.55f)
        bottom = dom.copy(alpha = 1f).darken(0.10f)
    }
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(top, bottom)))
    )
}

/**
 * mode=1：主题色沉浸（不动）。
 */
@Composable
private fun PrimaryToneLayer(primary: Color, isDark: Boolean) {
    val top = if (isDark) primary.darken(0.25f) else primary.lighten(0.55f)
    val bottom = if (isDark) primary.darken(0.75f) else primary.darken(0.10f)
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(top, bottom)))
    )
}

/** 颜色工具：把 RGB 调暗 factor∈[0,1] */
private fun Color.darken(factor: Float): Color {
    val f = 1f - factor.coerceIn(0f, 1f)
    return Color(
        (red * f).coerceIn(0f, 1f),
        (green * f).coerceIn(0f, 1f),
        (blue * f).coerceIn(0f, 1f),
        alpha
    )
}

/** 颜色工具：把 RGB 朝白色方向偏移 factor∈[0,1] */
private fun Color.lighten(factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return Color(
        red + (1f - red) * f,
        green + (1f - green) * f,
        blue + (1f - blue) * f,
        alpha
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