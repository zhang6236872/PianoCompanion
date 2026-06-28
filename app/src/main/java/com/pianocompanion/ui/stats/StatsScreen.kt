package com.pianocompanion.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.pianocompanion.analytics.AchievementCategory
import com.pianocompanion.analytics.AchievementProgress
import com.pianocompanion.analytics.AchievementSummary
import com.pianocompanion.analytics.GoalDefinition
import com.pianocompanion.analytics.GoalEditor
import com.pianocompanion.analytics.GoalMetric
import com.pianocompanion.analytics.GoalPeriod
import com.pianocompanion.analytics.GoalProgress
import com.pianocompanion.analytics.GoalReport
import com.pianocompanion.analytics.GoalStatus
import com.pianocompanion.analytics.GoalTracker
import com.pianocompanion.analytics.GoalValidation
import com.pianocompanion.analytics.HeatmapCell
import com.pianocompanion.analytics.NoteMasteryReport
import com.pianocompanion.analytics.NoteRegister
import com.pianocompanion.analytics.PitchClassStat
import com.pianocompanion.analytics.PracticeHeatmap
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
    var showGoalEditor by remember { mutableStateOf(false) }

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

            // === Practice calendar heatmap ===
            val heatmap = uiState.heatmap
            if (heatmap != null) {
                item {
                    SectionHeader(title = "练习日历", icon = Icons.Filled.CalendarMonth)
                }
                item {
                    PracticeHeatmapCard(heatmap)
                }
            }

            // === Practice goals ===
            val goalReport = uiState.goalReport
            if (goalReport != null) {
                item {
                    SectionHeader(
                        title = "练习目标",
                        icon = Icons.Filled.Flag,
                        action = {
                            TextButton(onClick = { showGoalEditor = true }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "编辑目标",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("编辑", fontSize = 12.sp)
                            }
                        }
                    )
                }
                item {
                    GoalOverviewCard(goalReport)
                }
                items(goalReport.progresses) { progress ->
                    GoalCard(progress)
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

            // === Achievements ===
            val achievements = uiState.achievementSummary
            if (achievements != null) {
                item {
                    SectionHeader(title = "成就徽章", icon = Icons.Filled.EmojiEvents)
                }
                item {
                    AchievementOverviewCard(achievements)
                }
                items(achievements.all) { progress ->
                    AchievementCard(progress)
                }
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

            // === Note mastery analysis ===
            val noteMastery = uiState.noteMastery
            if (noteMastery != null && noteMastery.hasData) {
                item {
                    SectionHeader(title = "音符掌握度", icon = Icons.Filled.MusicNote)
                }
                item {
                    NoteMasteryCard(noteMastery)
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

    if (showGoalEditor) {
        GoalEditorDialog(
            currentGoals = viewModel.getCurrentGoals(),
            onDismiss = { showGoalEditor = false },
            onSave = { goals ->
                viewModel.setGoals(goals)
                showGoalEditor = false
            }
        )
    }

    // 成就解锁庆祝弹窗
    val newlyUnlocked = uiState.newlyUnlockedAchievements
    if (newlyUnlocked.isNotEmpty()) {
        AchievementUnlockDialog(
            achievements = newlyUnlocked,
            onDismiss = { viewModel.clearNewlyUnlocked() }
        )
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

/**
 * 成就总览卡片：展示已解锁/总数、完成进度条、分类统计。
 */
@Composable
private fun AchievementOverviewCard(summary: AchievementSummary) {
    val unlocked = summary.unlockedCount
    val total = summary.totalCount
    val ratio = summary.completionRatio

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🏆 $unlocked / $total",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${(ratio * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Category quick stats
            val byCategory = summary.byCategory()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                byCategory.forEach { (category, progresses) ->
                    val catUnlocked = progresses.count { it.isUnlocked }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(category.icon, fontSize = 16.sp)
                        Text(
                            "$catUnlocked/${progresses.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个成就卡片：展示图标、名称、描述、进度条（锁定时）或已解锁标记。
 */
@Composable
private fun AchievementCard(progress: AchievementProgress) {
    val def = progress.definition
    val unlocked = progress.isUnlocked

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (unlocked) 2.dp else 0.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Achievement icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) Color(0xFFFFD700).copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unlocked) def.category.icon else "🔒",
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        def.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (unlocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✓", fontSize = 14.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    def.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                if (!unlocked && progress.progressRatio > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.progressRatio },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${progress.formatCurrentValue()} / ${def.formatTarget()}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * 成就解锁庆祝弹窗：当用户解锁新成就时弹出，展示解锁的成就列表。
 */
@Composable
private fun AchievementUnlockDialog(
    achievements: List<AchievementProgress>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (achievements.size == 1) "成就解锁！" else "${achievements.size} 个成就解锁！",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                achievements.forEach { progress ->
                    val def = progress.definition
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 成就图标（金色圆形背景）
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(def.category.icon, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                def.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                def.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text("✓", fontSize = 18.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("太棒了！", fontWeight = FontWeight.Bold)
            }
        }
    )
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
 * 练习日历热力图卡片：GitHub 风格的贡献网格。
 *
 * 按周(列) × 7 天(行) 展示练习强度，颜色深浅表示当天总练习时长。
 * 顶部展示汇总（活跃天数 / 累计时长 / 最长连续），底部为强度图例。
 */
@Composable
private fun PracticeHeatmapCard(heatmap: PracticeHeatmap) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 汇总行
            Text(
                heatmap.summary(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 热力图网格（横向滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (column in heatmap.columns) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (cell in column.cells) {
                            HeatmapCellBox(cell)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "少",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                for (level in 0..4) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(heatmapLevelColor(level))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    "多",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/** 热力图单个格子（一天）。 */
@Composable
private fun HeatmapCellBox(cell: HeatmapCell) {
    val color = heatmapLevelColor(cell.level)
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

/** 强度等级 0–4 → 颜色（GitHub 风格绿色阶梯）。 */
private fun heatmapLevelColor(level: Int): Color = when (level) {
    0 -> Color(0xFFEbedF0)
    1 -> Color(0xFF9BE9A8)
    2 -> Color(0xFF40C463)
    3 -> Color(0xFF30A14E)
    else -> Color(0xFF216E39)
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

// ═══════════════════════════════════════════════════════════════════════
//  音符掌握度 (Note Mastery) UI
// ═══════════════════════════════════════════════════════════════════════

/**
 * 音符掌握度分析卡片：展示跨乐谱的音高维度弱项分析，
 * 包括最薄弱音级、黑/白键对比、音域分布、最易出错音符和音高混淆。
 */
@Composable
private fun NoteMasteryCard(report: NoteMasteryReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 摘要
            Text(
                report.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 17.sp
            )

            // 最薄弱音级排行（带水平条形图）
            val topPitches = report.pitchClassStats
                .filter { it.errorCount > 0 }
                .take(5)
            if (topPitches.isNotEmpty()) {
                Text("最易出错音级", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val maxCount = topPitches.maxOf { it.errorCount }
                topPitches.forEach { stat ->
                    PitchClassBar(stat, maxCount)
                }
            }

            // 黑键 vs 白键
            val ratio = report.keyTypeStats.blackToWhiteRatio
            if (report.keyTypeStats.blackKeyCount > 0 || report.keyTypeStats.whiteKeyCount > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("白键错误", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(
                            "${report.keyTypeStats.whiteKeyCount} 次 " +
                                    "(${(report.keyTypeStats.whiteKeyRate * 100).toInt()}%)",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("黑键错误", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        val ratioText = if (ratio.isFinite() && ratio > 0) {
                            " (${ratio.toInt()}.${((ratio % 1) * 10).toInt()}×)"
                        } else ""
                        Text(
                            "${report.keyTypeStats.blackKeyCount} 次 " +
                                    "(${(report.keyTypeStats.blackKeyRate * 100).toInt()}%)$ratioText",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (ratio.isFinite() && ratio > 1.5f)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 音域分布
            if (report.registerStats.totalAnalyzedErrors > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RegisterPill(NoteRegister.LOW, report.registerStats.rateFor(NoteRegister.LOW), report.registerStats.lowCount)
                    RegisterPill(NoteRegister.MID, report.registerStats.rateFor(NoteRegister.MID), report.registerStats.midCount)
                    RegisterPill(NoteRegister.HIGH, report.registerStats.rateFor(NoteRegister.HIGH), report.registerStats.highCount)
                }
            }

            // 最易混淆的音对
            val topConfusion = report.topConfusions.firstOrNull()
            if (topConfusion != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔀", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "最易混淆：${topConfusion.expectedNote} → ${topConfusion.detectedNote}" +
                                "（${topConfusion.count} 次）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 单个音级的水平条形图行。
 */
@Composable
private fun PitchClassBar(stat: PitchClassStat, maxCount: Int) {
    val fraction = if (maxCount > 0) stat.errorCount.toFloat() / maxCount else 0f
    val barColor = if (stat.isAccidental)
        Color(0xFFE53935) // 升降号 → 红色
    else
        Color(0xFF1E88E5) // 白键音级 → 蓝色
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 音级名
        Text(
            stat.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(28.dp)
        )
        // 条形图
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        // 次数
        Text(
            "${stat.errorCount}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(28.dp)
        )
    }
}

/**
 * 音域错误分布胶囊。
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RegisterPill(
    register: NoteRegister,
    rate: Float,
    count: Int
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(register.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text("${count}次", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("${(rate * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  练习目标 (Practice Goals) UI
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun GoalOverviewCard(report: GoalReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val completionPct = (report.completionRatio * 100).toInt()
            Text(
                if (report.allCompleted) "🎉 全部目标已达成！" else "已完成 ${report.completedCount}/${report.totalCount} 个目标 ($completionPct%)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (report.allCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { report.completionRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (report.allCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 每日/每周目标分组统计
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📅 每日目标", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(
                        "${report.dailyCompletedCount}/${report.dailyGoals.size} 达成",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (report.weeklyGoals.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📆 每周目标", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text(
                            "${report.weeklyCompletedCount}/${report.weeklyGoals.size} 达成",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            // 继续努力提示
            report.nextGoal?.let { next ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "💪 最接近: ${next.definition.metric.icon} ${next.definition.metric.label} ${next.formatCurrent()}/${next.formatTarget()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun GoalCard(progress: GoalProgress) {
    val status = progress.status()
    val statusColor = when (status) {
        GoalStatus.COMPLETED -> Color(0xFF4CAF50)
        GoalStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
        GoalStatus.BEHIND -> Color(0xFFFF9800)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        progress.definition.metric.icon,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "${progress.definition.period.label}${progress.definition.metric.label}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${progress.formatCurrent()} / ${progress.formatTarget()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Text(
                    "${status.emoji} ${status.label}",
                    fontSize = 12.sp,
                    color = statusColor
                )
            }
            if (!progress.isCompleted) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.progressRatio },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  目标编辑对话框 (Goal Editor Dialog)
// ═══════════════════════════════════════════════════════════════════════

/**
 * 练习目标编辑对话框。
 *
 * 功能：
 * - 预设快捷应用（轻松/适中/挑战）
 * - 逐个目标启用/禁用开关
 * - 已启用目标的目标值步进调整（含建议值快捷按钮）
 * - 保存/取消
 *
 * @param currentGoals 当前已启用的目标列表（用于初始化编辑状态）
 * @param onDismiss 取消回调
 * @param onSave 保存回调，传入编辑后的完整目标列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorDialog(
    currentGoals: List<GoalDefinition>,
    onDismiss: () -> Unit,
    onSave: (List<GoalDefinition>) -> Unit
) {
    // 编辑状态：key → 目标定义（启用集）
    val goalMap = remember {
        mutableStateMapOf<String, GoalDefinition>().apply {
            currentGoals.forEach { put(it.key, it) }
        }
    }
    // 当前选中的预设（用于高亮）
    var selectedPreset by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🎯 编辑练习目标",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 预设快捷应用
                Text(
                    "📋 快速预设",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GoalTracker.presets().keys.forEach { presetName ->
                        FilterChip(
                            selected = selectedPreset == presetName,
                            onClick = {
                                selectedPreset = presetName
                                goalMap.clear()
                                GoalEditor.applyPreset(presetName).forEach { goalMap[it.key] = it }
                            },
                            label = { Text(presetName, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 目标列表（可滚动）
                Text(
                    "⚙️ 目标设置",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (period in GoalPeriod.values()) {
                        Text(
                            period.label + "目标",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        for (metric in GoalMetric.values()) {
                            val key = "${period.name}_${metric.name}"
                            val goal = goalMap[key]
                            val enabled = goal != null
                            GoalEditRow(
                                metric = metric,
                                period = period,
                                enabled = enabled,
                                target = goal?.target ?: GoalEditor.suggestedTargets(metric).let { it[it.size / 3] },
                                onToggle = { isChecked ->
                                    if (isChecked) {
                                        val defaultTarget = GoalEditor.suggestedTargets(metric)
                                            .let { it[it.size / 3] }
                                        goalMap[key] = GoalDefinition(metric, period, defaultTarget)
                                        selectedPreset = null
                                    } else {
                                        goalMap.remove(key)
                                        selectedPreset = null
                                    }
                                },
                                onTargetChange = { newTarget ->
                                    goalMap[key] = GoalDefinition(metric, period, newTarget)
                                    selectedPreset = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(goalMap.values.toList().sortedBy { it.period.ordinal * 100 + it.metric.ordinal })
                        },
                        enabled = goalMap.isNotEmpty()
                    ) {
                        Text("保存 (${goalMap.size})")
                    }
                }
            }
        }
    }
}

/**
 * 单个目标编辑行：开关 + 名称 + 目标值步进器（启用时显示）。
 */
@Composable
private fun GoalEditRow(
    metric: GoalMetric,
    period: GoalPeriod,
    enabled: Boolean,
    target: Double,
    onToggle: (Boolean) -> Unit,
    onTargetChange: (Double) -> Unit
) {
    val suggestions = GoalEditor.suggestedTargets(metric)
    val currentIdx = suggestions.indexOfFirst { kotlin.math.abs(it - target) < 0.01 }
    val displayTarget = GoalEditor.formatTargetForInput(metric, target)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 开关
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.85f)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // 图标 + 名称
        Text(metric.icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                metric.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (!enabled) {
                Text(
                    "未启用",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        // 目标值步进器（仅启用时显示）
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        // 步进到上一个建议值
                        val prevIdx = (currentIdx - 1).coerceAtLeast(0)
                        if (prevIdx != currentIdx || currentIdx < 0) {
                            val stepTarget = if (currentIdx > 0) suggestions[prevIdx] else target - stepSize(metric)
                            onTargetChange(clampTarget(metric, stepTarget))
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "减少", modifier = Modifier.size(18.dp))
                }
                Text(
                    displayTarget + metric.unit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = 56.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(
                    onClick = {
                        // 步进到下一个建议值
                        val nextIdx = (currentIdx + 1).coerceAtMost(suggestions.lastIndex)
                        if (nextIdx != currentIdx || currentIdx < 0) {
                            val stepTarget = if (currentIdx >= 0 && currentIdx < suggestions.lastIndex)
                                suggestions[nextIdx]
                            else target + stepSize(metric)
                            onTargetChange(clampTarget(metric, stepTarget))
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "增加", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 各指标类型的步进增量（手动微调用）。 */
private fun stepSize(metric: GoalMetric): Double = when (metric) {
    GoalMetric.PRACTICE_TIME -> 5.0
    GoalMetric.SESSION_COUNT -> 1.0
    GoalMetric.NOTES_PLAYED -> 100.0
    GoalMetric.ACCURACY -> 0.05
    GoalMetric.UNIQUE_PIECES -> 1.0
}

/** 将目标值钳制到合理范围。 */
private fun clampTarget(metric: GoalMetric, target: Double): Double = when (metric) {
    GoalMetric.ACCURACY -> target.coerceIn(0.0, 1.0)
    GoalMetric.PRACTICE_TIME -> target.coerceIn(1.0, 480.0)
    GoalMetric.SESSION_COUNT -> target.coerceIn(1.0, 20.0)
    GoalMetric.NOTES_PLAYED -> target.coerceIn(10.0, 100000.0)
    GoalMetric.UNIQUE_PIECES -> target.coerceIn(1.0, 50.0)
}
