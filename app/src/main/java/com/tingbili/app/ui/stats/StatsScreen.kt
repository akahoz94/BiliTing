package com.tingbili.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val s by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            // ===== 月历 + 时长热力 =====
            item {
                SectionTitle("收听日历")
                Spacer(Modifier.height(8.dp))
                MonthHeader(
                    year = s.year, month = s.month,
                    totalMin = s.monthTotalMs,
                    onPrev = viewModel::prevMonth,
                    onNext = viewModel::nextMonth
                )
                Spacer(Modifier.height(8.dp))
                ListeningCalendar(s)
                Spacer(Modifier.height(20.dp))
            }

            item {
                SectionTitle("总览")
                Spacer(Modifier.height(8.dp))
                StatCard("累计收听时长", formatDuration(s.totalMs), Icons.Filled.Headphones)
                Spacer(Modifier.height(10.dp))
                StatCard("本月新增集数", "${s.monthlyCount}", Icons.Filled.PlayArrow)
                Spacer(Modifier.height(10.dp))
                StatCard("收藏条目数", "${s.totalCount}", Icons.Filled.Star)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MonthHeader(year: Int, month: Int, totalMin: Long, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$year 年 $month 月", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (totalMin > 0) "收听 ${formatMin(totalMin)}" else "本月暂无收听",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月") }
    }
}

@Composable
private fun ListeningCalendar(s: StatsViewModel.UiState) {
    val week = listOf("一", "二", "三", "四", "五", "六", "日")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                week.forEach { w ->
                    Text(
                        w,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val cells = s.daysInMonth + s.firstDayOfWeek
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - s.firstDayOfWeek + 1
                        Box(Modifier.weight(1f).padding(2.dp)) {
                            if (day in 1..s.daysInMonth) {
                                DayCell(day = day, ms = s.dailyMs[day] ?: 0L, maxMs = s.maxDayMs)
                            } else {
                                Spacer(Modifier.size(1.dp).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, ms: Long, maxMs: Long) {
    val intensity = if (maxMs > 0 && ms > 0) (ms.toDouble() / maxMs.toDouble()).coerceIn(0.12, 1.0) else 0.0
    val fill = if (intensity > 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = intensity.toFloat())
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        Modifier.aspectRatio(1f).background(fill, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (ms > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (intensity > 0.45) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMin(ms: Long): String {
    val m = (ms / 60_000).toLong()
    return if (m >= 60) {
        val h = m / 60
        val mm = m % 60
        if (mm > 0) "${h} 小时 ${mm} 分钟" else "${h} 小时"
    } else "${m} 分钟"
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0 分钟"
    return formatMin(ms)
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}