package com.tingbili.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.tingbili.app.ui.theme.AppTokens
import com.tingbili.app.util.CoverUtil

/**
 * 统一封面组件（4 个 Tab 共用）：
 *   - URL 走 CoverUtil.normalize（处理无协议/相对路径）
 *   - 加载失败时降级为 primary→tertiary 渐变 + 衬线字 · 占位
 *
 * 任务：原 PlaylistScreen.CoverImage → 提到本包，行为保持一致。
 */
@Composable
fun CoverImage(
    url: String,
    size: Dp,
    cornerDp: Dp = AppTokens.RadiusCover
) {
    val shape = RoundedCornerShape(cornerDp)
    val safeUrl = CoverUtil.normalize(url)
    var failed by remember(safeUrl) { mutableStateOf(false) }
    if (safeUrl.isNotBlank() && !failed) {
        AsyncImage(
            model = safeUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) failed = true
            }
        )
    } else {
        CoverPlaceholder(size = size, cornerDp = cornerDp, glyph = "·")
    }
}

/**
 * 纯渐变占位封面（不需要 URL）：用于搜索/历史等"封面未知"场景。
 */
@Composable
fun CoverPlaceholder(
    size: Dp,
    cornerDp: Dp = AppTokens.RadiusCover,
    glyph: String = "·"
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerDp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}