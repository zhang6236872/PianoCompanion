package com.pianocompanion.ui.keysig

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.keysig.*

/**
 * 调号识别训练主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择谱号（高音/低音）和难度（初级/中级/高级）
 * 2. 开始练习后，屏幕显示五线谱上的调号（升降号组合）
 * 3. 用户从选项中选择正确的调性
 * 4. 答题后显示对错，可播放音阶验证
 * 5. 点击「下一题」继续
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeySigScreen(
    viewModel: KeySigViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KeySigViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎼 调号识别训练", fontWeight = FontWeight.Bold) },
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
                KeySigSetupPanel(
                    clef = uiState.clef,
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onStart = { selectedClef, selectedDifficulty ->
                        viewModel.startSession(selectedClef, selectedDifficulty)
                    }
                )
            } else {
                KeySigPracticePanel(
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
private fun KeySigSetupPanel(
    clef: KeySigClef,
    difficulty: KeySigDifficulty,
    progress: KeySigProgress,
    onStart: (KeySigClef, KeySigDifficulty) -> Unit
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
                KeySigClef.ALL.forEach { c ->
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
                KeySigDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text(d.displayName) }
                    )
                }
            }
            Text(
                when (selectedDifficulty) {
                    KeySigDifficulty.BEGINNER -> "仅大调 · 0-3 个升降号（C/G/D/A/F/B♭/E♭ 大调）"
                    KeySigDifficulty.INTERMEDIATE -> "仅大调 · 最多 5 个升降号（加入 B/E♭/A♭/D♭ 大调）"
                    KeySigDifficulty.ADVANCED -> "大调 + 小调 · 最多 7 个升降号（需区分关系大小调）"
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
                    KeySigStatColumn("总答题", "${progress.totalAnswered}")
                    KeySigStatColumn("正确", "${progress.totalCorrect}")
                    KeySigStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    KeySigStatColumn("练习次数", "${progress.totalSessions}")
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = { onStart(selectedClef, selectedDifficulty) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
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
            Text("1. 屏幕显示五线谱及调号（升降号组合）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 数清升降号个数并判断是大调还是小调", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 从选项中选择正确的调性", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 答题后可点击播放按钮听音阶验证", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("📖 调号知识", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• 升号顺序：F♯-C♯-G♯-D♯-A♯-E♯-B♯（五度圈顺时针）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 降号顺序：B♭-E♭-A♭-D♭-G♭-C♭-F♭（五度圈逆时针）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 口诀：最后一个升号 = 大调导音，上行半音即大调主音", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 口诀：最后一个降号 = 大调下属音（Fa），唱到 Do 即大调主音", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• 关系小调主音 = 大调主音下方小三度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun KeySigPracticePanel(
    uiState: KeySigUiState,
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
        KeySigStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题", 1f)
        KeySigStatCard("${uiState.currentStreak}", "连击", 1f)
        KeySigStatCard(
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

    // 五线谱显示（调号）
    KeySigStaffCard(
        clef = question.clef,
        accidentalType = question.keyInfo.accidentalType,
        accidentalStaffSteps = question.accidentalStaffSteps,
        accidentalCount = question.keyInfo.accidentalCount,
        isAnswered = uiState.isAnswered,
        keyDisplayName = question.keyInfo.displayName,
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
                                "${result.question.keyInfo.accidentalCount} 个" +
                                    if (result.question.keyInfo.accidentalType == AccidentalType.SHARP) "升号"
                                    else if (result.question.keyInfo.accidentalType == AccidentalType.FLAT) "降号"
                                    else "升降号",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = { if (uiState.isPlaying) onStopAudio() else onPlayAudio() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            "播放音阶"
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
                    Text("试听音阶")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.answerChoices.forEach { choice ->
                KeySigAnswerButton(text = choice, onClick = { onSubmit(choice) })
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

@Composable
private fun KeySigAnswerButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 统计卡片 ──────────────────────────────────────────────

@Composable
private fun RowScope.KeySigStatCard(value: String, label: String, @Suppress("UNUSED_PARAMETER") weight: Float) {
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
private fun KeySigStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

// ── 五线谱绘制（调号） ───────────────────────────────────

/**
 * 五线谱调号显示卡片。
 *
 * 使用 Canvas 绘制五线谱、谱号和调号升降号。
 */
@Composable
private fun KeySigStaffCard(
    clef: KeySigClef,
    accidentalType: AccidentalType,
    accidentalStaffSteps: List<Int>,
    accidentalCount: Int,
    isAnswered: Boolean,
    keyDisplayName: String,
    lastResultCorrect: Boolean?
) {
    val accentColor = if (isAnswered) {
        if (lastResultCorrect == true) Color(0xFF2E7D32) else Color(0xFFC62828)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(260.dp),
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
                    clef, accidentalType, accidentalStaffSteps, accentColor
                )
            }
            // 答题后显示调性名称
            if (isAnswered) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        keyDisplayName,
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

/**
 * 在 DrawScope 中绘制五线谱和调号升降号。
 */
private fun DrawScope.drawStaffWithKeySignature(
    clef: KeySigClef,
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

    val staffLeft = w * 0.12f
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
    val clefSymbol = if (clef == KeySigClef.TREBLE) "𝄞" else "𝄢"
    drawContext.canvas.nativeCanvas.apply {
        val clefPaint = android.graphics.Paint().apply {
            color = lineColor.toArgb()
            textSize = lineGap * 5.5f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(clefSymbol, staffLeft + lineGap * 0.6f, centerY + lineGap * 2.2f, clefPaint)
    }

    // 绘制调号升降号
    if (accidentalStaffSteps.isNotEmpty()) {
        val accidentalSymbol = when (accidentalType) {
            AccidentalType.SHARP -> "♯"
            AccidentalType.FLAT -> "♭"
            else -> ""
        }
        if (accidentalSymbol.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.apply {
                val accidentalPaint = android.graphics.Paint().apply {
                    color = accentColor.toArgb()
                    textSize = lineGap * 3.2f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                // 升降号从谱号右侧依次排列
                val accidentalStart = staffLeft + lineGap * 2.0f
                val accidentalSpacing = lineGap * 1.0f
                for ((index, step) in accidentalStaffSteps.withIndex()) {
                    val x = accidentalStart + index * accidentalSpacing
                    // staff step → Y 坐标（底线 step 0 → bottomLineY）
                    val y = bottomLineY - step * halfStep + lineGap * 1.0f
                    drawText(accidentalSymbol, x, y, accidentalPaint)
                }
            }
        }
    }

    // 绘制一个示例全音符在调号右侧（使谱面看起来完整）
    val noteX = staffLeft + lineGap * 2.0f +
        (if (accidentalStaffSteps.isNotEmpty()) accidentalStaffSteps.size * lineGap * 1.0f else 0f) +
        lineGap * 1.5f
    if (noteX < staffRight - lineGap) {
        drawContext.canvas.nativeCanvas.apply {
            val notePaint = android.graphics.Paint().apply {
                color = accentColor.toArgb()
                textSize = lineGap * 2.8f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            // 在第 3 线（中间线）位置画一个示例音符
            val noteStep = 4 // 中间线
            val noteY = bottomLineY - noteStep * halfStep + lineGap * 1.0f
            drawText("𝅝", noteX, noteY, notePaint)
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
