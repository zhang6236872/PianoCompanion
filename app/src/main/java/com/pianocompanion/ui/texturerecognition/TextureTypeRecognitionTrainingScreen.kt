package com.pianocompanion.ui.texturerecognition

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.texturerecognition.*

/**
 * 织体类型辨识训练主界面（Material 3 Compose）。
 *
 * 功能流程（4 类基础织体辨识）：
 * 1. 选择难度（初级 单声部/主调/复调 · 中级 全 4 类 · 高级 更快更复杂）
 * 2. 开始练习后，听到一段短小的音乐片段
 * 3. 用户判断这段片段属于哪种织体（单声部/主调/复调/支声复调）
 * 4. 答题后显示对错 + 织体结构可视化（声部数量与关系），点击「下一题」继续
 *
 * 核心训练：辨识音乐的 4 种基础织体类型，难点在区分「复调」与「支声复调」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextureTypeRecognitionTrainingScreen(
    viewModel: TextureCategoryViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TextureCategoryViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎼 织体类型辨识", fontWeight = FontWeight.Bold) },
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
                TextureCategorySetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selected -> viewModel.startSession(selected) }
                )
            } else {
                TextureCategoryPracticePanel(
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
private fun TextureCategorySetupPanel(
    difficulty: MusicTextureDifficulty,
    progress: TextureCategoryProgress,
    onStart: (MusicTextureDifficulty) -> Unit
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
                MusicTextureDifficulty.ALL.forEach { d ->
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
                    TCStatColumn("总答题", "${progress.totalAnswered}")
                    TCStatColumn("正确", "${progress.totalCorrect}")
                    TCStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    TCStatColumn("最长连击", "${progress.overallBestStreak}")
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
            Text("1. 点击播放，听到一段短小的音乐片段", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 感受片段里有几条线、它们之间是什么关系", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选出正确的织体类型", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎼 四类基础织体", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 单声部：只有一条旋律线，一次只响一个音（干净纯粹）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 主调：一条突出的旋律 + 和声伴奏托底（旋律主角，伴奏背景）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 复调：两条独立的旋律线同时进行，各有自己的节奏（平等对话）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 支声复调：同一条旋律由两个声部演奏，一条加装饰（同源加花）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 先判断「有几条线在响」：只有一条 = 单声部；多条 = 继续分辨", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 多条线时，看旋律素材是否相同：相同(被加花) = 支声；不同(各自独立) = 复调", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 有没有突出主旋律 + 和声背景？有 = 主调", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 复调的难点：两条线是「两个不同的故事」；支声是「一个故事被装饰」", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun TextureCategoryPracticePanel(
    uiState: TextureCategoryUiState,
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
        TCStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        TCStatCard("${uiState.currentStreak}", "连击")
        TCStatCard(
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

    // 播放卡片 + 织体结构可视化
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
            Text("🎧 听音乐片段", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "感受有几条线、它们之间的关系",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 织体结构可视化（答题后展示，避免泄露答案）
            if (uiState.isAnswered) {
                TextureStructureVisualization(
                    texture = question.targetTexture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
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
                        "${result.question.targetTexture.emoji} ${result.question.targetTexture.fullLabel}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        result.question.targetTexture.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.run {
                            if (result.isCorrect) onPrimaryContainer else onErrorContainer
                        }.copy(alpha = 0.85f)
                    )
                    Text(
                        "💡 听辨线索：${result.question.targetTexture.listenHint}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.run {
                            if (result.isCorrect) onPrimaryContainer else onErrorContainer
                        }.copy(alpha = 0.85f)
                    )
                    if (!result.isCorrect) {
                        Text(
                            "你的答案：${result.userAnswer}",
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "🔑 提示：先数有几条线；多条线时，旋律素材相同=支声复调，" +
                            "素材不同=复调；有突出主旋律+和声背景=主调。",
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
            TCAnswerButton(
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
 * 织体结构可视化（答题后展示）。
 *
 * 用色块直观表示织体的声部结构：
 * - 单声部：一条线
 * - 主调：一条旋律线（上）+ 和声块（下）
 * - 复调：两条独立的波浪线
 * - 支声复调：两条同形的波浪线（一条加装饰点）
 */
@Composable
private fun TextureStructureVisualization(
    texture: MusicTextureType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        when (texture) {
            MusicTextureType.MONOPHONIC -> {
                // 单条线
                drawTrack(w, midY, Color(0xFF42A5F5), "单条旋律线")
            }
            MusicTextureType.HOMOPHONIC -> {
                // 上方旋律线 + 下方和声块
                drawTrack(w, h * 0.30f, Color(0xFFE65100), "旋律")
                drawBlocks(w, h * 0.62f, Color(0xFF66BB6A))
            }
            MusicTextureType.POLYPHONIC -> {
                // 两条独立波浪线
                drawWavyTrack(w, h * 0.32f, Color(0xFF42A5F5), phase = 0f)
                drawWavyTrack(w, h * 0.68f, Color(0xFFAB47BC), phase = 1.6f)
            }
            MusicTextureType.HETEROPHONIC -> {
                // 两条同形波浪线（一条加装饰点）
                drawWavyTrack(w, h * 0.32f, Color(0xFF42A5F5), phase = 0f)
                drawWavyTrack(w, h * 0.68f, Color(0xFFAB47BC), phase = 0f, withOrnaments = true)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrack(
    w: Float, y: Float, color: Color, label: String
) {
    drawRect(
        color = color.copy(alpha = 0.85f),
        topLeft = Offset(8f, y - 10f),
        size = Size(w - 16f, 20f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlocks(
    w: Float, y: Float, color: Color
) {
    val blockW = (w - 16f) / 4f - 4f
    for (i in 0 until 4) {
        drawRect(
            color = color.copy(alpha = 0.8f),
            topLeft = Offset(8f + i * (blockW + 4f), y - 14f),
            size = Size(blockW, 28f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavyTrack(
    w: Float, y: Float, color: Color, phase: Float, withOrnaments: Boolean = false
) {
    val segments = 40
    val amp = 14f
    var prevX = 8f
    var prevY = y + amp * kotlin.math.sin(phase)
    for (i in 1..segments) {
        val x = 8f + (w - 16f) * i / segments
        val sy = y + amp * kotlin.math.sin(phase + i * 0.5f)
        drawLine(
            color = color.copy(alpha = 0.9f),
            start = Offset(prevX, prevY),
            end = Offset(x, sy),
            strokeWidth = 6f
        )
        prevX = x
        prevY = sy
    }
    if (withOrnaments) {
        // 装饰点
        for (i in 1 until segments step 4) {
            val x = 8f + (w - 16f) * i / segments
            val sy = y + amp * kotlin.math.sin(phase + i * 0.5f)
            drawCircle(
                color = color.copy(alpha = 0.9f),
                radius = 4f,
                center = Offset(x, sy - 12f)
            )
        }
    }
}

@Composable
private fun TCAnswerButton(
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
private fun RowScope.TCStatCard(value: String, label: String) {
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
private fun TCStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
