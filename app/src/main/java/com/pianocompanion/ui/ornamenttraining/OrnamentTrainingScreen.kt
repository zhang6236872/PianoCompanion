package com.pianocompanion.ui.ornamenttraining

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.ornamenttraining.*

/**
 * 装饰音辨识训练主界面（Material 3 Compose）。
 *
 * 功能流程（装饰音辨识训练）：
 * 1. 选择难度（初级/中级/高级）
 * 2. 开始练习后，播放一段装饰音（围绕主音的快速装饰音符组合）
 * 3. 用户辨识该装饰音的形态——颤动/点头/环绕/倚靠
 * 4. 从选项中选出正确的装饰音类型
 * 5. 答题后显示对错 + 类型描述 + 听感特征，点击「下一题」继续
 *
 * 核心训练：辨识装饰音的形态——颤音/波音/回音/短倚音/长倚音。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrnamentTrainingScreen(
    viewModel: OrnamentTrainingViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OrnamentTrainingViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎶 装饰音辨识", fontWeight = FontWeight.Bold) },
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
                OrnamentSetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { viewModel.startSession(uiState.difficulty) }
                )
            } else {
                OrnamentPracticePanel(
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
private fun OrnamentSetupPanel(
    difficulty: OrnamentDifficulty,
    progress: OrnamentTrainingProgress,
    onStart: () -> Unit
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
                OrnamentDifficulty.ALL.forEach { d ->
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
                    ORNStatColumn("总答题", "${progress.totalAnswered}")
                    ORNStatColumn("正确", "${progress.totalCorrect}")
                    ORNStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    ORNStatColumn("最长连击", "${progress.overallBestStreak}")
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = onStart,
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
            Text("1. 点击播放，听一段围绕主音的装饰音", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 注意音符的「运动形态」——是颤抖、点头、环绕，还是倚靠？", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 数一数音符的个数和长短，这是关键线索", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 从装饰音类型选项中选出正确答案", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎼 五种装饰音", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 💗 颤音（tr）—— 主音与上方音快速交替多次，华丽颤抖", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 🙇 波音（mo）—— 快速上跳再立刻回来，单次「点头」", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 🔄 回音（tu）—— 绕主音转一圈：上→主→下→主", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• ⚡ 短倚音 —— 一记极短的轻点紧接长主音（一闪而过）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 🤝 长倚音 —— 较长的重音「倚靠」后解决到主音（延宕）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 听到许多快速交替？—— 多半是颤音", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 只听到一两个短音 + 一个长音？—— 是倚音（轻点=短倚，重音=长倚）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 音符绕着主音上下走了一圈？—— 是回音", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun OrnamentPracticePanel(
    uiState: OrnamentTrainingUiState,
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
        ORNStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        ORNStatCard("${uiState.currentStreak}", "连击")
        ORNStatCard(
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
            Text("🎧 仔细听这段装饰音", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "围绕主音的快速装饰音符组合 · 注意音符的运动形态——颤动、点头、环绕还是倚靠？",
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
                        "${result.question.type.displayName}（${result.question.type.symbol} · ${result.question.type.englishName}）",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "🎼 ${result.question.type.noteCount} 个音符 · 听辨特征：${result.question.type.listeningHint}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!result.isCorrect) {
                        Text(
                            "你的答案：${result.userAnswer}",
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        result.question.type.description,
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
            ORNAnswerButton(
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

@Composable
private fun ORNAnswerButton(
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
private fun RowScope.ORNStatCard(value: String, label: String) {
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
private fun ORNStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
