package com.pianocompanion.ui.notation

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.notation.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 识谱训练主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择谱号（高音/低音）和难度（初级/中级/高级）
 * 2. 开始练习后，屏幕显示一个五线谱上的音符
 * 3. 用户从选项中选择正确的音名
 * 4. 答题后显示对错，可播放音符音频验证
 * 5. 点击「下一题」继续
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteReadingScreen(
    viewModel: NoteReadingViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NoteReadingViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎼 识谱训练", fontWeight = FontWeight.Bold) },
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
                SetupPanel(
                    clef = uiState.clef,
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onClefChange = { },
                    onDifficultyChange = { },
                    onStart = { viewModel.startSession(uiState.clef, uiState.difficulty) }
                )
            } else {
                // === 练习面板 ===
                PracticePanel(
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
private fun SetupPanel(
    clef: NoteReadingClef,
    difficulty: NoteReadingDifficulty,
    progress: NoteReadingProgress,
    onClefChange: (NoteReadingClef) -> Unit,
    onDifficultyChange: (NoteReadingDifficulty) -> Unit,
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
                NoteReadingClef.ALL.forEach { c ->
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
                NoteReadingDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text(d.displayName) }
                    )
                }
            }
            Text(
                when (selectedDifficulty) {
                    NoteReadingDifficulty.BEGINNER -> "仅五线谱上的线（5 个音）"
                    NoteReadingDifficulty.INTERMEDIATE -> "线 + 间（9 个音）"
                    NoteReadingDifficulty.ADVANCED -> "线 + 间 + 加线（更多音）"
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
                    StatColumn("总答题", "${progress.totalAnswered}")
                    StatColumn("正确", "${progress.totalCorrect}")
                    StatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    StatColumn("练习次数", "${progress.totalSessions}")
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
            Text("1. 屏幕上会显示五线谱上的一个音符", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 从下方选项中选择正确的音名（C/D/E/F/G/A/B）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 答题后可点击播放按钮听音符音高", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 高音谱号线：E G B D F（从下到上）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("5. 低音谱号线：G B D F A（从下到上）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun PracticePanel(
    uiState: NoteReadingUiState,
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
        StatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题", 1f)
        StatCard("${uiState.currentStreak}", "连击", 1f)
        StatCard(
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

    // 五线谱显示
    StaffNotationCard(
        clef = question.clef,
        staffStep = question.staffStep,
        isAnswered = uiState.isAnswered,
        letterName = question.letterName,
        fullNoteName = question.fullNoteName,
        lastResult = uiState.lastResult
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
                                "正确答案：${result.question.letterName}（${result.question.fullNoteName}）",
                                fontSize = 14.sp
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
                            "播放音符"
                        )
                    }
                }
            }
        }
    }

    // 答案选项
    if (!uiState.isAnswered) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.answerChoices.forEach { choice ->
                AnswerChoiceButton(
                    letter = choice,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Arrangement.Vertical,
    content: @Composable () -> Unit
) = androidx.compose.foundation.layout.FlowRow(
    modifier = modifier,
    horizontalArrangement = horizontalArrangement,
    verticalArrangement = verticalArrangement
) { content() }

@Composable
private fun AnswerChoiceButton(
    letter: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(letter, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计卡片 ──────────────────────────────────────────────

@Composable
private fun RowScope.StatCard(value: String, label: String, @Suppress("UNUSED_PARAMETER") weight: Float) {
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
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

// ── 五线谱绘制 ────────────────────────────────────────────

/**
 * 五线谱音符显示卡片。
 *
 * 使用 Canvas 绘制五线谱、谱号和音符头。staffStep 决定音符的垂直位置。
 */
@Composable
private fun StaffNotationCard(
    clef: NoteReadingClef,
    staffStep: Int,
    isAnswered: Boolean,
    letterName: String,
    fullNoteName: String,
    lastResult: NoteReadingAnswerRecord?
) {
    val noteColor = if (isAnswered) {
        if (lastResult?.isCorrect == true) Color(0xFF2E7D32) else Color(0xFFC62828)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithNote(clef, staffStep, noteColor)
            }
            // 答题后显示音名
            if (isAnswered) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = noteColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "$letterName = $fullNoteName",
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
 * 在 DrawScope 中绘制五线谱和音符。
 *
 * 坐标系（Canvas 原点在左上角）：
 * - 五线谱中心 Y = canvasHeight / 2
 * - 线间距（lineGap）根据可用高度计算
 * - bottomLineY = centerY + 2 * lineGap（底线位置）
 * - 音符 Y = bottomLineY - staffStep * (lineGap / 2)
 *   staffStep=0 → 底线，staffStep=8 → 顶线
 */
private fun DrawScope.drawStaffWithNote(
    clef: NoteReadingClef,
    staffStep: Int,
    noteColor: Color
) {
    val w = size.width
    val h = size.height
    val centerY = h / 2f

    // 线间距：五线谱 4 个间隔占可用高度的 ~60%
    val lineGap = (h * 0.12f).coerceIn(20f, 50f)
    val halfStep = lineGap / 2f

    // 五线谱 5 条线的 Y 坐标（从底到顶：step 0, 2, 4, 6, 8）
    val bottomLineY = centerY + 2 * lineGap
    val lineYs = listOf(
        bottomLineY,                     // step 0 (bottom line)
        bottomLineY - lineGap,           // step 2
        bottomLineY - 2 * lineGap,       // step 4 (middle line)
        bottomLineY - 3 * lineGap,       // step 6
        bottomLineY - 4 * lineGap        // step 8 (top line)
    )

    val staffLeft = w * 0.22f
    val staffRight = w * 0.92f
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

    // 绘制谱号（Unicode 符号叠加）
    val clefSymbol = if (clef == NoteReadingClef.TREBLE) "𝄞" else "𝄢"
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = lineColor.toArgb()
            textSize = lineGap * 5.5f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(clefSymbol, staffLeft - lineGap * 0.5f, centerY + lineGap * 2.2f, paint)
    }

    // 计算音符头 Y 坐标
    val noteY = bottomLineY - staffStep * halfStep
    val noteX = (staffLeft + staffRight) / 2f

    // 绘制加线（ledger lines）
    drawLedgerLines(staffStep, noteX, lineGap, halfStep, staffLeft, staffRight, lineColor)

    // 绘制音符头（实心椭圆，倾斜约 -20°）
    val noteW = lineGap * 1.3f
    val noteH = lineGap * 0.95f
    drawOval(
        color = noteColor,
        topLeft = Offset(noteX - noteW / 2, noteY - noteH / 2),
        size = Size(noteW, noteH)
    )

    // 绘制符干（step ≤ 4 向上，step > 4 向下）
    val stemX = noteX + noteW / 2
    val stemColor = noteColor
    val stemLen = lineGap * 3.2f
    if (staffStep <= 4) {
        // 符干向上
        drawLine(
            color = stemColor,
            start = Offset(stemX, noteY),
            end = Offset(stemX, noteY - stemLen),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    } else {
        // 符干向下
        val stemLeftX = noteX - noteW / 2
        drawLine(
            color = stemColor,
            start = Offset(stemLeftX, noteY),
            end = Offset(stemLeftX, noteY + stemLen),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 绘制加线（音符在五线谱上方或下方时需要的短横线）。
 *
 * 加线出现在偶数 step 上（线位置），不出现在奇数 step 上（间位置）。
 * - 顶线上方：step 10, 12, ... 需要加线
 * - 底线下方：step -2, -4, ... 需要加线
 */
private fun DrawScope.drawLedgerLines(
    staffStep: Int,
    noteX: Float,
    lineGap: Float,
    halfStep: Float,
    staffLeft: Float,
    staffRight: Float,
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
