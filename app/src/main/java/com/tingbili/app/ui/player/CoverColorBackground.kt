package com.tingbili.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.lerp
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
 * 沉浸式播放页背景 — 封面取色。
 *
 * 实现要点（避免之前出现的"封面顶部被截断/丑"问题）：
 *   ✅ 背景层只放"封面取出的纯色调"，不放封面图片本身
 *      → 封面不会被自己模糊版截断，背景永远干净
 *   ✅ 背景 = palette.dominant 的色阶 → 上下渐变
 *      → 类似主题色沉浸（mode=1）的渐变，但颜色取自封面而不是 ColorScheme
 *   ✅ 封面图作为前景元素居中悬浮，不被背景覆盖
 *   ✅ 所有 UI 控件文字色 = 按背景 luminance 自适应（白/黑）
 *
 * 沉浸模式：
 *   - 0 封面取色：本文件实现（封面色调背景）
 *   - 1 主题色：ColorScheme.primary 渐变背景（不变）
 *   - 2 极简：纯 surface 底色（不变）
 *
 * ⚠️ 2026-09-19 修复：设置里的「取色强度」滑条此前**没有任何代码读它**，
 *    拖到 0 或 100 背景都一模一样（用户反馈"选多少都一样"）。
 *    现在强度真的参与运算：见下方 [strength] —— 它是封面色阶与 surface 底色之间的插值系数。
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
    val paletteStrength by settings.paletteStrength.collectAsState(initial = 60)
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

    // ── 取色强度 ──
    // 0   = 完全退回 surface 底色（观感等同「极简」）
    // 100 = 完整的封面色阶（原行为）
    // 默认 60 = 明显能看出封面配色，又不会太艳。
    // 两端的"满强度"端点色与 mode=1（主题色）保持同一套明暗关系，只是把 primary 换成封面主色。
    val strength = paletteStrength.coerceIn(0, 100) / 100f
    val baseTone: Color = palette?.dominant?.let { Color(it) } ?: cs.primary
    val toneTop: Color = lerp(
        cs.surface,
        if (isDark) baseTone.darken(0.25f) else baseTone.lighten(0.55f),
        strength
    )
    val toneBottom: Color = lerp(
        cs.surface,
        if (isDark) baseTone.darken(0.75f) else baseTone.darken(0.10f),
        strength
    )

    // 文字色：背景是封面取色时，按**实际算出来的背景色**自适应（亮底 → 黑字，暗底 → 白字）。
    // 用混色后的 toneTop 而不是 raw dominant —— 强度调低时背景已经接近 surface，
    // 再按 raw dominant 判色会出现"浅灰底配白字"这种看不清的组合。
    val contentColor: Color = when {
        immersiveMode == 0 && palette != null -> {
            val bgL = if (isDark) toneTop.luminance() * 0.5f else toneTop.luminance()
            if (bgL < 0.5f) Color.White else Color(0xFF1C1B1F)
        }
        else -> cs.onSurface
    }

    Box(modifier = Modifier.fillMaxSize().background(cs.surface)) {
        when (immersiveMode) {
            0 -> CoverToneLayer(toneTop, toneBottom)
            1 -> PrimaryToneLayer(cs.primary, isDark)
            else -> { /* 极简：仅 surface 底色，由外层 Box 提供 */ }
        }
        content(palette, contentColor)
    }
}

/**
 * 封面取色调背景 — 单层纯色调（不放封面图）。
 *
 * 端点色由调用方按「取色强度」插值算好，这里只负责画渐变。
 *
 * 视觉对比 mode=1（主题色沉浸）：
 *   mode=1: primary 色阶
 *   mode=0: 封面 dominant 色阶 —— 两者结构一致，颜色不同
 */
@Composable
private fun CoverToneLayer(top: Color, bottom: Color) {
    Box(
        modifier = Modifier
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
