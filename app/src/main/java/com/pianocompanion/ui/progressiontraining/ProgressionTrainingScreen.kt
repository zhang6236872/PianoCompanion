package com.pianocompanion.ui.progressiontraining

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.progressiontraining.*

/**
 * 和弦进行听辨训练主界面（Material 3 Compose）。
 *
 * 功能流程（听辨训练）：
 * 1. 选择难度（初级/中级/高级）
 * 2. 开始练习后，依次播放一组和弦（每个和弦是柱式三和弦）
 * 3. 用户凭听觉判断这是哪种和弦进行
 * 4. 答题后显示对错 + 进行描述和风格，可重播验证
 * 5. 点击「下一题」继续
 *
 * 核心听觉线索：和声的整体走向、「张力-释放」模式以及根音运动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionTrainingScreen(
    viewModel: ProgressionTrainingViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProgressionTrainingViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎸 和弦进行听辨", fontWeight = FontWeight.Bold) },
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
                ProgressionTrainingSetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { viewModel.startSession(uiState.difficulty) }
                )
            } else {
                ProgressionTrainingPracticePanel(
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
private fun ProgressionTrainingSetupPanel(
    difficulty: ProgressionDifficulty,
    progress: ProgressionTrainingProgress,
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
                ProgressionDifficulty.ALL.forEach { d ->
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
                    ProgStatColumn("总答题", "${progress.totalAnswered}")
                    ProgStatColumn("正确", "${progress.totalCorrect}")
                    ProgStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    ProgStatColumn("最长连击", "${progress.overallBestStreak}")
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
            Text("1. 点击播放按钮，依次聆听 3-4 个和弦", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 凭听觉判断这段和声运动属于哪种和弦进行", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选择正确答案", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 答题后可重播，并查看该进行的听感描述", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("📖 什么是和弦进行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 和弦进行是多个和弦按顺序排列形成的和声运动", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 不同的进行拥有截然不同的情感色彩和风格", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 识别和弦进行是编曲、即兴和音乐理解的核心技能", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🔑 听辨技巧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 关注整体「张力-释放」模式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 注意低音（根音）的运动方向", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 感受每个和弦的色彩（大调明亮 vs 小调忧郁）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 记住常见进行的「招牌」听感", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun ProgressionTrainingPracticePanel(
    uiState: ProgressionTrainingUiState,
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
        ProgStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题")
        ProgStatCard("${uiState.currentStreak}", "连击")
        ProgStatCard(
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
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "${question.difficulty.displayName} · ${question.chordCount} 个和弦",
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onEnd) {
            Text("结束", color = MaterialTheme.colorScheme.error)
        }
    }

    // 播放卡片（大号播放按钮）
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
            Text("🎧 听辨这段和弦进行", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            FilledIconButton(
                onClick = {
                    if (uiState.isPlaying) onStopAudio() else onPlayAudio()
                },
                modifier = Modifier.size(72.dp),
                enabled = uiState.audioReady
            ) {
                Icon(
                    if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayCircle,
                    "播放和弦进行",
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
                        "${result.question.fullDescription}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!result.isCorrect) {
                        Text(
                            "正确答案：${result.question.correctAnswer}",
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "听感：${result.question.type.description}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.run {
                            if (result.isCorrect) onPrimaryContainer else onErrorContainer
                        }.copy(alpha = 0.85f)
                    )
                    Text(
                        result.question.styleDescription,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.run {
                            if (result.isCorrect) onPrimaryContainer else onErrorContainer
                        }.copy(alpha = 0.7f)
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
            ProgAnswerButton(
                text = choice,
                enabled = !uiState.isAnswered,
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
private fun ProgAnswerButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计组件 ──────────────────────────────────────────────

@Composable
private fun RowScope.ProgStatCard(value: String, label: String) {
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
private fun ProgStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}
