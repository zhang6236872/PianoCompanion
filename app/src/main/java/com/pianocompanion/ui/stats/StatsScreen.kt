package com.pianocompanion.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import com.pianocompanion.data.model.SessionRecord
import com.pianocompanion.analytics.WeakSpotTrend
import com.pianocompanion.data.model.MatchStatus
import com.pianocompanion.ui.components.EmptyState
import com.pianocompanion.ui.components.GradientStatCard
import com.pianocompanion.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: StatsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StatsViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 练习统计", fontWeight = FontWeight.Bold) },
                actions = {
                    if (uiState.sessions.isNotEmpty()) {
                        IconButton(onClick = {
                            val report = com.pianocompanion.util.ReportExporter.generateReport(uiState.sessions)
                            com.pianocompanion.util.ReportExporter.shareAsText(context, report)
                        }) {
                            Icon(Icons.Filled.Share, "分享报告")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.sessions.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    emoji = "📊",
                    title = "还没有练习记录",
                    subtitle = "完成一次练习后这里会显示你的数据",
                    modifier = Modifier.padding(top = 80.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // === Overview cards ===
            item {
                SectionHeader(title = "总览", icon = Icons.Filled.Insights)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GradientStatCard(
                        icon = "🎵",
                        value = "${uiState.totalSessions}",
                        label = "练习次数",
                        gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                        modifier = Modifier.weight(1f)
                    )
                    GradientStatCard(
                        icon = "⏱️",
                        value = formatDuration(uiState.totalDurationMs),
                        label = "总时长",
                        gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GradientStatCard(
                        icon = "🎯",
                        value = "${(uiState.avgAccuracy * 100).toInt()}%",
                        label = "平均准确率",
                        gradientColors = listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),
                        modifier = Modifier.weight(1f)
                    )
                    GradientStatCard(
                        icon = "🔥",
                        value = "${uiState.streak}天",
                        label = "连续练习",
                        gradientColors = listOf(Color(0xFFFF6B35), Color(0xFFFFA726)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // === Accuracy trend ===
            item {
                SectionHeader(title = "准确率趋势", icon = Icons.Filled.TrendingUp)
            }

            item {
                AccuracyChart(
                    sessions = uiState.sessions.takeLast(10),
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            }

            // === Weak spot analysis ===
            if (uiState.weakSpotSections.isNotEmpty()) {
                item {
                    SectionHeader(title = "薄弱环节", icon = Icons.Filled.Flag)
                }
                items(uiState.weakSpotSections) { section ->
                    WeakSpotCard(section)
                }
            }

            // === Session history ===
            item {
                SectionHeader(title = "最近练习", icon = Icons.Filled.History)
            }

            items(uiState.sessions.takeLast(10).asReversed()) { session ->
                SessionHistoryItem(session)
            }
        }
    }
}

@Composable
private fun AccuracyChart(
    sessions: List<SessionRecord>,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) return

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height

            // Grid lines
            val gridColor = Color(0xFFE0E0E0)
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // Accuracy line
            val points = sessions.mapIndexed { idx, s ->
                Offset(
                    x = if (sessions.size > 1) idx.toFloat() / (sessions.size - 1) * w else w / 2,
                    y = h - (s.accuracy * h)
                )
            }

            // Draw filled area under curve
            if (points.size > 1) {
                val areaPath = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(areaPath, Color(0xFF6750A4).copy(alpha = 0.1f))
            }

            // Draw line
            val linePath = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
            }
            drawPath(linePath, Color(0xFF6750A4), style = Stroke(
                width = 3f,
                cap = StrokeCap.Round
            ))

            // Draw points
            points.forEach { point ->
                drawCircle(Color(0xFF6750A4), radius = 5f, center = point)
                drawCircle(Color.White, radius = 2f, center = point)
            }
        }
    }
}

@Composable
private fun SessionHistoryItem(session: SessionRecord) {
    val accuracyColor = when {
        session.accuracy >= 0.8f -> Color(0xFF4CAF50)
        session.accuracy >= 0.5f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accuracy circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accuracyColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${(session.accuracy * 100).toInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accuracyColor
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.scoreTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "${formatDuration(session.durationMs)} · ✅${session.correctNotes} ❌${session.wrongNotes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRelativeTime(session.startTime), fontSize = 11.sp,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
    }
}

// Helpers
private fun formatDuration(ms: Long): String {
    val mins = ms / 60000
    val secs = (ms % 60000) / 1000
    return if (mins > 0) "${mins}m" else "${secs}s"
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val mins = diff / 60000
    return when {
        mins < 1 -> "刚刚"
        mins < 60 -> "${mins}分钟前"
        mins < 1440 -> "${mins / 60}小时前"
        else -> "${mins / 1440}天前"
    }
}

/** 将内部 0 基小节序号转为 1 基显示。 */
private fun displayMeasure(measureIndex: Int): Int = measureIndex + 1

private fun errorTypeLabel(type: MatchStatus): String = when (type) {
    MatchStatus.WRONG_PITCH -> "音高错误"
    MatchStatus.MISSING_NOTE -> "漏弹"
    MatchStatus.EXTRA_NOTE -> "多弹"
    MatchStatus.RHYTHM_ERROR -> "节奏错误"
    MatchStatus.CORRECT -> "正确"
}

private fun trendEmoji(trend: WeakSpotTrend): String = when (trend) {
    WeakSpotTrend.IMPROVING -> "📈"
    WeakSpotTrend.STABLE -> "➖"
    WeakSpotTrend.WORSENING -> "📉"
    WeakSpotTrend.INSUFFICIENT_DATA -> "❓"
}

/**
 * 薄弱环节分析卡片：展示单首乐谱的弱项摘要、推荐练习段落与重点小节。
 */
@Composable
private fun WeakSpotCard(section: StatsViewModel.WeakSpotSection) {
    val report = section.report
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                section.scoreTitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                report.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // 推荐练习段落
            report.recommendedPassages.take(2).forEach { passage ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "重点练习 第 ${displayMeasure(passage.startMeasure)}–" +
                                "${displayMeasure(passage.endMeasure)} 小节",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "(${passage.totalErrors} 次错误)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            // 重点弱项小节（最多 3 个）
            report.weakSpots.take(3).forEach { spot ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "第 ${displayMeasure(spot.measureIndex)} 小节 · " +
                                errorTypeLabel(spot.dominantErrorType),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${spot.errorCount}次",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(trendEmoji(spot.trend), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
