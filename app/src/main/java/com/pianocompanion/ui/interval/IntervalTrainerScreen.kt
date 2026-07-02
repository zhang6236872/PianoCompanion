package com.pianocompanion.ui.interval

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.interval.*
import kotlin.math.max
import kotlin.math.min

/**
 * 音程识别训练主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择谱号（高音/低音）和难度（初级/中级/高级）
 * 2. 开始练习后，屏幕显示五线谱上的两个音符
 * 3. 用户从选项中选择正确的音程名称
 * 4. 答题后显示对错，可播放两个音符验证
 * 5. 点击「下一题」继续
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalTrainerScreen(
    viewModel: IntervalViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntervalViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📐 音程识别训练", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                // === 配置面板 ===
                IntervalSetupPanel(
                    clef = uiState.clef,
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onClefChange = { },
                    onDifficultyChange = { },
                    onStart = { viewModel.startSession(uiState.clef, uiState.difficulty) }
                )
            } else {
                // === 练习面板 ===
                IntervalPracticePanel(
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
private fun IntervalSetupPanel(
    clef: IntervalClef,
    difficulty: IntervalDifficulty,
    progress: IntervalProgress,
    onClefChange: (IntervalClef) -> Unit,
    onDifficultyChange: (IntervalDifficulty) -> Unit,
    onStart: () -> Unit
) {
    var selectedClef by remember { mutableStateOf(clef) }
    var selectedDifficulty by remember { mutableStateOf(difficulty) }

    // 谱号选择
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择谱号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntervalClef.ALL.forEach { c ->
                    FilterChip(
                        selected = selectedClef == c,
                        onClick = { selectedClef = c },
                        label = { Text(c.displayName) }
                    )
                }
            }
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
                IntervalDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text(d.displayName) }
                    )
                }
            }
            Text(
                when (selectedDifficulty) {
                    IntervalDifficulty.BEGINNER -> "仅判断度数（二度~五度）· 无需判断大小"
                    IntervalDifficulty.INTERMEDIATE -> "度数 + 性质（大/小/纯）· 高低音谱号"
                    IntervalDifficulty.ADVANCED -> "全部性质（含增/减）· 更宽音域"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    IntervalStatColumn("总答题", "${progress.totalAnswered}")
                    IntervalStatColumn("正确", "${progress.totalCorrect}")
                    IntervalStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    IntervalStatColumn("练习次数", "${progress.totalSessions}")
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = {
            onClefChange(selectedClef)
            onDifficultyChange(selectedDifficulty)
            onStart()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, "开始")
        Spacer(modifier = Modifier.width(8.dp))
        Text("开始练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }

    // 说明卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💡 如何练习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("1. 屏幕上会显示五线谱上的两个音符", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 判断两个音之间的音程（度数/性质）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选择正确答案", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 答题后可点击播放按钮听音程效果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("📖 音程知识", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 度数 = 两个音之间的音名间隔（C→E = 三度）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 一/四/五/八度 → 纯音程（纯/增/减）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 二/三/六/七度 → 大小音程（大/小/增/减）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 自然音程示例：C-E=大三度，D-F=小三度，F-B=增四度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun IntervalPracticePanel(
    uiState: IntervalUiState,
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
        IntervalStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题", 1f)
        IntervalStatCard("${uiState.currentStreak}", "连击", 1f)
        IntervalStatCard(
            if (uiState.answeredCount > 0) "${"%.0f".format(uiState.correctCount.toDouble() / uiState.answeredCount * 100)}%" else "—",
            "准确率",
            1f
        )
    }

    // 谱号/难度标签
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
                "${question.clef.displayName} · ${question.difficulty.displayName}",
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

    // 五线谱显示（两个音符）
    IntervalStaffCard(
        clef = question.clef,
        lowerStaffStep = question.lowerStaffStep,
        higherStaffStep = question.higherStaffStep,
        isAnswered = uiState.isAnswered,
        intervalName = question.interval.displayName,
        lastResultCorrect = uiState.lastResult?.isCorrect
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (result.isCorrect) "✅ 正确！" else "❌ 答错了",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!result.isCorrect) {
                            Text(
                                "正确答案：${result.question.correctAnswer}",
                                fontSize = 14.sp
                            )
                            Text(
                                "${result.question.lowerLetterName} → ${result.question.higherLetterName}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // 播放音频按钮
                    FilledIconButton(
                        onClick = {
                            if (uiState.isPlaying) onStopAudio() else onPlayAudio()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            "播放音程"
                        )
                    }
                }
            }
        }
    }

    // 答案选项
    if (!uiState.isAnswered) {
        // 播放按钮（答题前也可试听）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (!uiState.audioReady) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("音频准备中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FilledTonalButton(
                    onClick = { if (uiState.isPlaying) onStopAudio() else onPlayAudio() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.MusicNote,
                        "试听",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("试听音程")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.answerChoices.forEach { choice ->
                IntervalAnswerButton(
                    text = choice,
                    onClick = { onSubmit(choice) }
                )
            }
        }
    } else {
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.NavigateNext, "下一题")
            Spacer(modifier = Modifier.width(8.dp))
            Text("下一题", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IntervalAnswerButton(
    text: String,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = 1f, label = "btn_scale")
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计卡片 ──────────────────────────────────────────────

@Composable
private fun RowScope.IntervalStatCard(value: String, label: String, @Suppress("UNUSED_PARAMETER") weight: Float) {
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
private fun IntervalStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

// ── 五线谱绘制（两个音符） ────────────────────────────────

/**
 * 五线谱双音符显示卡片。
 *
 * 使用 Canvas 绘制五线谱、谱号和两个音符头。
 */
@Composable
private fun IntervalStaffCard(
    clef: IntervalClef,
    lowerStaffStep: Int,
    higherStaffStep: Int,
    isAnswered: Boolean,
    intervalName: String,
    lastResultCorrect: Boolean?
) {
    val noteColor = if (isAnswered) {
        if (lastResultCorrect == true) Color(0xFF2E7D32) else Color(0xFFC62828)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithTwoNotes(clef, lowerStaffStep, higherStaffStep, noteColor)
            }
            // 答题后显示音程名称
            if (isAnswered) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = noteColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        intervalName,
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

/**
 * 在 DrawScope 中绘制五线谱和两个音符。
 *
 * 左侧音符 = 较低音，右侧音符 = 较高音。
 * 坐标系与 NoteReadingScreen 一致。
 */
private fun DrawScope.drawStaffWithTwoNotes(
    clef: IntervalClef,
    lowerStep: Int,
    higherStep: Int,
    noteColor: Color
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

    val staffLeft = w * 0.20f
    val staffRight = w * 0.94f
    val lineColor = Color(0xFF333333)

    // 绘制 5 条谱线
    for (y in lineYs) {
        drawLine(
            color = lineColor,
            start = Offset(staffLeft, y),
            end = Offset(staffRight, y),
            strokeWidth = 1.5f
        )
    }

    // 绘制谱号
    val clefSymbol = if (clef == IntervalClef.TREBLE) "𝄞" else "𝄢"
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = lineColor.toArgb()
            textSize = lineGap * 5.5f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(clefSymbol, staffLeft - lineGap * 0.5f, centerY + lineGap * 2.2f, paint)
    }

    // 两个音符的 X 坐标（左侧偏低音，右侧偏高音）
    val noteAreaStart = staffLeft + lineGap * 3f
    val noteAreaEnd = staffRight - lineGap * 0.5f
    val noteAreaWidth = noteAreaEnd - noteAreaStart
    val lowerNoteX = noteAreaStart + noteAreaWidth * 0.30f
    val higherNoteX = noteAreaStart + noteAreaWidth * 0.70f

    val lowerNoteY = bottomLineY - lowerStep * halfStep
    val higherNoteY = bottomLineY - higherStep * halfStep

    // 绘制加线（同时为两个音符绘制）
    drawLedgerLinesFor(lowerStep, lowerNoteX, lineGap, halfStep, lineColor)
    drawLedgerLinesFor(higherStep, higherNoteX, lineGap, halfStep, lineColor)

    val noteW = lineGap * 1.3f
    val noteH = lineGap * 0.95f

    // 绘制较低音音符头
    drawOval(
        color = noteColor,
        topLeft = Offset(lowerNoteX - noteW / 2, lowerNoteY - noteH / 2),
        size = Size(noteW, noteH)
    )
    // 较低音符干（向上）
    val lowerStemX = lowerNoteX + noteW / 2
    val stemLen = lineGap * 3.2f
    drawLine(
        color = noteColor,
        start = Offset(lowerStemX, lowerNoteY),
        end = Offset(lowerStemX, lowerNoteY - stemLen),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // 绘制较高音音符头
    drawOval(
        color = noteColor,
        topLeft = Offset(higherNoteX - noteW / 2, higherNoteY - noteH / 2),
        size = Size(noteW, noteH)
    )
    // 较高音符干（向下，因为通常在上方）
    val higherStemX = higherNoteX - noteW / 2
    drawLine(
        color = noteColor,
        start = Offset(higherStemX, higherNoteY),
        end = Offset(higherStemX, higherNoteY + stemLen),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // 绘制音符之间的连接弧线（提示这是一个音程）
    val arcStart = Offset(lowerNoteX, lowerNoteY - noteH / 2)
    val arcEnd = Offset(higherNoteX, higherNoteY - noteH / 2)
    val arcControlY = min(arcStart.y, arcEnd.y) - lineGap * 0.8f
    // 使用贝塞尔曲线绘制连接弧
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(arcStart.x, arcStart.y)
        cubicTo(
            arcStart.x, arcControlY,
            arcEnd.x, arcControlY,
            arcEnd.x, arcEnd.y
        )
    }
    drawPath(
        path = path,
        color = noteColor.copy(alpha = 0.3f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.5f,
            cap = StrokeCap.Round
        )
    )
}

/**
 * 绘制加线（音符在五线谱上方或下方时需要的短横线）。
 */
private fun DrawScope.drawLedgerLinesFor(
    staffStep: Int,
    noteX: Float,
    lineGap: Float,
    halfStep: Float,
    color: Color
) {
    val bottomLineY = size.height / 2f + 2 * lineGap
    val ledgerLen = lineGap * 1.6f
    val ledgerLeft = noteX - ledgerLen / 2
    val ledgerRight = noteX + ledgerLen / 2

    // 上方加线（step > 8）
    if (staffStep > 8) {
        var step = 10
        while (step <= staffStep + 1) {
            val y = bottomLineY - step * halfStep
            drawLine(
                color = color,
                start = Offset(ledgerLeft, y),
                end = Offset(ledgerRight, y),
                strokeWidth = 1.5f
            )
            step += 2
        }
    }

    // 下方加线（step < 0）
    if (staffStep < 0) {
        var step = -2
        while (step >= staffStep - 1) {
            val y = bottomLineY - step * halfStep
            drawLine(
                color = color,
                start = Offset(ledgerLeft, y),
                end = Offset(ledgerRight, y),
                strokeWidth = 1.5f
            )
            step -= 2
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
