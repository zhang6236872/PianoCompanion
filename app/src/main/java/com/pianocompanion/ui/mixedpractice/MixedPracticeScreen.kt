package com.pianocompanion.ui.mixedpractice

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.chordreading.ChordReadingQuestion
import com.pianocompanion.interval.IntervalQuestion
import com.pianocompanion.keysig.AccidentalType
import com.pianocompanion.keysig.KeySigQuestion
import com.pianocompanion.mixedpractice.*
import com.pianocompanion.notation.NoteReadingClef
import com.pianocompanion.notation.NoteReadingQuestion
import com.pianocompanion.rhythmreading.RhythmReadingQuestion

/**
 * 综合练习主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择难度（初级/中级/高级）
 * 2. 开始练习后，随机出现 5 种题型的题目
 * 3. 每道题显示对应的可视化内容和选项
 * 4. 答题后显示对错和正确答案
 * 5. 点击「下一题」继续，直到结束
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixedPracticeScreen(
    viewModel: MixedPracticeViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MixedPracticeViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 综合练习", fontWeight = FontWeight.Bold) },
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
                MixedPracticeSetupPanel(
                    progress = uiState.progress,
                    onStart = { selectedDifficulty ->
                        viewModel.startSession(selectedDifficulty)
                    }
                )
            } else {
                MixedPracticeSessionPanel(
                    uiState = uiState,
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
private fun MixedPracticeSetupPanel(
    progress: MixedPracticeProgress,
    onStart: (MixedDifficulty) -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(MixedDifficulty.BEGINNER) }

    // 说明卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🎯 综合练习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "将识谱、音程、和弦、调号、节奏 5 种视唱练耳训练混合在一起，随机交错出题，" +
                    "训练你的综合乐理能力。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    // 难度选择
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择难度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MixedDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text(d.displayName) }
                    )
                }
            }
            Text(
                when (selectedDifficulty) {
                    MixedDifficulty.BEGINNER -> "各模块初级难度，适合入门练习"
                    MixedDifficulty.INTERMEDIATE -> "各模块中级难度，适合进阶提升"
                    MixedDifficulty.ADVANCED -> "各模块高级难度，适合挑战自我"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 各题型统计
    if (progress.totalAnswered > 0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📊 练习记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MixedStatColumn("${progress.totalAnswered}", "总答题")
                    MixedStatColumn("${progress.totalCorrect}", "正确")
                    MixedStatColumn("${"%.0f".format(progress.overallAccuracy * 100)}%", "准确率")
                    MixedStatColumn("${progress.totalSessions}", "练习次数")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("各题型正确率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                MixedQuestionType.ALL.forEach { type ->
                    val entry = progress.getTypeProgress(type)
                    if (entry.totalAnswered > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${type.emoji} ${type.displayName}", fontSize = 13.sp)
                            Text(
                                "${entry.totalCorrect}/${entry.totalAnswered} " +
                                    "(${"%.0f".format(entry.cumulativeAccuracy * 100)}%)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = { onStart(selectedDifficulty) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, "开始")
        Spacer(modifier = Modifier.width(8.dp))
        Text("开始练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun MixedPracticeSessionPanel(
    uiState: MixedPracticeUiState,
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
        MixedStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题", 1f)
        MixedStatCard("${uiState.currentStreak}", "连击", 1f)
        MixedStatCard(
            if (uiState.answeredCount > 0)
                "${"%.0f".format(uiState.correctCount.toDouble() / uiState.answeredCount * 100)}%"
            else "—",
            "准确率",
            1f
        )
    }

    // 题型标签 + 结束按钮
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
                "${question.type.emoji} ${question.type.displayName} · ${uiState.difficulty.displayName}",
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

    // 题目提示语
    Text(
        question.prompt,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 可视化区域（按题型分派）
    QuestionVisualArea(
        question = question,
        isAnswered = uiState.isAnswered,
        isCorrect = uiState.lastResult?.isCorrect
    )

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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (result.isCorrect) "✅ 正确！" else "❌ 答错了",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!result.isCorrect) {
                        Text(
                            "正确答案：${result.correctAnswer}",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // 答案选项
    if (!uiState.isAnswered) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.choices.forEach { choice ->
                MixedAnswerButton(text = choice, onClick = { onSubmit(choice) })
            }
        }
    } else {
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.NavigateNext, "下一题")
            Spacer(modifier = Modifier.width(8.dp))
            Text("下一题", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── 可视化区域（按题型分派） ─────────────────────────────

@Composable
private fun QuestionVisualArea(
    question: MixedQuestion,
    isAnswered: Boolean,
    isCorrect: Boolean?
) {
    when (question) {
        is MixedQuestion.Note -> NoteVisualCard(question.question, isAnswered, isCorrect)
        is MixedQuestion.Interval -> IntervalVisualCard(question.question, isAnswered, isCorrect)
        is MixedQuestion.Chord -> ChordVisualCard(question.question, isAnswered, isCorrect)
        is MixedQuestion.KeySig -> KeySigVisualCard(question.question, isAnswered, isCorrect)
        is MixedQuestion.Rhythm -> RhythmVisualCard(question.question, isAnswered, isCorrect)
    }
}

// ── 识谱可视化（五线谱 + 单音符） ───────────────────────

@Composable
private fun NoteVisualCard(
    question: NoteReadingQuestion,
    isAnswered: Boolean,
    isCorrect: Boolean?
) {
    val noteColor = answerColor(isAnswered, isCorrect)
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithNotes(
                    clefSymbol = "𝄞",
                    staffSteps = listOf(question.staffStep),
                    noteColor = noteColor,
                    isChord = false
                )
            }
            if (isAnswered) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = noteColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "${question.letterName} = ${question.fullNoteName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = noteColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── 音程可视化（五线谱 + 两个音符） ─────────────────────

@Composable
private fun IntervalVisualCard(
    question: IntervalQuestion,
    isAnswered: Boolean,
    isCorrect: Boolean?
) {
    val noteColor = answerColor(isAnswered, isCorrect)
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithNotes(
                    clefSymbol = "𝄞",
                    staffSteps = listOf(question.lowerStaffStep, question.higherStaffStep),
                    noteColor = noteColor,
                    isChord = false
                )
            }
            if (isAnswered) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = noteColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "${question.lowerLetterName} → ${question.higherLetterName}：" +
                            "${question.correctAnswer}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = noteColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── 和弦可视化（五线谱 + 和弦音符） ─────────────────────

@Composable
private fun ChordVisualCard(
    question: ChordReadingQuestion,
    isAnswered: Boolean,
    isCorrect: Boolean?
) {
    val noteColor = answerColor(isAnswered, isCorrect)
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithNotes(
                    clefSymbol = "𝄞",
                    staffSteps = question.noteStaffSteps,
                    noteColor = noteColor,
                    isChord = true
                )
            }
            if (isAnswered) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = noteColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "${question.noteNames.joinToString(" + ")} ：${question.correctAnswer}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = noteColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── 调号可视化（五线谱 + 升降号） ───────────────────────

@Composable
private fun KeySigVisualCard(
    question: KeySigQuestion,
    isAnswered: Boolean,
    isCorrect: Boolean?
) {
    val accentColor = answerColor(isAnswered, isCorrect)
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithKeySignature(
                    clefSymbol = "𝄞",
                    accidentalType = question.keyInfo.accidentalType,
                    accidentalStaffSteps = question.accidentalStaffSteps,
                    accentColor = accentColor
                )
            }
            if (isAnswered) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        question.keyInfo.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── 节奏可视化（文字节奏型展示） ─────────────────────────

@Composable
private fun RhythmVisualCard(
    question: RhythmReadingQuestion,
    isAnswered: Boolean,
    isCorrect: Boolean?
) {
    val rhythmColor = answerColor(isAnswered, isCorrect)
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 显示题目节奏型
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                question.pattern.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            rhythmEmoji(item.duration.shortLabel),
                            fontSize = 36.sp,
                            color = rhythmColor
                        )
                        Text(
                            item.duration.shortLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "总拍数：4/4（${"%.1f".format(question.totalBeats)} 拍）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 根据休止符短标签返回对应的 emoji 符号。 */
private fun rhythmEmoji(shortLabel: String): String = when (shortLabel) {
    "全" -> "○"
    "二" -> "◐"
    "四" -> "♩"
    "八" -> "♪"
    "十六" -> "♬"
    "休" -> "𝄽"
    "半休" -> "𝄾"
    else -> "♪"
}

// ── 五线谱绘制函数 ───────────────────────────────────────

/**
 * 统一的五线谱 + 音符绘制。
 *
 * @param clefSymbol 谱号 Unicode 符号
 * @param staffSteps 音符在谱表上的位置列表（单个=识谱/两个间距=音程/多个叠加=和弦）
 * @param noteColor 音符颜色
 * @param isChord 是否为和弦（叠加在同一水平位置）
 */
private fun DrawScope.drawStaffWithNotes(
    clefSymbol: String,
    staffSteps: List<Int>,
    noteColor: Color,
    isChord: Boolean
) {
    val w = size.width
    val h = size.height
    val centerY = h / 2f

    val lineGap = (h * 0.12f).coerceIn(20f, 50f)
    val halfStep = lineGap / 2f

    val bottomLineY = centerY + 2 * lineGap
    val lineYs = listOf(
        bottomLineY,
        bottomLineY - lineGap,
        bottomLineY - 2 * lineGap,
        bottomLineY - 3 * lineGap,
        bottomLineY - 4 * lineGap
    )

    val staffLeft = w * 0.22f
    val staffRight = w * 0.92f
    val lineColor = Color(0xFF333333)

    // 5 条谱线
    for (y in lineYs) {
        drawLine(lineColor, Offset(staffLeft, y), Offset(staffRight, y), strokeWidth = 1.5f)
    }

    // 谱号
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = lineColor.toArgb()
            textSize = lineGap * 5.5f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(clefSymbol, staffLeft - lineGap * 0.5f, centerY + lineGap * 2.2f, paint)
    }

    val noteW = lineGap * 1.3f
    val noteH = lineGap * 0.95f

    if (isChord) {
        // 和弦：所有音符在同一 X 位置（叠加）
        val noteX = (staffLeft + staffRight) / 2f
        for (step in staffSteps) {
            val noteY = bottomLineY - step * halfStep
            drawLedgerLines(step, noteX, lineGap, halfStep, staffLeft, staffRight, lineColor)
            drawNoteHead(noteX, noteY, noteW, noteH, noteColor)
        }
    } else {
        // 非和弦：音符在 X 轴均匀分布
        val positions = staffSteps.size
        for ((index, step) in staffSteps.withIndex()) {
            val noteX = if (positions == 1) {
                (staffLeft + staffRight) / 2f
            } else {
                staffLeft + w * 0.35f + index * (w * 0.25f)
            }
            val noteY = bottomLineY - step * halfStep
            drawLedgerLines(step, noteX, lineGap, halfStep, staffLeft, staffRight, lineColor)
            drawNoteHead(noteX, noteY, noteW, noteH, noteColor)
            // 符干
            val stemLen = lineGap * 3.2f
            val stemX = noteX + noteW / 2
            if (step <= 4) {
                drawLine(noteColor, Offset(stemX, noteY), Offset(stemX, noteY - stemLen), strokeWidth = 2f, cap = StrokeCap.Round)
            } else {
                drawLine(noteColor, Offset(noteX - noteW / 2, noteY), Offset(noteX - noteW / 2, noteY + stemLen), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawNoteHead(centerX: Float, centerY: Float, w: Float, h: Float, color: Color) {
    drawOval(
        color = color,
        topLeft = Offset(centerX - w / 2, centerY - h / 2),
        size = Size(w, h)
    )
}

private fun DrawScope.drawLedgerLines(
    staffStep: Int,
    noteX: Float,
    lineGap: Float,
    halfStep: Float,
    staffLeft: Float,
    staffRight: Float,
    lineColor: Color
) {
    @Suppress("UNUSED_PARAMETER") val unused = staffLeft
    @Suppress("UNUSED_PARAMETER") val unused2 = staffRight
    val topLineStep = 8
    val ledgerLen = lineGap * 1.8f
    val bottomLineY = size.height / 2f + 2 * lineGap
    if (staffStep < 0) {
        // 下方加线
        var step = -2
        while (step >= staffStep) {
            val lineY = bottomLineY + (-step) * halfStep
            drawLine(lineColor, Offset(noteX - ledgerLen / 2, lineY), Offset(noteX + ledgerLen / 2, lineY), strokeWidth = 1.5f)
            step -= 2
        }
    } else if (staffStep > topLineStep) {
        // 上方加线
        var step = 10
        while (step <= staffStep) {
            val lineY = bottomLineY - step * halfStep
            drawLine(lineColor, Offset(noteX - ledgerLen / 2, lineY), Offset(noteX + ledgerLen / 2, lineY), strokeWidth = 1.5f)
            step += 2
        }
    }
}

/**
 * 绘制五线谱 + 调号升降号。
 */
private fun DrawScope.drawStaffWithKeySignature(
    clefSymbol: String,
    accidentalType: AccidentalType,
    accidentalStaffSteps: List<Int>,
    accentColor: Color
) {
    val w = size.width
    val h = size.height
    val centerY = h / 2f

    val lineGap = (h * 0.11f).coerceIn(20f, 46f)
    val halfStep = lineGap / 2f

    val bottomLineY = centerY + 2 * lineGap
    val lineYs = listOf(
        bottomLineY,
        bottomLineY - lineGap,
        bottomLineY - 2 * lineGap,
        bottomLineY - 3 * lineGap,
        bottomLineY - 4 * lineGap
    )

    val staffLeft = w * 0.15f
    val staffRight = w * 0.92f
    val lineColor = Color(0xFF333333)

    for (y in lineYs) {
        drawLine(lineColor, Offset(staffLeft, y), Offset(staffRight, y), strokeWidth = 1.5f)
    }

    // 谱号
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = lineColor.toArgb()
            textSize = lineGap * 5.5f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(clefSymbol, staffLeft - lineGap * 0.2f, centerY + lineGap * 2.2f, paint)

        // 升降号
        val accidentals = if (accidentalType == AccidentalType.SHARP) "♯" else "♭"
        val accPaint = android.graphics.Paint().apply {
            color = accentColor.toArgb()
            textSize = lineGap * 3f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val startX = staffLeft + lineGap * 3f
        val spacing = lineGap * 1.2f
        for ((i, step) in accidentalStaffSteps.withIndex()) {
            val accY = bottomLineY - step * halfStep + lineGap * 0.5f
            drawText(accidentals, startX + i * spacing, accY, accPaint)
        }
    }
}

// ── 辅助组件 ─────────────────────────────────────────────

@Composable
private fun MixedAnswerButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.MixedStatCard(value: String, label: String, weight: Float) {
    Card(
        modifier = Modifier.weight(weight),
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
private fun MixedStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun answerColor(isAnswered: Boolean, isCorrect: Boolean?): Color {
    return if (isAnswered) {
        if (isCorrect == true) Color(0xFF2E7D32) else Color(0xFFC62828)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
}
