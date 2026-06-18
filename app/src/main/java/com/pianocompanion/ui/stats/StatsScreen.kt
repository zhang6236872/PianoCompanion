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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianocompanion.data.model.PracticeRating
import com.pianocompanion.data.model.SessionRecord
import com.pianocompanion.data.repository.StatsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val repository = remember { StatsRepository(context) }
    val aggregated = remember { repository.getAggregatedStats() }
    val sessions = remember { repository.getRecentSessions(20) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 练习统计", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("还没有练习记录", style = MaterialTheme.typography.titleMedium)
                    Text("开始你的第一次练习吧！", style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // === Overview cards ===
                item {
                    OverviewCards(aggregated)
                }

                // === Streak banner ===
                item {
                    StreakBanner(aggregated.currentStreak)
                }

                // === Accuracy trend chart ===
                item {
                    AccuracyTrendChart(sessions.takeLast(minOf(15, sessions.size)))
                }

                // === Session history ===
                item {
                    Text(
                        "最近练习记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(sessions) { session ->
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun OverviewCards(aggregated: com.pianocompanion.data.model.AggregatedStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "总练习",
            value = "\${aggregated.totalSessions}",
            subtitle = "次",
            color = Color(0xFF42A5F5),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "总时长",
            value = aggregated.getFormattedTotalTime(),
            subtitle = "",
            color = Color(0xFF66BB6A),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "平均准确率",
            value = "\${(aggregated.averageAccuracy * 100).toInt()}%",
            subtitle = "最佳 \${(aggregated.bestAccuracy * 100).toInt()}%",
            color = Color(0xFFFFA726),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 10.sp, color = color.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun StreakBanner(streak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF6B35)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("🔥", fontSize = 32.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("连续练习", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                Text("\${streak} 天", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("💪", fontSize = 32.sp)
        }
    }
}

@Composable
private fun AccuracyTrendChart(sessions: List<SessionRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📈 准确率趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (sessions.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (sessions.size - 1)

                    val path = Path()
                    val fillPath = Path()

                    sessions.forEachIndexed { i, session ->
                        val x = i * stepX
                        val y = h - session.accuracy * h
                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        if (i == sessions.size - 1) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }

                    drawPath(fillPath, color = Color(0xFF42A5F5).copy(alpha = 0.2f))
                    drawPath(path, color = Color(0xFF42A5F5), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

                    // Draw dots
                    sessions.forEachIndexed { i, session ->
                        val x = i * stepX
                        val y = h - session.accuracy * h
                        drawCircle(
                            color = if (session.accuracy >= 0.8f) Color(0xFF4CAF50) else Color(0xFFEF5350),
                            radius = 5f,
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }
                }
            } else {
                Text("需要至少2次练习记录", fontSize = 12.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionRecord) {
    val rating = session.getRating()
    val accuracyColor = when {
        session.accuracy >= 0.8f -> Color(0xFF4CAF50)
        session.accuracy >= 0.5f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rating circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accuracyColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(rating.emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.scoreTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "\${session.getFormattedDate()} · \${session.getFormattedDuration()} · \${session.totalNotes}个音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Accuracy
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "\${(session.accuracy * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accuracyColor
                )
                Text(rating.label, fontSize = 10.sp, color = accuracyColor)
            }
        }
    }
}
