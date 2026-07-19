package com.pianocompanion.ui.subdivisionrecognition

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.pianocompanion.subdivisionrecognition.*

/**
 * 节奏细分听辨训练主界面（Material 3 Compose）。
 *
 * 功能流程（节奏细分听辨训练）：
 * 1. 选择难度（初级 二分vs三连音 · 中级 加入四分 · 高级 连奏模糊音头）
 * 2. 开始练习后，播放一段乐句，每拍被等分成若干个音符
 * 3. 用户判断每拍被分成了几等份（2 / 3 / 4）
 * 4. 从「二分细分 / 三连音 / 四分细分」选项中选出正确答案
 * 5. 答题后显示对错 + 拍点细分可视化（每拍几个点）+ 提示，点击「下一题」继续
 *
 * 核心训练：感知一拍内部的细分密度（duple / triple / quadruple feel）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubdivisionRecognitionTrainingScreen(
    viewModel: SubdivisionViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SubdivisionViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎼 节奏细分", fontWeight = FontWeight.Bold) },
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
                SubdivisionSetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selected -> viewModel.startSession(selected) }
                )
            } else {
                SubdivisionPracticePanel(
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
private fun SubdivisionSetupPanel(
    difficulty: SubdivisionDifficulty,
    progress: SubdivisionProgress,
    onStart: (SubdivisionDifficulty) -> Unit
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
                SubdivisionDifficulty.ALL.forEach { d ->
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
                    SubdivisionStatColumn("总答题", "${progress.totalAnswered}")
                    SubdivisionStatColumn("正确", "${progress.totalCorrect}")
                    SubdivisionStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    SubdivisionStatColumn("最长连击", "${progress.overallBestStreak}")
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
            Text("1. 点击播放，听到一段乐句，每一拍被等分成若干个音符", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 数一数每拍里有几个等距的音 —— 是 2 个、3 个还是 4 个", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选出对应的细分类型", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎵 这个能力训练什么", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 感知「2 对 3 对 4」的律动感（duple / triple / quadruple feel）—— 节奏核心素养", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 与拍号听辨不同：拍号问「每小节几拍」，这里问「每拍分几份」", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 高级难度用连奏模糊音头，须靠整体律动而非数音头", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 跟着拍点音（略响的那个）找到「拍」，再数拍内有几个音", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 三连音有独特的「摇晃感」，和「直」的二分明显不同", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 拿不准时可反复重听，专注整体密度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun SubdivisionPracticePanel(
    uiState: SubdivisionUiState,
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
        SubdivisionStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        SubdivisionStatCard("${uiState.currentStreak}", "连击")
        SubdivisionStatCard(
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
                "${question.difficulty.displayName} · ${if (question.staccato) "断奏" else "连奏"} · ${"%.0f".format(60000.0 / question.beatMs)} BPM",
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
            Text("🎧 每拍分成了几等份？", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "每拍起始音略响（标记拍位）· 数一数拍里有几个音",
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

    // 答题反馈（含拍点细分可视化）
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
                    // 拍点细分可视化：每拍 N 个点，展示真实细分密度
                    SubdivisionBeatVisualization(
                        beatsPerMeasure = question.beatsPerMeasure,
                        notesPerBeat = question.subdivision.notesPerBeat
                    )
                    Text(
                        "🎼 ${question.subdivision.displayName} · 每拍 ${question.subdivision.notesPerBeat} 个音（${question.subdivision.symbol}）",
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
                        "💡 ${question.subdivision.description}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.run {
                            if (result.isCorrect) onPrimaryContainer else onErrorContainer
                        }.copy(alpha = 0.85f)
                    )
                    Text(
                        "🎧 ${question.subdivision.hint}",
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
            SubdivisionAnswerButton(
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
 * 拍点细分可视化：展示每拍的细分音符数。
 * 每拍一行圆点（拍点音用大圆 + 高亮，细分音用小圆）。
 */
@Composable
private fun SubdivisionBeatVisualization(
    beatsPerMeasure: Int,
    notesPerBeat: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (beat in 1..beatsPerMeasure) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (n in 1..notesPerBeat) {
                    val isBeatStart = n == 1
                    val color = if (isBeatStart) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .size(if (isBeatStart) 16.dp else 11.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
        Text("每拍 $notesPerBeat 个音", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubdivisionAnswerButton(
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
private fun RowScope.SubdivisionStatCard(value: String, label: String) {
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
private fun SubdivisionStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
