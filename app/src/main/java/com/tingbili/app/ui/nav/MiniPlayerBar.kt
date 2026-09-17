package com.tingbili.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tingbili.app.player.PlayerHolder
import com.tingbili.app.util.CoverUtil
import kotlinx.coroutines.delay

/**
 * 迷你播放条：常驻底部导航上方，显示当前播放内容 + 播放/暂停。
 * 点击整条 → 回播放页；封面缺失时显示标题文案。
 */
@Composable
fun MiniPlayerBar(
    holder: PlayerHolder,
    onOpenPlayer: () -> Unit
) {
    val record by holder.record.collectAsState()
    if (record == null) return  // 无播放内容时不显示

    var isPlaying by remember { mutableStateOf(holder.player.isPlaying) }
    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = holder.player.isPlaying
            delay(500)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 封面（无则用渐变块 + 标题首字）
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val cover = record!!.cover
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = CoverUtil.normalize(cover),
                        contentDescription = record!!.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Text(
                        record!!.title.take(1).ifBlank { "·" },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 标题 + 副标题
            Column(Modifier.weight(1f)) {
                Text(
                    record!!.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (record!!.totalParts > 0) {
                    Text(
                        "第 ${record!!.currentPart} 集 / ${record!!.totalParts}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 播放/暂停（独立 clickable，避免触发跳转播放页）
            FilledIconButton(
                onClick = { holder.togglePlay() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Text(
                    if (isPlaying) "⏸" else "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}