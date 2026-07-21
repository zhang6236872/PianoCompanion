package com.pianocompanion.ui.voiceentryorder

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
import com.pianocompanion.voiceentryorder.*

/**
 * 声部进入顺序辨识训练主界面（Material 3 Compose）。
 *
 * 功能流程（声部进入顺序辨识训练）：
 * 1. 选择难度（初级 2 声部 · 中级 3 声部 · 高级 3 声部紧密）
 * 2. 开始练习后，播放一段多声部先后进入的短织体
 * 3. 用户聆听各音区出现的时间先后——先听到哪个、后听到哪个
 * 4. 从选项中选出正确的进入顺序（如「低声部 → 高声部」）
 * 5. 答题后显示对错 + 进入顺序时间线可视化，点击「下一题」继续
 *
 * 核心训练：辨识复调织体中声部进入的时间先后顺序（赋格主题呈现的核心听觉能力）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEntryTrainingScreen(
    viewModel: VoiceEntryViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceEntryViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎭 声部进入顺序", fontWeight = FontWeight.Bold) },
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
                VoiceEntrySetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selected -> viewModel.startSession(selected) }
                )
            } else {
                VoiceEntryPracticePanel(
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
private fun VoiceEntrySetupPanel(
    difficulty: EntryDifficulty,
    progress: VoiceEntryProgress,
    onStart: (EntryDifficulty) -> Unit
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
                EntryDifficulty.ALL.forEach { d ->
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
                    VEStatColumn("总答题", "${progress.totalAnswered}")
                    VEStatColumn("正确", "${progress.totalCorrect}")
                    VEStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    VEStatColumn("最长连击", "${progress.overallBestStreak}")
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
            Text("1. 点击播放，听到多个声部先后进入（每个声部在其音区演奏短动机）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 注意「先出现的是哪个音区？再出现的是哪个？」", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 按进入先后顺序，从选项中选出正确排列", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎭 3 个声部音区", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 🎵 高声部（Soprano）— 高音区", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 🎶 中声部（Alto）— 中音区", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 🎸 低声部（Bass）— 低音区", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 每当有「新声音出现」时，立即判断它的音区高低", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 逐个记录进入顺序：第一个出现的音区 → 第二个 → 第三个", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 可以反复重听，每次只关注「新出现的声部落在高音还是低音」", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 这是跟随赋格、卡农等复调作品的核心能力——主题在各声部依次呈现", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun VoiceEntryPracticePanel(
    uiState: VoiceEntryUiState,
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
        VEStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        VEStatCard("${uiState.currentStreak}", "连击")
        VEStatCard(
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

    // 播放卡片 + 进入顺序时间线可视化
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
            Text("🎧 听声部进入的先后", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "多个声部依次加入 · 注意每个新音区出现的时间先后",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 进入顺序时间线可视化（答题后展示，避免泄露答案）
            if (uiState.isAnswered) {
                EntryOrderTimeline(
                    question = question,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
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
                        "🎭 进入顺序：${result.question.emojiOrderLabel}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${result.question.correctAnswer}",
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
                        "💡 提示：每当有新声音出现时，立即判断它是高音区还是低音区。" +
                            "在赋格中，主题往往从某个声部开始，然后依次在其他声部重现。",
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
            VEAnswerButton(
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
 * 进入顺序时间线可视化（答题后展示）。
 *
 * 横轴 = 时间，每个声部一条水平轨道（高声部在上、低声部在下）。
 * 每个声部从其进入时间开始绘制音符点，帮助用户直观理解进入先后。
 */
@Composable
private fun EntryOrderTimeline(
    question: EntryOrderQuestion,
    modifier: Modifier = Modifier
) {
    val builder = remember(question) { VoiceEntryAudioBuilder() }
    Canvas(modifier = modifier) {
        val events = builder.buildNoteEvents(question)
        if (events.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val padLeft = 52f
        val padRight = 16f
        val padTop = 16f
        val padBottom = 16f

        val maxOnsetEnd = events.maxOf { it.onsetMs + it.durationMs }.toFloat().coerceAtLeast(1f)
        val drawW = w - padLeft - padRight
        val drawH = h - padTop - padBottom

        // 轨道排序：高声部在上，低声部在下
        val laneOrder = listOf(VoiceRegister.SOPRANO, VoiceRegister.ALTO, VoiceRegister.BASS)
            .filter { it in question.entryOrder }
        val laneCount = laneOrder.size
        fun laneY(reg: VoiceRegister): Float {
            val idx = laneOrder.indexOf(reg)
            return padTop + drawH * (idx + 0.5f) / laneCount
        }
        fun timeX(ms: Double): Float = padLeft + drawW * (ms.toFloat() / maxOnsetEnd)

        val colors = mapOf(
            VoiceRegister.SOPRANO to Color(0xFF1976D2),
            VoiceRegister.ALTO to Color(0xFF7B1FA2),
            VoiceRegister.BASS to Color(0xFFE65100)
        )

        // 绘制轨道标签 + 基线
        laneOrder.forEach { reg ->
            val y = laneY(reg)
            val color = colors[reg] ?: Color.Gray
            // 基线
            drawLine(
                color = color.copy(alpha = 0.2f),
                start = Offset(padLeft, y),
                end = Offset(w - padRight, y),
                strokeWidth = 2f
            )
        }

        // 绘制每个音符点
        events.forEach { ev ->
            val x = timeX(ev.onsetMs)
            val y = laneY(ev.register)
            val color = colors[ev.register] ?: Color.Gray
            // 进入位置的第一个音符画稍大圆圈 + 竖线标记
            val isEntry = ev.onsetMs < question.difficulty.entryGapMs * 0.5
            if (isEntry) {
                drawLine(
                    color = color.copy(alpha = 0.5f),
                    start = Offset(x, padTop),
                    end = Offset(x, h - padBottom),
                    strokeWidth = 1.5f
                )
                drawCircle(color, radius = 8f, center = Offset(x, y))
            } else {
                drawCircle(color.copy(alpha = 0.85f), radius = 5f, center = Offset(x, y))
            }
        }

        // 轨道名标签
        laneOrder.forEach { reg ->
            val y = laneY(reg)
            val color = colors[reg] ?: Color.Gray
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(180, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                    textSize = 22f
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(reg.emoji, 8f, y + 8f, paint)
            }
        }
    }
}

@Composable
private fun VEAnswerButton(
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
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计组件 ──────────────────────────────────────────────

@Composable
private fun RowScope.VEStatCard(value: String, label: String) {
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
private fun VEStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
