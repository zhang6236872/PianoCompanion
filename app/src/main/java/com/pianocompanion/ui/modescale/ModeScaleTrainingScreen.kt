package com.pianocompanion.ui.modescale

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.modescale.*

/**
 * 调式音阶色彩对比训练主界面（Material 3 Compose）。
 *
 * 功能流程（调式色彩辨识）：
 * 1. 选择难度（初级 大调vs小调 · 中级 4种调式 · 高级 7种教会调式）
 * 2. 开始练习后，听到主音参照 + 上下行调式音阶
 * 3. 用户判断这个音阶是哪种调式
 * 4. 答题后显示对错 + 调式色彩可视化，点击「下一题」继续
 *
 * 核心训练：辨识不同调式的色彩差异（明亮/暗淡的听觉感知）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeScaleTrainingScreen(
    viewModel: ModeScaleViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ModeScaleViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 调式色彩对比", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.isSessionActive) {
                ModeScaleSetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selected -> viewModel.startSession(selected) }
                )
            } else {
                ModeScalePracticePanel(
                    uiState = uiState,
                    onPlayAudio = { viewModel.playAudio() },
                    onStopAudio = { viewModel.stopAudio() },
                    onSubmit = { viewModel.submitAnswer(it) },
                    onNext = { viewModel.nextQuestion() },
                    onEnd = { viewModel.endSession() }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── 配置面板 ──────────────────────────────────────────────

@Composable
private fun ModeScaleSetupPanel(
    difficulty: ModeScaleDifficulty,
    progress: ModeScaleProgress,
    onStart: (ModeScaleDifficulty) -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(difficulty) }

    // 难度选择
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择难度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeScaleDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text("${d.displayName}（${d.description}）") }
                    )
                }
            }
        }
    }

    // 进度展示
    if (progress.totalAnswered > 0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📊 练习记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MSStatColumn("总答题", "${progress.totalAnswered}")
                    MSStatColumn("正确", "${progress.totalCorrect}")
                    MSStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    MSStatColumn("最长连击", "${progress.overallBestStreak}")
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = { onStart(selectedDifficulty) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, "开始")
        Spacer(modifier = Modifier.width(8.dp))
        Text("开始听辨", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }

    // 说明卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💡 如何练习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("1. 点击播放，先听到主音参照，然后是上下行调式音阶", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 感受这个音阶的「色彩」——是明亮还是暗淡", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选出正确的调式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎨 调式色彩光谱", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 利底亚（最亮）→ 大调 → 混合利底亚", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 多利亚 → 小调 → 弗里几亚 → 洛克利亚（最暗）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 先判断整体明暗：像大调还是像小调", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 大调类：注意第4音（增四度=利底亚）和第7音（小七度=混合利底亚）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 小调类：注意第2音（小二度=弗里几亚）和第6音（大六度=多利亚）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 调式色彩是理解爵士、民谣、世界音乐的核心", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun ModeScalePracticePanel(
    uiState: ModeScaleUiState,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit
) {
    val question = uiState.currentQuestion ?: return

    // 统计栏
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MSStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        MSStatCard("${uiState.currentStreak}", "连击")
        MSStatCard(
            if (uiState.answeredCount > 0) "${"%.0f".format(uiState.correctCount.toDouble() / uiState.answeredCount * 100)}%" else "—",
            "准确率"
        )
    }

    // 难度标签
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                question.difficulty.displayName,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onEnd) {
            Text("结束", color = MaterialTheme.colorScheme.error)
        }
    }

    // 播放卡片 + 调式色彩可视化
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🎧 听调式音阶", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "主音参照 → 上行音阶 → 下行音阶 · 判断这是什么调式",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 调式色彩可视化（答题后展示，避免泄露答案）
            if (uiState.isAnswered) {
                ModeColorVisualization(
                    question = question,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }

            FilledIconButton(
                onClick = {
                    if (uiState.isPlaying) onStopAudio() else onPlayAudio()
                },
                modifier = Modifier.size(72.dp),
                enabled = uiState.audioReady
            ) {
                Icon(
                    if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayCircle,
                    "播放",
                    modifier = Modifier.size(40.dp)
                )
            }

            if (!uiState.audioReady) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("音频准备中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    if (uiState.isPlaying) "正在播放…（可点击停止重播）" else "点击播放，可多次重听",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 答题反馈
    AnimatedVisibility(
        visible = uiState.isAnswered,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val result = uiState.lastResult
        if (result != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isCorrect)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (result.isCorrect) "✅ 正确！" else "❌ 答错了",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "🎨 ${result.question.targetMode.fullLabel}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        result.question.targetMode.teachingDescription,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!result.isCorrect) {
                        Text(
                            "你的答案：${result.userAnswer}",
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "💡 提示：先判断整体是「亮」还是「暗」，再听特征音程——" +
                            "利底亚的增四度最亮，洛克利亚的减五度最暗。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.run {
                            if (result.isCorrect) onPrimaryContainer else onErrorContainer
                        }.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    // 答案选项
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        question.answerChoices.forEach { choice ->
            MSAnswerButton(
                text = choice,
                enabled = !uiState.isAnswered,
                isCorrect = choice == question.correctAnswer,
                isAnswered = uiState.isAnswered,
                onClick = { onSubmit(choice) }
            )
        }
    }

    if (uiState.isAnswered) {
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, "下一题")
            Spacer(modifier = Modifier.width(8.dp))
            Text("下一题", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 调式色彩可视化（答题后展示）。
 *
 * 横轴 = 7 种调式按亮度排列，当前目标调式高亮。
 */
@Composable
private fun ModeColorVisualization(
    question: ModeScaleQuestion,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padLeft = 8f
        val padRight = 8f
        val padTop = 8f
        val padBottom = 24f

        val drawW = w - padLeft - padRight
        val drawH = h - padTop - padBottom

        // 7 种调式按亮度排序（最暗到最亮）
        val sortedModes = ModeType.entries.sortedByDescending { it.brightness }
        val targetMode = question.targetMode

        val barW = drawW / sortedModes.size

        sortedModes.forEachIndexed { index, mode ->
            val x = padLeft + index * barW
            val isTarget = mode == targetMode

            // 亮度越高 → 越亮（黄）；越低 → 越暗（蓝紫）
            val brightness = mode.brightness // -3 to +3
            val normalizedBrightness = (brightness + 3) / 6.0f // 0 to 1
            val r = (255 * (0.4f + normalizedBrightness * 0.6f)).toInt().coerceIn(0, 255)
            val g = (255 * (0.5f + normalizedBrightness * 0.4f)).toInt().coerceIn(0, 255)
            val b = (255 * (0.9f - normalizedBrightness * 0.5f)).toInt().coerceIn(0, 255)

            val color = Color(r, g, b).copy(alpha = if (isTarget) 1.0f else 0.55f)

            // 色彩柱
            val barTop = padTop + drawH * (1.0f - 0.3f - normalizedBrightness * 0.6f)
            val barH = drawH * (0.3f + normalizedBrightness * 0.6f)
            drawRect(
                color = color,
                topLeft = Offset(x + barW * 0.1f, barTop),
                size = androidx.compose.ui.geometry.Size(barW * 0.8f, barH)
            )

            // 目标指示线
            if (isTarget) {
                drawLine(
                    color = Color(0xFFE65100),
                    start = Offset(x + barW / 2, padTop),
                    end = Offset(x + barW / 2, h - padBottom),
                    strokeWidth = 3f
                )
            }
        }

        // 标签
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(200, 80, 80, 80)
                textSize = 20f
                isAntiAlias = true
            }
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(220, 230, 81, 0)
                textSize = 22f
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            // 明暗标签
            paint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.nativeCanvas.drawText("暗", padLeft, h - 4f, paint)
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.nativeCanvas.drawText("亮", w - padRight, h - 4f, paint)
        }
    }
}

@Composable
private fun MSAnswerButton(
    text: String,
    enabled: Boolean,
    isCorrect: Boolean,
    isAnswered: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when {
        isAnswered && isCorrect -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isAnswered && isCorrect -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计组件 ──────────────────────────────────────────────

@Composable
private fun RowScope.MSStatCard(value: String, label: String) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MSStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
