package com.pianocompanion.ui.voicecounttraining

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.voicecounttraining.*

/**
 * 声部数量听辨训练主界面（Material 3 Compose）。
 *
 * 功能流程（声部数量听辨训练）：
 * 1. 选择难度（初级 1-3音宽间距 · 中级 1-4音中间距 · 高级 1-6音密集间距）
 * 2. 开始练习后，播放一段同时鸣响的和弦（block chord）
 * 3. 用户判断同时鸣响的音有几个
 * 4. 从「1 个音 … N 个音」选项中选出正确答案
 * 5. 答题后显示对错 + 声部数可视化（音符圆点）+ 提示，点击「下一题」继续
 *
 * 核心训练：和声密度感知——"这一刻有几个音在响？"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCountTrainingScreen(
    viewModel: VoiceCountViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceCountViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎹 声部数量", fontWeight = FontWeight.Bold) },
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
                VoiceCountSetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selected -> viewModel.startSession(selected) }
                )
            } else {
                VoiceCountPracticePanel(
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
private fun VoiceCountSetupPanel(
    difficulty: VoiceCountDifficulty,
    progress: VoiceCountProgress,
    onStart: (VoiceCountDifficulty) -> Unit
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
                VoiceCountDifficulty.ALL.forEach { d ->
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
                    VoiceCountStatColumn("总答题", "${progress.totalAnswered}")
                    VoiceCountStatColumn("正确", "${progress.totalCorrect}")
                    VoiceCountStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    VoiceCountStatColumn("最长连击", "${progress.overallBestStreak}")
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
            Text("1. 点击播放，听到一段同时鸣响的和弦（所有音同时起、同时落）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 感受这段声音的「厚度」——同时鸣响的音越多，声音越厚、越满", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 判断有几个音在同时响，从选项中选出", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎵 这个能力训练什么", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 和声密度感知：辨识「这一刻有几个音在响」——是辨认和弦种类之前的前置能力", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 与和弦品质训练不同：这里只关心数量，不关心是大三还是属七", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 间距越窄的音越容易融合成一个音色，高级难度的密集音簇最难数清", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 先建立参照：单音=一条线、音程=两条线、三和弦=丰满的三层", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 听到和弦后默想它有多「厚」，再数出层数", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 可反复重听，专注分辨同时鸣响的独立音高", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun VoiceCountPracticePanel(
    uiState: VoiceCountUiState,
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
        VoiceCountStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        VoiceCountStatCard("${uiState.currentStreak}", "连击")
        VoiceCountStatCard(
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
                "${question.difficulty.displayName} · ${question.spacing.displayName} · 最多 ${question.difficulty.voiceCountRange.last} 个音",
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

    // 播放卡片
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
            Text("🎧 数一数有几个音在响", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "所有音同时鸣响 · 感受声音的「厚度」 · 判断声部数量",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

    // 答题反馈（含声部数可视化）
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (result.isCorrect) "✅ 正确！" else "❌ 答错了",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // 声部数可视化：正确数量的音符圆点，标注用户答错的数量
                    VoiceCountVisualization(
                        correctCount = question.voiceCount,
                        userAnswerCount = if (result.isCorrect) null else parseCount(result.userAnswer)
                    )
                    Text(
                        "🎹 正确答案：${question.countLabel}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!result.isCorrect) {
                        Text(
                            "你的答案：${result.userAnswer}",
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "💡 提示：本题是${question.spacing.displayName}，" + spacingHint(question.spacing) +
                            if (question.voiceCount == 1) {
                                "只有一个音时，声音像一条单线，最易识别。"
                            } else if (question.voiceCount <= 3) {
                                "声部较少时各音相对独立，试着分辨每个独立音高。"
                            } else {
                                "声部较多时声音变「厚」、形成音簇，专注感受整体密度。"
                            },
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
            VoiceCountAnswerButton(
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

/** 间距 → 听辨提示文本。 */
private fun spacingHint(spacing: NoteSpacing): String = when (spacing) {
    NoteSpacing.WIDE -> "每个音清晰分离，间距较大。"
    NoteSpacing.MEDIUM -> "音与音之间中等间距，需专注分辨。"
    NoteSpacing.CLOSE -> "音与音密集排列，容易融合，最难数清。"
}

/** 从「N 个音（…）」选项文本解析出数字（解析失败返回 null）。 */
private fun parseCount(label: String): Int? {
    val s = label.trim()
    // 取开头连续数字
    val num = StringBuilder()
    for (c in s) {
        if (c.isDigit()) num.append(c) else break
    }
    return num.toString().toIntOrNull()
}

/**
 * 声部数可视化：用一行音符圆点表示正确声部数（高亮），用户答错数量用不同颜色标注。
 */
@Composable
private fun VoiceCountVisualization(
    correctCount: Int,
    userAnswerCount: Int?
) {
    val maxShow = maxOf(correctCount, userAnswerCount ?: 0, 1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..maxShow) {
            val isCorrectNote = i <= correctCount
            val isUserExtra = userAnswerCount != null && i <= userAnswerCount && i > correctCount
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val color = when {
                    isCorrectNote -> MaterialTheme.colorScheme.primary
                    isUserExtra -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(if (isCorrectNote) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    "🎵",
                    fontSize = if (isCorrectNote) 14.sp else 11.sp
                )
            }
        }
    }
}

@Composable
private fun VoiceCountAnswerButton(
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
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计组件 ──────────────────────────────────────────────

@Composable
private fun RowScope.VoiceCountStatCard(value: String, label: String) {
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
private fun VoiceCountStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
