package com.pianocompanion.ui.training

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.training.*
import com.pianocompanion.ui.theme.CorrectGreen
import com.pianocompanion.ui.theme.WrongRed

/**
 * 听音训练页面。
 *
 * 两种模式：
 * 1. 选择模式（未开始会话）：选择练习类型 + 难度 → 开始
 * 2. 训练模式（会话进行中）：播放音频 → 选择答案 → 反馈 → 下一题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarTrainingScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: EarTrainingViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EarTrainingViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("👂 听音训练", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        if (state.isSessionActive && state.currentQuestion != null) {
            TrainingContent(state, viewModel, Modifier.padding(padding))
        } else {
            SetupContent(state, viewModel, Modifier.padding(padding))
        }
    }
}

// ── 选择模式 ────────────────────────────────────────────

@Composable
private fun SetupContent(
    state: EarTrainingUiState,
    viewModel: EarTrainingViewModel,
    modifier: Modifier
) {
    var selectedType by remember { mutableStateOf(ExerciseType.INTERVAL) }
    var selectedDifficulty by remember { mutableStateOf(Difficulty.BEGINNER) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "🎵 听音训练",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "通过辨认音程、和弦和音阶来训练你的耳朵。" +
                        "这是钢琴学习中最重要的音乐素养技能之一。\n\n" +
                        "🔊 App 内置合成音色，无需外部音频文件。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        // 练习类型选择
        SectionLabel("练习类型")
        ExerciseTypeSelector(selectedType) { selectedType = it }

        // 难度选择
        SectionLabel("难度等级")
        DifficultySelector(selectedDifficulty) { selectedDifficulty = it }

        // 历史进度
        val progress = state.progress.getProgress(selectedType, selectedDifficulty)
        if (progress.sessionCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📊 历史记录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("${progress.sessionCount}", "练习次数")
                        StatItem("${"%.0f".format(progress.cumulativeAccuracy * 100)}%", "累计准确率")
                        StatItem("${progress.bestStreak}", "最长连击")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 开始按钮
        Button(
            onClick = { viewModel.startSession(selectedType, selectedDifficulty) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("开始训练", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ExerciseTypeSelector(selected: ExerciseType, onSelect: (ExerciseType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExerciseType.ALL.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.displayName, fontWeight = FontWeight.Medium) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Difficulty.ALL.forEach { diff ->
            FilterChip(
                selected = selected == diff,
                onClick = { onSelect(diff) },
                label = { Text(diff.displayName, fontWeight = FontWeight.Medium) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ── 训练模式 ────────────────────────────────────────────

@Composable
private fun TrainingContent(
    state: EarTrainingUiState,
    viewModel: EarTrainingViewModel,
    modifier: Modifier
) {
    val question = state.currentQuestion!!

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 顶部统计栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${state.correctCount}/${state.answeredCount}", "得分")
            StatItem("${state.currentStreak}", "连击")
            Chip("${state.exerciseType.displayName}·${state.difficulty.displayName}")
        }

        // 题目类型 + 播放模式提示
        Text(
            "听一听，这是什么${state.exerciseType.displayName}？",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "播放模式：${question.playMode.displayName}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 播放按钮
        PlayButton(
            isPlaying = state.isPlaying,
            audioReady = state.audioReady,
            onPlay = { viewModel.playAudio() },
            onStop = { viewModel.stopAudio() }
        )

        Spacer(modifier = Modifier.weight(1f))

        // 答案选项
        AnswerChoices(
            choices = question.answerChoices,
            correctAnswer = question.correctAnswer,
            isAnswered = state.isAnswered,
            userAnswer = state.lastResult?.userAnswer,
            enabled = !state.isAnswered,
            onSelect = { viewModel.submitAnswer(it) }
        )

        // 反馈区域
        AnimatedVisibility(
            visible = state.isAnswered,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FeedbackCard(state.lastResult, question)
        }

        // 下一题 / 结束按钮
        if (state.isAnswered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.endSession() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("结束")
                }
                Button(
                    onClick = { viewModel.nextQuestion() },
                    modifier = Modifier.weight(1.5f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("下一题", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun PlayButton(
    isPlaying: Boolean,
    audioReady: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    // 脉冲动画（播放时）
    val pulseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.1f else 1.0f,
        animationSpec = if (isPlaying) infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ) else tween(200),
        label = "pulse"
    )

    FilledIconButton(
        onClick = { if (isPlaying) onStop() else onPlay() },
        modifier = Modifier
            .size(96.dp)
            .scale(pulseScale)
            .clip(CircleShape),
        enabled = audioReady,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.VolumeUp,
            contentDescription = if (isPlaying) "停止" else "播放",
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun AnswerChoices(
    choices: List<String>,
    correctAnswer: String,
    isAnswered: Boolean,
    userAnswer: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { choice ->
            val isCorrect = choice == correctAnswer
            val isUserChoice = choice == userAnswer
            val color = when {
                !isAnswered -> MaterialTheme.colorScheme.surface
                isCorrect -> CorrectGreen.copy(alpha = 0.15f)
                isUserChoice -> WrongRed.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                !isAnswered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                isCorrect -> CorrectGreen
                isUserChoice -> WrongRed
                else -> Color.Transparent
            }

            OutlinedButton(
                onClick = { onSelect(choice) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = color,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    if (isAnswered && (isCorrect || isUserChoice)) 2.dp else 1.dp,
                    borderColor
                )
            ) {
                Text(choice, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (isAnswered && isCorrect) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.Check, contentDescription = null, tint = CorrectGreen)
                } else if (isAnswered && isUserChoice && !isCorrect) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = WrongRed)
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(result: AnswerRecord?, question: EarTrainingQuestion) {
    if (result == null) return
    val isCorrect = result.isCorrect
    val containerColor = if (isCorrect) CorrectGreen.copy(alpha = 0.12f) else WrongRed.copy(alpha = 0.12f)
    val contentColor = if (isCorrect) CorrectGreen else WrongRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    if (isCorrect) "回答正确！" else "再试试",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    question.displayInfo,
                    fontSize = 14.sp,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
