package com.pianocompanion.ui.intervalsequence

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.intervalsequence.*

/**
 * 音程序列记忆训练界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalSequenceMemoryTrainingScreen(
    viewModel: IntervalSequenceViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntervalSequenceViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎵 音程序列记忆", fontWeight = FontWeight.Bold) },
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
            if (uiState.difficulty == null) {
                // 难度选择
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "听辨连续音程序列，按顺序回忆各音程类型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                IntervalSequenceDifficulty.values().forEach { difficulty ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.selectDifficulty(difficulty) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    difficulty.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(onClick = {}, label = {
                                    Text("${difficulty.sequenceLength}个音程 · ${difficulty.choiceCount}选项")
                                })
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "可用音程: ${difficulty.availableIntervals.size} 种 · " +
                                "音符时长 ${difficulty.noteDurationMs.toInt()}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                IntervalSequenceTipsCard()
            } else {
                // 训练界面
                val question = uiState.currentQuestion
                if (question != null) {
                    IntervalSequenceStatsBar(
                        streak = uiState.streak,
                        bestStreak = uiState.bestStreak,
                        answeredCount = uiState.answeredCount,
                        accuracy = uiState.accuracy
                    )

                    Text(
                        "共 ${question.sequenceLength} 个音程（${question.midiNotes.size} 个音符）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 播放按钮
                    Button(
                        onClick = { viewModel.playAudio() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("播放音程序列", fontSize = 16.sp)
                    }

                    // 选项
                    question.answerChoices.forEach { choice ->
                        val isSelected = uiState.feedback?.userAnswer == choice
                        val isCorrectChoice = choice == question.correctAnswer
                        val showResult = uiState.hasAnswered

                        val containerColor = when {
                            showResult && isCorrectChoice -> MaterialTheme.colorScheme.primaryContainer
                            showResult && isSelected && !isCorrectChoice ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }

                        OutlinedButton(
                            onClick = {
                                if (!uiState.hasAnswered) {
                                    viewModel.submitAnswer(choice)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = containerColor
                            ),
                            enabled = !uiState.hasAnswered
                        ) {
                            Text(
                                choice,
                                modifier = Modifier.padding(8.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 反馈
                    uiState.feedback?.let { feedback ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (feedback.isCorrect)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (feedback.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (feedback.isCorrect) "正确！" else "不正确",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("正确序列：${feedback.targetDisplay}")
                                Spacer(modifier = Modifier.height(8.dp))
                                IntervalSequenceVisualization(question)
                            }
                        }
                        Button(
                            onClick = { viewModel.nextQuestion() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("下一题", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntervalSequenceStatsBar(
    streak: Int,
    bestStreak: Int,
    answeredCount: Int,
    accuracy: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("🔥 连击", "$streak")
        StatItem("🏆 最佳", "$bestStreak")
        StatItem("✅ 已答", "$answeredCount")
        StatItem("📊 准确率", "${(accuracy * 100).toInt()}%")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntervalSequenceVisualization(question: IntervalSequenceQuestion) {
    val midiNotes = question.midiNotes
    val minMidi = midiNotes.min()
    val maxMidi = midiNotes.max()
    val range = (maxMidi - minMidi).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("旋律线轮廓：", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            midiNotes.forEach { midi ->
                val heightFraction = (midi - minMidi).toFloat() / range
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                    val noteName = noteNames[midi % 12]
                    val octave = midi / 12 - 1
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height((20 + heightFraction * 80).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        "$noteName$octave",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        question.targetSequence.entries.forEachIndexed { i, entry ->
            Text(
                "  ${i + 1}. ${entry.fullDescription}（${entry.interval.englishName}）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntervalSequenceTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("💡 听辨技巧", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "• 哼唱法：先唱出旋律线，再逐段判断音程\n" +
                "• 方向意识：注意每个音程是上行↑还是下行↓\n" +
                "• 参照歌曲：大二度=生日歌，大三度=当圣徒，纯五度=星球大战\n" +
                "• 大调音程（大/纯）协和，含小/增/减的不协和",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
