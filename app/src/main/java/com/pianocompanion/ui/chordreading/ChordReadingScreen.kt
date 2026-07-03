package com.pianocompanion.ui.chordreading

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
import com.pianocompanion.chordreading.*
import kotlin.math.max

/**
 * 和弦识别训练主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择谱号（高音/低音）和难度（初级/中级/高级）
 * 2. 开始练习后，屏幕显示五线谱上叠置的和弦（3-4 个音符）
 * 3. 用户从选项中选择正确的和弦类型
 * 4. 答题后显示对错，可播放和弦验证
 * 5. 点击「下一题」继续
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordReadingScreen(
    viewModel: ChordReadingViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChordReadingViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎸 和弦识别训练", fontWeight = FontWeight.Bold) },
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
                ChordReadingSetupPanel(
                    clef = uiState.clef,
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selectedClef, selectedDifficulty ->
                        viewModel.startSession(selectedClef, selectedDifficulty)
                    }
                )
            } else {
                // === 练习面板 ===
                ChordReadingPracticePanel(
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
private fun ChordReadingSetupPanel(
    clef: ChordReadingClef,
    difficulty: ChordReadingDifficulty,
    progress: ChordReadingProgress,
    onStart: (ChordReadingClef, ChordReadingDifficulty) -> Unit
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
                ChordReadingClef.ALL.forEach { c ->
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
                ChordReadingDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text(d.displayName) }
                    )
                }
            }
            Text(
                when (selectedDifficulty) {
                    ChordReadingDifficulty.BEGINNER -> "三和弦：大三 / 小三（识别明亮与暗淡色彩）"
                    ChordReadingDifficulty.INTERMEDIATE -> "三和弦：大三 / 小三 / 减三（含紧张减三和弦）"
                    ChordReadingDifficulty.ADVANCED -> "七和弦：大七 / 属七 / 小七 / 半减七（四音叠置）"
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
                    ChordReadingStatColumn("总答题", "${progress.totalAnswered}")
                    ChordReadingStatColumn("正确", "${progress.totalCorrect}")
                    ChordReadingStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    ChordReadingStatColumn("练习次数", "${progress.totalSessions}")
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = {
            onStart(selectedClef, selectedDifficulty)
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
            Text("1. 屏幕上会显示五线谱上叠置的和弦（3-4 个音符）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 判断和弦的类型（大三/小三/减三/增三/七和弦）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选择正确答案", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 答题后可点击播放按钮听和弦效果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("📖 和弦知识", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 三和弦 = 根音 + 三音 + 五音（三度叠置）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 大三(4+3半音)=明亮 · 小三(3+4)=暗淡 · 减三(3+3)=紧张", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 自然音三和弦：C/F/G=大三，D/E/A=小三，B=减三", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 七和弦在三和弦上再加七音，色彩更丰富", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun ChordReadingPracticePanel(
    uiState: ChordReadingUiState,
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
        ChordReadingStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题", 1f)
        ChordReadingStatCard("${uiState.currentStreak}", "连击", 1f)
        ChordReadingStatCard(
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
                "${question.clef.displayName} · ${question.difficulty.displayName}" +
                    if (question.isSeventh) " · 七和弦" else " · 三和弦",
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

    // 五线谱显示（叠置和弦）
    ChordStaffCard(
        clef = question.clef,
        noteStaffSteps = question.noteStaffSteps,
        isAnswered = uiState.isAnswered,
        chordName = question.chordType.displayName,
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
                                result.question.noteNames.joinToString("-"),
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
                            "播放和弦"
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
                    Text("试听和弦")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.answerChoices.forEach { choice ->
                ChordReadingAnswerButton(
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
private fun ChordReadingAnswerButton(
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
private fun RowScope.ChordReadingStatCard(value: String, label: String, @Suppress("UNUSED_PARAMETER") weight: Float) {
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
private fun ChordReadingStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

// ── 五线谱绘制（叠置和弦） ───────────────────────────────

/**
 * 五线谱和弦显示卡片。
 *
 * 使用 Canvas 绘制五线谱、谱号和叠置的多个音符头。
 */
@Composable
private fun ChordStaffCard(
    clef: ChordReadingClef,
    noteStaffSteps: List<Int>,
    isAnswered: Boolean,
    chordName: String,
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
            .height(260.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStaffWithChord(clef, noteStaffSteps, noteColor)
            }
            // 答题后显示和弦名称
            if (isAnswered) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = noteColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        chordName,
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
 * 在 DrawScope 中绘制五线谱和叠置和弦。
 *
 * 所有音符在同一 X 位置（和弦同时发声），按各自 staffStep 确定纵坐标。
 */
private fun DrawScope.drawStaffWithChord(
    clef: ChordReadingClef,
    noteStaffSteps: List<Int>,
    noteColor: Color
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

    val staffLeft = w * 0.16f
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
    val clefSymbol = if (clef == ChordReadingClef.TREBLE) "𝄞" else "𝄢"
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = lineColor.toArgb()
            textSize = lineGap * 5.5f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(clefSymbol, staffLeft - lineGap * 0.5f, centerY + lineGap * 2.2f, paint)
    }

    // 和弦音符的 X 坐标（居中偏右）
    val noteX = (staffLeft + staffRight) / 2f + lineGap * 0.5f

    // 为所有音符绘制加线（取并集避免重复绘制）
    drawChordLedgerLines(noteStaffSteps, noteX, lineGap, halfStep, lineColor)

    val noteW = lineGap * 1.3f
    val noteH = lineGap * 0.95f

    // 绘制所有音符头（同一 X 位置，不同 Y）
    for (step in noteStaffSteps) {
        val noteY = bottomLineY - step * halfStep
        drawOval(
            color = noteColor,
            topLeft = Offset(noteX - noteW / 2, noteY - noteH / 2),
            size = Size(noteW, noteH)
        )
    }

    // 绘制和弦符干：连接最低音和最高音
    if (noteStaffSteps.size >= 2) {
        val lowestStep = noteStaffSteps.min()
        val highestStep = noteStaffSteps.max()
        val lowestY = bottomLineY - lowestStep * halfStep
        val highestY = bottomLineY - highestStep * halfStep
        // 符干方向：中线上方音符向下，中线下方音符向上（简化：用最低音位置判断）
        val middleY = bottomLineY - 2 * lineGap
        val stemLen = lineGap * 3.5f
        if (lowestY < middleY) {
            // 音符整体偏上，符干向下（画在音符左侧）
            val stemX = noteX - noteW / 2
            drawLine(
                color = noteColor,
                start = Offset(stemX, highestY),
                end = Offset(stemX, highestY + stemLen),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        } else {
            // 音符整体偏下，符干向上（画在音符右侧）
            val stemX = noteX + noteW / 2
            drawLine(
                color = noteColor,
                start = Offset(stemX, lowestY),
                end = Offset(stemX, lowestY - stemLen),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 为一组音符绘制加线（去重，避免同一加线被多次绘制）。
 */
private fun DrawScope.drawChordLedgerLines(
    staffSteps: List<Int>,
    noteX: Float,
    lineGap: Float,
    halfStep: Float,
    color: Color
) {
    val bottomLineY = size.height / 2f + 2 * lineGap
    val ledgerLen = lineGap * 1.8f
    val ledgerLeft = noteX - ledgerLen / 2
    val ledgerRight = noteX + ledgerLen / 2

    // 收集所有需要的加线 step 值（偶数 step = 线位置）
    val ledgerSteps = mutableSetOf<Int>()
    for (step in staffSteps) {
        if (step > 8) {
            // 上方加线（step 10, 12, ... 到最高音）
            var s = 10
            while (s <= step + 1) {
                ledgerSteps.add(s)
                s += 2
            }
        }
        if (step < 0) {
            // 下方加线（step -2, -4, ... 到最低音）
            var s = -2
            while (s >= step - 1) {
                ledgerSteps.add(s)
                s -= 2
            }
        }
    }

    for (step in ledgerSteps) {
        val y = bottomLineY - step * halfStep
        drawLine(
            color = color,
            start = Offset(ledgerLeft, y),
            end = Offset(ledgerRight, y),
            strokeWidth = 1.5f
        )
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
