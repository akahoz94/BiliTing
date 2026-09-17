package com.tingbili.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 跨 Screen 共享的设计 token（间距、圆角、字号）。
 * 颜色一律走 MaterialTheme.colorScheme，主题色变化时所有 Composable 自动跟随。
 *
 * 设计参考 prototype 那套统一设计语言：
 *  - 卡片圆角：24dp（hero / list-row）
 *  - 内部模块：20dp（mode-card / chip-group）
 *  - chip / 按钮：12dp
 *  - cover：16dp（与 hero 区分）
 *  - 列表行内间距：12dp
 *  - 模块间大间距：20dp
 */
object AppTokens {
    // ===== 形状（圆角）=====
    val RadiusHero     = 24.dp  // 听单 / 历史 list-row / 大卡片
    val RadiusModule   = 20.dp  // mode-card / 搜索结果卡
    val RadiusCover    = 16.dp  // 封面缩略图
    val RadiusChip     = 12.dp  // chip / 段控件
    val RadiusMini     = 10.dp  // mini player / 小控件

    // 预制形状（直接喂 Modifier.clip() / MaterialTheme.shapes）
    val ShapeHero      = RoundedCornerShape(RadiusHero)
    val ShapeModule    = RoundedCornerShape(RadiusModule)
    val ShapeCover     = RoundedCornerShape(RadiusCover)
    val ShapeChip      = RoundedCornerShape(RadiusChip)

    // ===== 间距 =====
    val Spacing1       = 4.dp
    val Spacing2       = 8.dp
    val Spacing3       = 12.dp   // list-row 内间距
    val Spacing4       = 16.dp   // 卡片内 padding
    val Spacing5       = 20.dp   // 模块间
    val Spacing6       = 24.dp   // 大模块间
    val Spacing8       = 32.dp

    // ===== 进度条 / 分割线 =====
    val ProgressTrack     = 4.dp
    val ProgressTrackHero = 6.dp
}