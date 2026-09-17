package com.tingbili.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tingbili.app.ui.theme.AppTokens

/**
 * 通用"段控 chip"：用于主题色 / 速度 / 沉浸模式等单选场景。
 * 选中态走 primaryContainer + onPrimaryContainer；未选走 surface + outline 描边。
 *
 * 选 chip 用 onClick=true 时的回调；样式统一由 MaterialTheme.colorScheme 决定。
 */
@Composable
fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppTokens.ShapeChip,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

/**
 * 横排段控件：自动均分宽度，避免手动算 width。
 */
@Composable
fun <T> SegmentRow(
    options: List<T>,
    labelOf: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = AppTokens.Spacing2
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        options.forEach { opt ->
            SegmentChip(
                label = labelOf(opt),
                selected = isSelected(opt),
                onClick = { onSelect(opt) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 横滑 chip 列表：用于关键词、tag 等。
 */
@Composable
fun ChipFlow(
    items: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = AppTokens.Spacing2
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(horizontal = AppTokens.Spacing4)
    ) {
        items(items) { tag ->
            SegmentChip(
                label = tag,
                selected = tag in selected,
                onClick = { onToggle(tag) }
            )
        }
    }
}

/**
 * ModeCard：用于"默认倍速"、"沉浸模式"等成组选择（多个选项独占一行）。
 * 整卡是 surface + outlineVariant 描边 + 24dp 圆角；内部用 SegmentRow 横排。
 */
@Composable
fun ModeCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTokens.ShapeHero)
            .background(MaterialTheme.colorScheme.surface)
            .padding(AppTokens.Spacing4)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(AppTokens.Spacing1))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(AppTokens.Spacing3))
        content()
    }
}

/**
 * NavRow：设置页的统一行样式 —— 左侧 icon、title+subtitle（垂直两行），右侧 chevron。
 * 选中态由外部传入高亮（isSelected=true 时 title 走 onPrimaryContainer）。
 */
@Composable
fun NavRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTokens.ShapeHero)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.width(AppTokens.Spacing3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            // 默认右侧 chevron 留空，让调用方自己实现
            Spacer(Modifier.width(AppTokens.Spacing1))
        }
    }
}

/**
 * 两行描述 + 进度条的进度行：用于听单/历史卡片底部的"已播放 1/3"。
 */
@Composable
fun ProgressRow(
    playedMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier
) {
    val pct = if (totalMs > 0L) (playedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    val playedFmt = formatHm(playedMs / 1000)
    val totalFmt = formatHm(totalMs / 1000)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = playedFmt,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(AppTokens.Spacing2))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .weight(1f)
                .height(AppTokens.ProgressTrackHero)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.width(AppTokens.Spacing2))
        Text(
            text = totalFmt,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatHm(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}

/** 通用的空状态占位（听单为空、历史为空等） */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppTokens.Spacing6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(AppTokens.Spacing2))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 主色色块（用于 ThemeColor 选色方块） */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = color,
        border = border,
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
    ) {}
}